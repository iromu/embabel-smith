/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.embabel.smith.studio.storage.entity.PackEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves an already-deployed {@code packs} table onto the {@code (name, version)} key
 * (change: publish-keeps-versions, task 2.2). Nothing else will: the studio's only schema
 * mechanism is {@code hbm2ddl.auto = update}, which adds tables and columns but never
 * re-keys a primary key, so a deployed studio would otherwise keep a name-keyed table
 * underneath an entity expecting two key columns — a runtime failure in exactly the
 * deployment whose rows matter.
 *
 * <p>
 * The detector reads the primary-key columns through JDBC metadata. When they are not
 * exactly {@code (name, version)} the table is rebuilt in place: the column is added,
 * existing rows are backfilled with the version parsed from their stored {@code content}
 * JSON (falling back to {@link PackEntity#DEFAULT_VERSION} when the artifact declared
 * none), and the key is replaced. The rows are kept rather than dropped because
 * {@code content} can be the only surviving copy of an artifact whose folder was already
 * overwritten by a loop pass.
 *
 * <p>
 * Postgres and H2 take the same path: the migration runs against whatever database the
 * binding opened, identifiers are read from metadata and issued unquoted because that is
 * how Hibernate created them, and the DDL used is the subset both speak. A database with
 * no {@code packs} table is left alone — Hibernate creates the new shape there.
 *
 * @author Ivan Rodriguez
 */
public final class PacksKeyMigration {

	private static final Logger log = LoggerFactory.getLogger(PacksKeyMigration.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String TABLE = "packs";

	private static final String NAME = "name";

	private static final String VERSION = "version";

	private static final String CONTENT = "content";

	private static final String NEW_KEY_CONSTRAINT = "packs_name_version_pkey";

	private PacksKeyMigration() {
	}

	/**
	 * Rebuilds the {@code packs} table when it is keyed by anything other than
	 * {@code (name, version)}.
	 * @param connection an open connection to the database being bound.
	 * @throws SQLException when the table's shape cannot be read or changed.
	 */
	public static void migrateIfNameKeyed(Connection connection) throws SQLException {
		DatabaseMetaData metaData = connection.getMetaData();
		String catalog = connection.getCatalog();
		String table = findTable(metaData, catalog);
		if (table == null) {
			return;
		}
		List<String> key = primaryKeyColumns(metaData, catalog, table);
		if (List.of(NAME, VERSION).equals(key)) {
			return;
		}
		log.info("Rebuilding the {} table key: found {}, expected ({}, {})", table, key, NAME, VERSION);
		rebuild(connection, table, key);
	}

	/**
	 * The name the database stores the packs table under, or {@code null} when it has
	 * none — H2 keeps unquoted identifiers upper case, Postgres lower case, and the
	 * metadata lookup is case-sensitive.
	 * @param metaData the connection metadata.
	 * @param catalog the connection catalog.
	 * @return the stored table name, or {@code null}.
	 * @throws SQLException when the catalog cannot be read.
	 */
	private static String findTable(DatabaseMetaData metaData, String catalog) throws SQLException {
		try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[] { "TABLE" })) {
			while (rs.next()) {
				if (TABLE.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
					return rs.getString("TABLE_NAME");
				}
			}
		}
		return null;
	}

	private static List<String> primaryKeyColumns(DatabaseMetaData metaData, String catalog, String table)
			throws SQLException {
		List<String> columns = new ArrayList<>();
		try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, table)) {
			while (rs.next()) {
				columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
			}
		}
		// Sorted so the comparison is about which columns form the key, not the order
		// they
		// were declared in: a key over these two columns is the key the entity expects.
		Collections.sort(columns);
		return columns;
	}

	private static String primaryKeyConstraintName(DatabaseMetaData metaData, String catalog, String table)
			throws SQLException {
		try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, table)) {
			if (rs.next()) {
				return rs.getString("PK_NAME");
			}
		}
		return null;
	}

	private static boolean hasColumn(DatabaseMetaData metaData, String catalog, String table, String column)
			throws SQLException {
		// The columns are listed and matched here rather than passed as a metadata
		// pattern: H2
		// stores an unquoted identifier upper case and matches a lower-case pattern
		// exactly.
		try (ResultSet rs = metaData.getColumns(catalog, null, table, null)) {
			while (rs.next()) {
				if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
					return true;
				}
			}
		}
		return false;
	}

	private static void rebuild(Connection connection, String table, List<String> oldKey) throws SQLException {
		if (!hasColumn(connection.getMetaData(), connection.getCatalog(), table, VERSION)) {
			execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + VERSION + " VARCHAR(255)");
		}
		backfill(connection, table);
		// Both databases accept this form, and it is what lets the key be added over a
		// column
		// that was created nullable so it could be populated.
		execute(connection, "ALTER TABLE " + table + " ALTER COLUMN " + VERSION + " SET NOT NULL");
		String constraint = primaryKeyConstraintName(connection.getMetaData(), connection.getCatalog(), table);
		if (constraint != null) {
			execute(connection, "ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
		}
		execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + NEW_KEY_CONSTRAINT + " PRIMARY KEY (" + NAME
				+ ", " + VERSION + ")");
		log.info("Rebuilt the {} table key from {} to ({}, {}), keeping the stored rows", table, oldKey, NAME, VERSION);
	}

	/**
	 * Gives every stored row the version its own {@code content} declares, so the new key
	 * is populated before it is enforced.
	 * @param connection the connection to the bound database.
	 * @param table the stored packs table name.
	 * @throws SQLException when a row cannot be read or updated.
	 */
	private static void backfill(Connection connection, String table) throws SQLException {
		Map<String, String> versions = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT " + NAME + ", " + CONTENT + " FROM " + table)) {
			while (rs.next()) {
				versions.put(rs.getString(1), declaredVersion(rs.getString(2)));
			}
		}
		try (PreparedStatement update = connection
			.prepareStatement("UPDATE " + table + " SET " + VERSION + " = ? WHERE " + NAME + " = ?")) {
			for (Map.Entry<String, String> row : versions.entrySet()) {
				update.setString(1, row.getValue());
				update.setString(2, row.getKey());
				update.executeUpdate();
			}
		}
	}

	/**
	 * The version a stored {@code content} document declares at {@code manifest.version}.
	 * @param content the serialized pack, possibly {@code null} or unreadable.
	 * @return the declared version, or {@link PackEntity#DEFAULT_VERSION}.
	 */
	private static String declaredVersion(String content) {
		if ((content == null) || content.isBlank()) {
			return PackEntity.DEFAULT_VERSION;
		}
		try {
			JsonNode version = JSON.readTree(content).path("manifest").path("version");
			String declared = version.asText("");
			return (declared == null || declared.isBlank()) ? PackEntity.DEFAULT_VERSION : declared;
		}
		catch (Exception ex) {
			// An unreadable row is still a row worth keeping; it just has no version to
			// claim.
			return PackEntity.DEFAULT_VERSION;
		}
	}

	private static void execute(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

}
