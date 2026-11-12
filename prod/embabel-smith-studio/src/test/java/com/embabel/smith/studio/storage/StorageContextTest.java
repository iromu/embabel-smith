package com.embabel.smith.studio.storage;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.studio.store.ProcessStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The hot-swap path (change: smith-studio-postgres-storage, task 2.6): bind an in-memory
 * database, write, rebind to a second in-memory database, and assert the new binding
 * serves, the old database is untouched, and the old connection pool is closed.
 */
class StorageContextTest {

	@TempDir
	Path root;

	@Test
	void rebindSwapsToTheNewDatabaseAndClosesTheOldPool() throws Exception {
		StorageContext ctx = newContext();
		ProcessStore store = new ProcessStore(ctx);

		String urlOne = memUrl("one");
		String urlTwo = memUrl("two");

		ctx.bind(settings(urlOne));
		StorageBinding first = ctx.main();
		store.savePack(pack("alpha"));
		assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::name).containsExactly("alpha");

		ctx.bind(settings(urlTwo));

		// The new binding serves: it starts empty and accepts new packs.
		assertThat(store.listPacks()).isEmpty();
		store.savePack(pack("beta"));
		assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::name).containsExactly("beta");

		// The old database is untouched: alpha is still stored there.
		assertThat(packCount(urlOne)).isEqualTo(1);

		// The old connection pool is closed.
		assertThat(first.isPoolClosed()).isTrue();

		ctx.shutdown();
	}

	@Test
	void unboundStoreReportsStorageNotConfigured() {
		StorageContext ctx = newContext();
		ProcessStore store = new ProcessStore(ctx);

		// Lists are empty when unbound.
		assertThat(store.listPacks()).isEmpty();
		assertThat(store.discoveries()).isEmpty();

		// Mutations and loads report the clear error.
		assertThatThrownBy(() -> store.savePack(pack("alpha"))).isInstanceOf(StorageNotConfiguredException.class)
			.hasMessageContaining("Storage not configured");
		assertThatThrownBy(() -> store.loadPack("alpha")).isInstanceOf(StorageNotConfiguredException.class);
		assertThatThrownBy(() -> store.recordDiscovery("folder", "/docs", 1))
			.isInstanceOf(StorageNotConfiguredException.class);

		ctx.shutdown();
	}

	@Test
	void unreachableEndpointKeepsThePreviousBinding() {
		StorageContext ctx = newContext();
		ProcessStore store = new ProcessStore(ctx);

		String urlOne = memUrl("keep");
		ctx.bind(settings(urlOne));
		store.savePack(pack("alpha"));
		StorageBinding first = ctx.main();

		// Rebinding to an unreachable endpoint fails and the previous binding stays in
		// effect.
		assertThatThrownBy(() -> ctx.bind(settings("jdbc:postgresql://127.0.0.1:1/none")))
			.hasMessageContaining("Cannot connect");
		assertThat(ctx.main()).isSameAs(first);
		assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::name).containsExactly("alpha");
		assertThat(first.isPoolClosed()).isFalse();

		ctx.shutdown();
	}

	private StorageContext newContext() {
		StorageSettingsService settingsService = new StorageSettingsService(root);
		return new StorageContext(settingsService, new JsonlTraceStore(root.resolve("runs.jsonl")), null, null, null);
	}

	private static StorageSettings settings(String url) {
		return new StorageSettings(new StorageSettings.DbSettings(url, "sa", ""),
				new StorageSettings.TraceSettings(StorageSettings.TRACE_JSONL, null, null, null));
	}

	private static String memUrl(String tag) {
		return "jdbc:h2:mem:" + tag + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
	}

	private static int packCount(String url) throws Exception {
		try (Connection c = DriverManager.getConnection(url, "sa", "");
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM packs")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static AgentSpec pack(String name) {
		ActionStepSpec step = ActionStepSpec.action(name + "_step", "a step", List.of("UserInput"), name + "_Out",
				"Produce {{userInput}} into the result.");
		GoalStepSpec goalSpec = GoalStepSpec.exportedGoal(name + "_goal", "the goal", name + "_Out");
		DynamicTypeDef type = new DynamicTypeDef(name + "_Out", "an output", null, null, null);
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"), goalSpec, List.of(step), List.of(type));
	}

}
