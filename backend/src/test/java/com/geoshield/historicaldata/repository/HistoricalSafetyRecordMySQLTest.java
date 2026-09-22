package com.geoshield.historicaldata.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.service.HistoricalSafetyRecordSchemaMigrator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@SpringBootTest(properties = {
        "geoshield.jwt.secret=integration-test-secret-at-least-32-bytes-long-123456"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GEOSHIELD_DB_PASSWORD", matches = ".+")
class HistoricalSafetyRecordMySQLTest {
    private static final String SOURCE = "MoRTH Road Accidents in India 2024 - MySQL Test";
    private static final int SOURCE_YEAR = 2024;
    private static final String CATEGORY = "MySQL Test Injury Category";
    private static final String METRIC_NAME = "Total Number of Persons Injured Per Lakh - MySQL Test";

    private static final List<String> EXPECTED_HIERARCHY_COLUMNS = List.of(
            "source", "source_year", "geographic_level", "parent_unit", "geographic_unit", "category", "metric_name"
    );

    @Autowired
    private HistoricalSafetyRecordRepository repository;

    @Autowired
    private HistoricalSafetyRecordSchemaMigrator schemaMigrator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdRecordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        schemaMigrator.migrateNow();
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdRecordIds) {
            try {
                repository.deleteById(id);
            } catch (Exception ignored) { }
        }
        createdRecordIds.clear();
        try {
            jdbcTemplate.update("DELETE FROM historical_safety_records WHERE source = ?", SOURCE);
        } catch (Exception ignored) { }
    }

    @Test
    @DisplayName("Real MySQL schema has exact required column definitions, legacy index absent, and 7-dimension hierarchy index")
    void verifyMySQLSchemaMetadata() {
        // 1. Column definitions
        verifyColumnMetadata("parent_unit", "NO", 64);
        verifyColumnMetadata("category", "NO", 160);
        verifyColumnMetadata("metric_name", "NO", 160);
        verifyColumnMetadata("geographic_unit", "NO", 128);
        verifyColumnMetadata("source", "NO", 128);

        // 2. Legacy index must be absent
        Integer legacyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_natural'",
                Integer.class);
        assertNotNull(legacyCount);
        assertEquals(0, legacyCount, "Legacy constraint uk_historical_safety_record_natural must be absent from MySQL");

        // 3. Hierarchy index must contain exactly the 7 expected dimensions in order
        List<String> actualCols = jdbcTemplate.query(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND INDEX_NAME = 'uk_historical_safety_record_hierarchy' " +
                "ORDER BY SEQ_IN_INDEX ASC",
                (rs, rowNum) -> rs.getString("COLUMN_NAME").toLowerCase()
        );
        assertEquals(EXPECTED_HIERARCHY_COLUMNS, actualCols, "Hierarchy unique key must contain exactly the 7 dimensions in order");
    }

    private void verifyColumnMetadata(String columnName, String expectedNullable, int expectedLength) {
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND COLUMN_NAME = ?",
                String.class, columnName);
        assertEquals(expectedNullable, isNullable, "Column " + columnName + " IS_NULLABLE mismatch");

        Integer charLen = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historical_safety_records' AND COLUMN_NAME = ?",
                Integer.class, columnName);
        assertEquals(expectedLength, charLen, "Column " + columnName + " CHARACTER_MAXIMUM_LENGTH mismatch");
    }

    @Test
    @DisplayName("Real MySQL allows Bilaspur HP and Bilaspur CG to coexist without duplicate key violation")
    void crossStateDistrictsWithSameNameCoexistInRealMySQL() {
        HistoricalSafetyRecord bilaspurHp = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Himachal Pradesh", "Bilaspur",
                CATEGORY, METRIC_NAME, new BigDecimal("45.5000"), false);

        HistoricalSafetyRecord bilaspurCg = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Chhattisgarh", "Bilaspur",
                CATEGORY, METRIC_NAME, new BigDecimal("65.2000"), false);

        assertDoesNotThrow(() -> {
            HistoricalSafetyRecord savedHp = repository.saveAndFlush(bilaspurHp);
            createdRecordIds.add(savedHp.getId());
            HistoricalSafetyRecord savedCg = repository.saveAndFlush(bilaspurCg);
            createdRecordIds.add(savedCg.getId());
        });

        var foundHp = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Himachal Pradesh", "Bilaspur", "MySQL Test");
        var foundCg = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Chhattisgarh", "Bilaspur", "MySQL Test");

        assertTrue(foundHp.isPresent());
        assertTrue(foundCg.isPresent());
        assertEquals(new BigDecimal("45.5000"), foundHp.get().getMetricValue());
        assertEquals(new BigDecimal("65.2000"), foundCg.get().getMetricValue());
    }

    @Test
    @DisplayName("State/UT and District records with exact same geographic unit name coexist under distinct parentUnits in MySQL")
    void stateAndDistrictWithExactSameNameCoexistInRealMySQL() {
        // Finding 5: State/UT Delhi (parent India) vs District Delhi (parent Delhi)
        HistoricalSafetyRecord stateDelhi = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Delhi",
                CATEGORY, METRIC_NAME, new BigDecimal("24.0000"), false);

        HistoricalSafetyRecord districtDelhi = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Delhi", "Delhi",
                CATEGORY, METRIC_NAME, new BigDecimal("30.0000"), false);

        assertDoesNotThrow(() -> {
            HistoricalSafetyRecord savedState = repository.saveAndFlush(stateDelhi);
            createdRecordIds.add(savedState.getId());
            HistoricalSafetyRecord savedDistrict = repository.saveAndFlush(districtDelhi);
            createdRecordIds.add(savedDistrict.getId());
        });

        var foundState = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT,
                "India", "Delhi", "MySQL Test");
        var foundDistrict = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Delhi", "Delhi", "MySQL Test");

        assertTrue(foundState.isPresent());
        assertTrue(foundDistrict.isPresent());
        assertEquals(new BigDecimal("24.0000"), foundState.get().getMetricValue());
        assertEquals(new BigDecimal("30.0000"), foundDistrict.get().getMetricValue());
    }

    @Test
    @DisplayName("Real MySQL strictly rejects duplicate State/UT record under uk_historical_safety_record_hierarchy")
    void duplicateStateUtRecordIsRejectedInRealMySQL() {
        HistoricalSafetyRecord state1 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2000"), false);

        HistoricalSafetyRecord state2 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("79.0000"), false);

        HistoricalSafetyRecord saved1 = repository.saveAndFlush(state1);
        createdRecordIds.add(saved1.getId());

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(state2);
        }, "Real MySQL must throw DataIntegrityViolationException for duplicate State/UT record");
    }

    @Test
    @DisplayName("Real MySQL strictly rejects duplicate District record with same parent state")
    void duplicateDistrictRecordWithSameParentIsRejectedInRealMySQL() {
        HistoricalSafetyRecord dist1 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, METRIC_NAME, new BigDecimal("68.4000"), false);

        HistoricalSafetyRecord dist2 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, METRIC_NAME, new BigDecimal("70.0000"), false);

        HistoricalSafetyRecord saved1 = repository.saveAndFlush(dist1);
        createdRecordIds.add(saved1.getId());

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(dist2);
        }, "Real MySQL must throw DataIntegrityViolationException for duplicate district with same parent state");
    }
}
