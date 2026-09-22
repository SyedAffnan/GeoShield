package com.geoshield.historicaldata.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures the historical_safety_records table conforms to the Step 5 hierarchical schema:
 * 1. Guarantees non-null parent_unit column with default 'India' exists.
 * 2. Backfills existing rows where parent_unit is null or empty before applying NOT NULL constraint.
 * 3. Modifies column lengths only when existing definitions differ from required definitions.
 * 4. Removes legacy uk_historical_safety_record_natural index/constraint if present.
 * 5. Ensures uk_historical_safety_record_hierarchy unique constraint is active with all 7 dimensions.
 * 6. Verifies constraints and column definitions, failing fast if schema integrity cannot be guaranteed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HistoricalSafetyRecordSchemaMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(HistoricalSafetyRecordSchemaMigrator.class);

    private static final List<String> EXPECTED_HIERARCHY_COLUMNS = List.of(
            "source", "source_year", "geographic_level", "parent_unit", "geographic_unit", "category", "metric_name"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public HistoricalSafetyRecordSchemaMigrator(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateNow();
    }

    public void migrateNow() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            log.info("HistoricalSafetyRecordSchemaMigrator checking database: {}", dbProduct);

            if (!tableExists(meta, "historical_safety_records")) {
                log.info("Table historical_safety_records does not exist yet. Skipping schema migration.");
                return;
            }

            if ("MySQL".equalsIgnoreCase(dbProduct)) {
                migrateMySQL();
            } else if ("H2".equalsIgnoreCase(dbProduct)) {
                migrateH2(meta);
            } else {
                log.info("Database {} does not require specific MySQL historical schema migration.", dbProduct);
            }
        } catch (SQLException e) {
            log.error("Fatal error during historical schema migration", e);
            throw new IllegalStateException("Historical safety record schema migration failed", e);
        }
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
            if (rs.next()) return true;
        }
        return false;
    }

    private record ColumnMeta(String name, String isNullable, Integer maxCharLength, String columnDefault) {}

    private Map<String, ColumnMeta> getColumnsMetadata() {
        String sql = "SELECT COLUMN_NAME, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT " +
                     "FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records'";
        List<ColumnMeta> list = jdbcTemplate.query(sql, (rs, rowNum) -> new ColumnMeta(
                rs.getString("COLUMN_NAME"),
                rs.getString("IS_NULLABLE"),
                rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ? rs.getInt("CHARACTER_MAXIMUM_LENGTH") : null,
                rs.getString("COLUMN_DEFAULT")
        ));
        Map<String, ColumnMeta> map = new HashMap<>();
        for (ColumnMeta cm : list) {
            map.put(cm.name().toLowerCase(), cm);
        }
        return map;
    }

    private void migrateMySQL() {
        log.info("Executing MySQL schema migration for historical_safety_records...");

        Map<String, ColumnMeta> cols = getColumnsMetadata();

        // 1. If parent_unit does not exist, add it
        if (!cols.containsKey("parent_unit")) {
            log.info("Adding parent_unit column to historical_safety_records in MySQL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records ADD COLUMN parent_unit VARCHAR(64) NOT NULL DEFAULT 'India'");
            cols = getColumnsMetadata();
        }

        // 2. Backfill existing rows BEFORE modifying to NOT NULL (Finding 2)
        // Check for any DISTRICT records with null/empty parent_unit
        Integer nullDistrictCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historical_safety_records WHERE geographic_level = 'DISTRICT' AND (parent_unit IS NULL OR TRIM(parent_unit) = '')",
                Integer.class);
        if (nullDistrictCount != null && nullDistrictCount > 0) {
            throw new IllegalStateException("Cannot migrate historical_safety_records: " + nullDistrictCount +
                    " DISTRICT records have null or empty parent_unit. District records must specify a valid parent State/UT.");
        }

        // Backfill NATIONAL and STATE_UT legacy records to canonical 'India'
        jdbcTemplate.execute("UPDATE historical_safety_records SET parent_unit = 'India' " +
                "WHERE (geographic_level IN ('NATIONAL', 'STATE_UT') OR geographic_level IS NULL) " +
                "AND (parent_unit IS NULL OR TRIM(parent_unit) = '')");

        // Verify zero rows have null or empty parent_unit
        Integer remainingNullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historical_safety_records WHERE parent_unit IS NULL OR TRIM(parent_unit) = ''",
                Integer.class);
        if (remainingNullCount != null && remainingNullCount > 0) {
            throw new IllegalStateException("Cannot migrate historical_safety_records: " + remainingNullCount +
                    " records still have null or empty parent_unit after backfill.");
        }

        // 3. Conditional column modifications - only modify if definition actually differs (Finding 8)
        ColumnMeta puMeta = cols.get("parent_unit");
        if (puMeta != null && (!"NO".equalsIgnoreCase(puMeta.isNullable()) || puMeta.maxCharLength() == null || puMeta.maxCharLength() != 64)) {
            log.info("Adjusting parent_unit column definition to VARCHAR(64) NOT NULL DEFAULT 'India'...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records MODIFY COLUMN parent_unit VARCHAR(64) NOT NULL DEFAULT 'India'");
        }

        ColumnMeta catMeta = cols.get("category");
        if (catMeta != null && (!"NO".equalsIgnoreCase(catMeta.isNullable()) || catMeta.maxCharLength() == null || catMeta.maxCharLength() != 160)) {
            log.info("Adjusting category column definition to VARCHAR(160) NOT NULL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records MODIFY COLUMN category VARCHAR(160) NOT NULL");
        }

        ColumnMeta mnMeta = cols.get("metric_name");
        if (mnMeta != null && (!"NO".equalsIgnoreCase(mnMeta.isNullable()) || mnMeta.maxCharLength() == null || mnMeta.maxCharLength() != 160)) {
            log.info("Adjusting metric_name column definition to VARCHAR(160) NOT NULL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records MODIFY COLUMN metric_name VARCHAR(160) NOT NULL");
        }

        ColumnMeta guMeta = cols.get("geographic_unit");
        if (guMeta != null && (!"NO".equalsIgnoreCase(guMeta.isNullable()) || guMeta.maxCharLength() == null || guMeta.maxCharLength() != 128)) {
            log.info("Adjusting geographic_unit column definition to VARCHAR(128) NOT NULL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records MODIFY COLUMN geographic_unit VARCHAR(128) NOT NULL");
        }

        ColumnMeta srcMeta = cols.get("source");
        if (srcMeta != null && (!"NO".equalsIgnoreCase(srcMeta.isNullable()) || srcMeta.maxCharLength() == null || srcMeta.maxCharLength() != 128)) {
            log.info("Adjusting source column definition to VARCHAR(128) NOT NULL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records MODIFY COLUMN source VARCHAR(128) NOT NULL");
        }

        // 4. Remove legacy uk_historical_safety_record_natural if present
        Integer legacyIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_natural'",
                Integer.class);

        if (legacyIndexCount != null && legacyIndexCount > 0) {
            log.info("Dropping legacy unique index uk_historical_safety_record_natural in MySQL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records DROP INDEX uk_historical_safety_record_natural");
        }

        // 5. Ensure uk_historical_safety_record_hierarchy is active
        Integer hierarchyIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_hierarchy'",
                Integer.class);

        if (hierarchyIndexCount == null || hierarchyIndexCount == 0) {
            log.info("Creating unique constraint uk_historical_safety_record_hierarchy in MySQL...");
            jdbcTemplate.execute("ALTER TABLE historical_safety_records ADD CONSTRAINT uk_historical_safety_record_hierarchy " +
                    "UNIQUE (source, source_year, geographic_level, parent_unit, geographic_unit, category, metric_name)");
        }

        // 6. Comprehensive Verification (Finding 3)
        Map<String, ColumnMeta> verifiedCols = getColumnsMetadata();
        verifyColumn(verifiedCols, "parent_unit", "NO", 64);
        verifyColumn(verifiedCols, "category", "NO", 160);
        verifyColumn(verifiedCols, "metric_name", "NO", 160);
        verifyColumn(verifiedCols, "geographic_unit", "NO", 128);
        verifyColumn(verifiedCols, "source", "NO", 128);

        Integer finalLegacyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_natural'",
                Integer.class);
        if (finalLegacyCount != null && finalLegacyCount > 0) {
            throw new IllegalStateException("Legacy index uk_historical_safety_record_natural is still active after migration!");
        }

        List<String> actualHierarchyCols = jdbcTemplate.query(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_hierarchy' " +
                "ORDER BY SEQ_IN_INDEX ASC",
                (rs, rowNum) -> rs.getString("COLUMN_NAME").toLowerCase()
        );

        if (actualHierarchyCols.size() != EXPECTED_HIERARCHY_COLUMNS.size() || !actualHierarchyCols.equals(EXPECTED_HIERARCHY_COLUMNS)) {
            throw new IllegalStateException("Hierarchy unique index uk_historical_safety_record_hierarchy does not contain exactly the 7 expected columns in order. Expected: " +
                    EXPECTED_HIERARCHY_COLUMNS + ", Found: " + actualHierarchyCols);
        }

        log.info("MySQL schema migration for historical_safety_records verified successfully.");
    }

    private void verifyColumn(Map<String, ColumnMeta> cols, String columnName, String expectedNullable, int expectedLength) {
        ColumnMeta meta = cols.get(columnName.toLowerCase());
        if (meta == null) {
            throw new IllegalStateException("Required column '" + columnName + "' missing from historical_safety_records table.");
        }
        if (!expectedNullable.equalsIgnoreCase(meta.isNullable())) {
            throw new IllegalStateException("Column '" + columnName + "' has IS_NULLABLE = '" + meta.isNullable() + "', expected: '" + expectedNullable + "'.");
        }
        if (meta.maxCharLength() == null || meta.maxCharLength() != expectedLength) {
            throw new IllegalStateException("Column '" + columnName + "' has CHARACTER_MAXIMUM_LENGTH = " + meta.maxCharLength() + ", expected: " + expectedLength + ".");
        }
    }

    private void migrateH2(DatabaseMetaData meta) {
        log.info("Executing H2 schema migration for historical_safety_records...");
        try {
            boolean colFound = false;
            try (ResultSet rs = meta.getColumns(null, null, "HISTORICAL_SAFETY_RECORDS", "PARENT_UNIT")) {
                if (rs.next()) colFound = true;
            }
            if (!colFound) {
                try (ResultSet rs = meta.getColumns(null, null, "historical_safety_records", "parent_unit")) {
                    if (rs.next()) colFound = true;
                }
            }
            if (!colFound) {
                jdbcTemplate.execute("ALTER TABLE historical_safety_records ADD COLUMN parent_unit VARCHAR(64) DEFAULT 'India' NOT NULL");
            }
            jdbcTemplate.execute("UPDATE historical_safety_records SET parent_unit = 'India' WHERE parent_unit IS NULL OR parent_unit = ''");
        } catch (Exception e) {
            log.warn("H2 migration notice: {}", e.getMessage());
        }
    }
}
