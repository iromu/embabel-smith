/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.embabel.smith.studio.storage.entity.PackEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The start-up re-key against a real Postgres (change: publish-keeps-versions, task 2.2
 * residual). The offline {@link PacksKeyMigrationTest} runs the same rebuild on H2 in
 * PostgreSQL mode, which proves the identifier casing and nothing else: the two
 * statements this test exercises are the ones whose spelling Postgres decides, and the
 * deployment that holds a name-keyed table in the persistent volume is a Postgres.
 *
 * <p>
 * It is a live test and stays out of the default run: it is tagged {@code live} and gated
 * on {@code SMITH_POSTGRES_URL}, so an ordinary build neither needs a server nor connects
 * to one. Point it at a scratch database — the guard below refuses any other, because a
 * migration test that seeds by dropping a table must not be able to aim at real data.
 *
 * @author Ivan Rodriguez
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "SMITH_POSTGRES_URL", matches = ".+")
class PacksKeyMigrationPostgresTest {

	/**
	 * The database names this test may run against. Anything else fails the test rather
	 * than being touched: the studio's own database is named for the product, so a name
	 * that does not read as scratch is treated as someone's data.
	 */
	private static final Pattern SCRATCH_DATABASE = Pattern.compile("(verify|test|scratch|tmp)",
			Pattern.CASE_INSENSITIVE);

	private String url;

	@Test
	void aNameKeyedTableIsRebuiltOnRealPostgresAndItsRowsKept() throws Exception {
		this.url = System.getenv("SMITH_POSTGRES_URL");
		guardAgainstNonScratchDatabase(this.url);

		seedNameKeyedTable();
		try (Connection connection = open()) {
			PacksKeyMigration.migrateIfNameKeyed(connection);

			assertThat(keyConstraintName(connection)).isEqualTo("packs_name_version_pkey");
			assertThat(keyColumns(connection)).containsExactly("name", "version");
			assertThat(isNotNull(connection, "version")).as("SET NOT NULL took effect").isTrue();

			// The rows survive the rebuild, each carrying the version its own content
			// declared or the entity's default — this is the property the change exists
			// for.
			assertThat(rows(connection)).containsExactly("alpha=1.4.2", "beta=" + PackEntity.DEFAULT_VERSION);

			// Two versions of one name is the whole point, so the new key really is two
			// columns: this insert must succeed where the old name-only key refused it.
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO packs (name, version, content) VALUES ('alpha','9.9.9','{}')");
			}
			assertThat(rows(connection)).contains("alpha=9.9.9");
		}
		finally {
			dropSeededTable();
		}
	}

	private void seedNameKeyedTable() throws SQLException {
		try (Connection connection = open(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("DROP TABLE IF EXISTS packs");
			// The old shape, with the constraint name Postgres generates for it, so the
			// migration reads that name out of metadata the way it will in a deployment.
			statement.executeUpdate("CREATE TABLE packs (name varchar(255) NOT NULL, content text,"
					+ " CONSTRAINT packs_pkey PRIMARY KEY (name))");
			statement.executeUpdate("INSERT INTO packs (name, content) VALUES"
					+ " ('alpha', '{\"manifest\":{\"version\":\"1.4.2\"}}'), ('beta', '{\"manifest\":{}}')");
		}
	}

	private void dropSeededTable() {
		try (Connection connection = open(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("DROP TABLE IF EXISTS packs");
		}
		catch (SQLException ex) {
			// Nothing to clean up in the run's outcome; the next run drops it anyway.
		}
	}

	private void guardAgainstNonScratchDatabase(String jdbcUrl) {
		// The database is the last path segment, after the authority: matching the first
		// slash in a jdbc:postgresql://host:5433/db URL yields the host, which would make
		// this guard refuse every run. A URL with no path yields the authority, which
		// does
		// not read as scratch and is refused — the safe direction for a mistake.
		String tail = jdbcUrl.substring(jdbcUrl.lastIndexOf('/') + 1);
		int parameters = tail.indexOf('?');
		String database = (parameters >= 0) ? tail.substring(0, parameters) : tail;
		if (!SCRATCH_DATABASE.matcher(database).find()) {
			fail("SMITH_POSTGRES_URL points at database '" + database + "', which does not read as a scratch "
					+ "database. This test seeds by dropping the packs table, so it refuses to run unless the "
					+ "name contains verify, test, scratch or tmp.");
		}
	}

	private Connection open() throws SQLException {
		String user = System.getenv().getOrDefault("SMITH_POSTGRES_USER", "embabel_smith");
		String password = System.getenv().getOrDefault("SMITH_POSTGRES_PASSWORD", "embabel_smith");
		return DriverManager.getConnection(this.url, user, password);
	}

	private String keyConstraintName(Connection connection) throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		try (ResultSet rs = metaData.getPrimaryKeys(connection.getCatalog(), null, "packs")) {
			if (rs.next()) {
				return rs.getString("PK_NAME");
			}
		}
		return null;
	}

	private List<String> keyColumns(Connection connection) throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		List<String> columns = new ArrayList<>();
		try (ResultSet rs = metaData.getPrimaryKeys(connection.getCatalog(), null, "packs")) {
			while (rs.next()) {
				columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
			}
		}
		return columns;
	}

	private boolean isNotNull(Connection connection, String column) throws SQLException {
		try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, "packs", null)) {
			while (rs.next()) {
				if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
					// The metadata column is numeric (columnNoNulls is 0), not boolean.
					return rs.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
				}
			}
		}
		return false;
	}

	private List<String> rows(Connection connection) throws SQLException {
		List<String> rows = new ArrayList<>();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT name, version FROM packs ORDER BY name, version")) {
			while (rs.next()) {
				rows.add(rs.getString("name") + "=" + rs.getString("version"));
			}
		}
		return rows;
	}

}
