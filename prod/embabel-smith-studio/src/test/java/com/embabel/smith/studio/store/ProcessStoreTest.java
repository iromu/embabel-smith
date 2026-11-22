/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.store;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.studio.storage.StorageContext;
import com.embabel.smith.studio.storage.StorageSettings;
import com.embabel.smith.studio.storage.StorageSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the database-backed store (change: smith-studio-postgres-storage): a
 * save → list → load round-trip, multiple packs, and the discovery history, against an
 * in-memory H2 binding. The versioning tests are change: publish-keeps-versions — the
 * store keys packs by {@code (name, version)}, so a republish keeps the version it
 * replaces.
 */
class ProcessStoreTest {

	@TempDir
	Path root;

	private StorageContext context;

	private String url;

	@AfterEach
	void cleanup() {
		if (context != null) {
			context.shutdown();
		}
	}

	/** A store bound to a fresh in-memory H2 database. */
	private ProcessStore boundStore() {
		StorageSettingsService settingsService = new StorageSettingsService(root);
		context = new StorageContext(settingsService, new JsonlTraceStore(root.resolve("runs.jsonl")), null, null,
				null);
		url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
		context.bind(new StorageSettings(new StorageSettings.DbSettings(url, "sa", ""),
				new StorageSettings.TraceSettings(StorageSettings.TRACE_JSONL, null, null, null)));
		return new ProcessStore(context);
	}

	private AgentSpec pack(String name, String goal) {
		return pack(name, goal, "1.0.0");
	}

	private AgentSpec pack(String name, String goal, String version) {
		ActionStepSpec step = ActionStepSpec.action(name + "_step", "a step", List.of("UserInput"), name + "_Out",
				"Produce {{userInput}} into the result.");
		GoalStepSpec goalSpec = GoalStepSpec.exportedGoal(goal, "the goal", name + "_Out");
		DynamicTypeDef type = new DynamicTypeDef(name + "_Out", "an output", null, null, null);
		return new AgentSpec(new AgentSpecManifest(name, version), goalSpec, List.of(step), List.of(type));
	}

	@Test
	void saveListLoadRoundTripsAPack() {
		ProcessStore store = boundStore();
		AgentSpec original = pack("alpha", "alpha_goal");

		ProcessStore.PackRecord record = store.savePack(original);
		assertThat(record.name()).isEqualTo("alpha");
		assertThat(record.goal()).isEqualTo("alpha_goal");
		assertThat(record.steps()).isEqualTo(1);

		AgentSpec loaded = store.loadPack("alpha");
		assertThat(loaded).isNotNull();
		assertThat(loaded.name()).isEqualTo("alpha");
		assertThat(loaded.version()).isEqualTo("1.0.0");
		assertThat(loaded.goal().name()).isEqualTo("alpha_goal");
		assertThat(loaded.goal().outputTypeName()).isEqualTo("alpha_Out");
		assertThat(loaded.steps()).hasSize(1);
		assertThat(loaded.step("alpha_step").prompt()).isEqualTo(original.step("alpha_step").prompt());
	}

	@Test
	void listReflectsMultiplePacks() {
		ProcessStore store = boundStore();
		store.savePack(pack("alpha", "alpha_goal"));
		store.savePack(pack("beta", "beta_goal"));

		List<ProcessStore.PackRecord> records = store.listPacks();
		assertThat(records).hasSize(2);
		assertThat(records).extracting(ProcessStore.PackRecord::name).contains("alpha", "beta");
	}

	@Test
	void loadPackReturnsNullForAnUnknownName() {
		ProcessStore store = boundStore();
		assertThat(store.loadPack("nope")).isNull();
	}

	@Test
	void twoVersionsOfOneNameAreStoredAsTwoRows() throws Exception {
		ProcessStore store = boundStore();
		store.savePack(pack("alpha", "alpha_goal", "1.0.0"));
		store.savePack(pack("alpha", "alpha_v2_goal", "1.1.0"));

		// Two rows, both readable. Keyed by name alone — the mapping before this change —
		// the second save is an update of the one row, so this is 1 row and the 1.0.0
		// content is gone.
		assertThat(versionsOf("alpha")).containsExactlyInAnyOrder("1.0.0", "1.1.0");

		// A bare name resolves to the highest version.
		AgentSpec loaded = store.loadPack("alpha");
		assertThat(loaded.version()).isEqualTo("1.1.0");
		assertThat(loaded.goal().name()).isEqualTo("alpha_v2_goal");

		// The listing shows the name once, not once per version.
		assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::name).containsExactly("alpha");
	}

	@Test
	void republishOfTheSameVersionUpdatesThatRow() throws Exception {
		ProcessStore store = boundStore();
		store.savePack(pack("alpha", "alpha_goal", "1.0.0"));
		store.savePack(pack("alpha", "alpha_improved_goal", "1.0.0"));

		assertThat(versionsOf("alpha")).containsExactly("1.0.0");
		assertThat(store.loadPack("alpha").goal().name()).isEqualTo("alpha_improved_goal");

		// A new version of the same name adds a row instead of replacing anything.
		store.savePack(pack("alpha", "alpha_next_goal", "1.1.0"));
		assertThat(versionsOf("alpha")).containsExactlyInAnyOrder("1.0.0", "1.1.0");
		assertThat(store.loadPack("alpha").goal().name()).isEqualTo("alpha_next_goal");
	}

	@Test
	void highestVersionComparesNumericallyNotLexically() throws Exception {
		ProcessStore store = boundStore();
		// The higher version is saved first, so this also fails for an implementation
		// that
		// simply keeps the most recently saved row, and lexicographic order would rank
		// the
		// 1.9.0 above the 1.10.0.
		store.savePack(pack("alpha", "alpha_release_goal", "1.10.0"));
		store.savePack(pack("alpha", "alpha_older_goal", "1.9.0"));

		assertThat(store.loadPack("alpha").version()).isEqualTo("1.10.0");
		assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::goal).containsExactly("alpha_release_goal");
	}

	@Test
	void packDeclaringNoVersionIsStoredAtTheDefault() throws Exception {
		ProcessStore store = boundStore();
		store.savePack(pack("gamma", "gamma_goal", null));

		assertThat(versionsOf("gamma")).containsExactly("1.0.0");
		assertThat(store.loadPack("gamma")).isNotNull();
	}

	@Test
	void discoveryHistoryAppendsAndReturnsMostRecentFirst() {
		ProcessStore store = boundStore();
		store.recordDiscovery("folder", "/docs/a", 3);
		store.recordDiscovery("git", "https://x/repo", 7);

		List<ProcessStore.DiscoveryRecord> history = store.discoveries();
		assertThat(history).hasSize(2);
		assertThat(history.get(0).location()).isEqualTo("https://x/repo");
		assertThat(history.get(0).docCount()).isEqualTo(7);
		assertThat(history.get(1).location()).isEqualTo("/docs/a");
		assertThat(history.get(1).docCount()).isEqualTo(3);
	}

	@Test
	void emptyStoreListsNoPacksAndNoDiscoveries() {
		ProcessStore store = boundStore();
		assertThat(store.listPacks()).isEmpty();
		assertThat(store.discoveries()).isEmpty();
	}

	/** The versions stored for one pack name, read straight off the table. */
	private List<String> versionsOf(String name) throws Exception {
		List<String> versions = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(url, "sa", "");
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("SELECT version FROM packs WHERE name = '" + name + "'")) {
			while (rows.next()) {
				versions.add(rows.getString(1));
			}
		}
		return versions;
	}

}
