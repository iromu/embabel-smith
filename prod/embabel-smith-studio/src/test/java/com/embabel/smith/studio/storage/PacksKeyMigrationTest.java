/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.studio.storage.StorageSettings.DbSettings;
import com.embabel.smith.studio.storage.StorageSettings.TraceSettings;
import com.embabel.smith.studio.store.ProcessStore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The start-up re-key (change: publish-keeps-versions, task 2.2), run the way it happens
 * in a deployment: a database is created holding the OLD shape — {@code packs} keyed by
 * name alone, no version column, rows already stored — and the studio's persistence is
 * then bound to it. What is asserted is that table and entity agree afterwards, not that
 * the new mapping works on a fresh database.
 *
 * @author Ivan Rodriguez
 */
class PacksKeyMigrationTest {

	@TempDir
	Path root;

	/** Serializes the way the store does, so the seeded rows look like rows it wrote. */
	private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

	@Test
	void aNameKeyedTableIsRebuiltAndItsRowsKept() throws Exception {
		String url = memUrl();
		createNameKeyedTable(url);

		StorageContext context = bind(url);
		try {
			// Table and entity agree: the key is exactly (name, version).
			assertThat(primaryKeyColumns(url)).containsExactly("name", "version");

			// The rows survived, each backfilled from the version its own content
			// declares —
			// and the one whose artifact declared none got the default.
			assertThat(primaryKeys(url)).containsExactlyInAnyOrder("alpha|2.3.0", "beta|1.0.0");

			// The entity reads the migrated rows and writes new versions onto them.
			ProcessStore store = new ProcessStore(context);
			assertThat(store.loadPack("alpha").version()).isEqualTo("2.3.0");
			assertThat(store.listPacks()).extracting(ProcessStore.PackRecord::name)
				.containsExactlyInAnyOrder("alpha", "beta");

			store.savePack(spec("alpha", "3.0.0"));
			assertThat(primaryKeys(url)).containsExactlyInAnyOrder("alpha|2.3.0", "alpha|3.0.0", "beta|1.0.0");
		}
		finally {
			context.shutdown();
		}
	}

	@Test
	void aTableAlreadyOnTheNewKeyIsLeftAlone() throws Exception {
		String url = memUrl();
		createNameKeyedTable(url);
		bind(url).shutdown();

		// Binding again runs the detector over the new shape: it must not rebuild, fail,
		// or
		// drop the rows it already backfilled.
		StorageContext context = bind(url);
		try {
			assertThat(primaryKeyColumns(url)).containsExactly("name", "version");
			assertThat(primaryKeys(url)).containsExactlyInAnyOrder("alpha|2.3.0", "beta|1.0.0");
			assertThat(new ProcessStore(context).loadPack("beta").name()).isEqualTo("beta");
		}
		finally {
			context.shutdown();
		}
	}

	/**
	 * The same re-key against a database that stores identifiers the way Postgres does —
	 * unquoted names lower case — which is the case the migration's unquoted DDL has to
	 * survive. H2 in PostgreSQL mode is not Postgres: it covers the identifier casing the
	 * DDL is issued in, not the Postgres-only constraint forms, which remain unexecuted
	 * here.
	 */
	@Test
	void aNameKeyedTableIsRebuiltWhenIdentifiersAreStoredLowerCase() throws Exception {
		String url = memUrl("MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;");
		createNameKeyedTable(url);

		StorageContext context = bind(url);
		try {
			assertThat(primaryKeyColumns(url)).containsExactly("name", "version");
			assertThat(primaryKeys(url)).containsExactlyInAnyOrder("alpha|2.3.0", "beta|1.0.0");

			ProcessStore store = new ProcessStore(context);
			assertThat(store.loadPack("alpha").version()).isEqualTo("2.3.0");

			store.savePack(spec("alpha", "3.0.0"));
			assertThat(primaryKeys(url)).containsExactlyInAnyOrder("alpha|2.3.0", "alpha|3.0.0", "beta|1.0.0");
		}
		finally {
			context.shutdown();
		}
	}

	private StorageContext bind(String url) {
		StorageContext context = new StorageContext(new StorageSettingsService(root),
				new JsonlTraceStore(root.resolve("runs.jsonl")), null, null, null);
		context.bind(new StorageSettings(new DbSettings(url, "sa", ""),
				new TraceSettings(StorageSettings.TRACE_JSONL, null, null, null)));
		return context;
	}

	/**
	 * A {@code packs} table as it was before this change: the name is the whole primary
	 * key and there is no version column at all.
	 */
	private void createNameKeyedTable(String url) throws Exception {
		try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE packs (name VARCHAR(255) NOT NULL PRIMARY KEY, goal VARCHAR(255), "
						+ "steps INTEGER, saved_at TIMESTAMP, content VARCHAR(100000))");
			}
			insertStoredPack(connection, "alpha", "2.3.0");
			insertStoredPack(connection, "beta", null);
		}
	}

	private void insertStoredPack(Connection connection, String name, String version) throws Exception {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO packs (name, goal, steps, saved_at, content) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)")) {
			insert.setString(1, name);
			insert.setString(2, name + "_goal");
			insert.setInt(3, 1);
			insert.setString(4, json.writeValueAsString(spec(name, version)));
			insert.executeUpdate();
		}
	}

	private static AgentSpec spec(String name, String version) {
		ActionStepSpec step = ActionStepSpec.action(name + "_step", "a step", List.of("UserInput"), name + "_Out",
				"Produce {{userInput}} into the result.");
		GoalStepSpec goal = GoalStepSpec.exportedGoal(name + "_goal", "the goal", name + "_Out");
		DynamicTypeDef type = new DynamicTypeDef(name + "_Out", "an output", null, null, null);
		return new AgentSpec(new AgentSpecManifest(name, version), goal, List.of(step), List.of(type));
	}

	/**
	 * The primary-key columns in key order, read through JDBC metadata. The table is
	 * looked up by the name the database actually stores it under — H2 keeps an unquoted
	 * identifier upper case, PostgreSQL mode lower-cases it, and {@code %} is not a
	 * wildcard here — which is the lookup the migration itself performs.
	 */
	private static List<String> primaryKeyColumns(String url) throws SQLException {
		TreeMap<Integer, String> byPosition = new TreeMap<>();
		try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
			try (ResultSet keys = connection.getMetaData()
				.getPrimaryKeys(connection.getCatalog(), null, storedPackTable(connection))) {
				while (keys.next()) {
					byPosition.put(keys.getInt("KEY_SEQ"), keys.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
				}
			}
		}
		return new ArrayList<>(byPosition.values());
	}

	private static String storedPackTable(Connection connection) throws SQLException {
		try (ResultSet tables = connection.getMetaData()
			.getTables(connection.getCatalog(), null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if ("packs".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return tables.getString("TABLE_NAME");
				}
			}
		}
		throw new IllegalStateException("the database has no packs table to check");
	}

	/** Every stored row as {@code name|version}. */
	private static List<String> primaryKeys(String url) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(url, "sa", "");
				Statement statement = connection.createStatement();
				ResultSet stored = statement.executeQuery("SELECT name, version FROM packs")) {
			while (stored.next()) {
				rows.add(stored.getString(1) + "|" + stored.getString(2));
			}
		}
		return rows;
	}

	private static String memUrl() {
		return memUrl("");
	}

	private static String memUrl(String options) {
		return "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";" + options
				+ "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
	}

}
