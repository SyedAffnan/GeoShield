package com.geoshield.historicaldata.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
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
class DistrictHistoricalSafetyRecordRepositoryTest {
    private static final String SOURCE = "MoRTH Road Accidents in India 2024 - District Repo Test";
    private static final int SOURCE_YEAR = 2024;
    private static final String CATEGORY = "Total Number of Persons Injured in Road Accidents";
    private static final String METRIC_NAME = "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024";

    @Autowired
    private HistoricalSafetyRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdRecordIds = new ArrayList<>();

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
    @DisplayName("Identical district names across different parent states coexist without collision")
    void crossStateDistrictsWithSameNameCoexist() {
        HistoricalSafetyRecord bilaspurHp = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Himachal Pradesh", "Bilaspur",
                CATEGORY, METRIC_NAME, new BigDecimal("45.5000"), false);

        HistoricalSafetyRecord bilaspurCg = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Chhattisgarh", "Bilaspur",
                CATEGORY, METRIC_NAME, new BigDecimal("65.2000"), false);

        assertDoesNotThrow(() -> {
            repository.saveAndFlush(bilaspurHp);
            repository.saveAndFlush(bilaspurCg);
        });

        var foundHp = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Himachal Pradesh", "Bilaspur", "Per Lakh Population");
        var foundCg = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Chhattisgarh", "Bilaspur", "Per Lakh Population");

        assertTrue(foundHp.isPresent());
        assertTrue(foundCg.isPresent());
        assertEquals(new BigDecimal("45.5000"), foundHp.get().getMetricValue());
        assertEquals(new BigDecimal("65.2000"), foundCg.get().getMetricValue());
    }

    @Test
    @DisplayName("Duplicate district records within the same parent state are strictly rejected")
    void duplicateSameStateDistrictRecordIsRejected() {
        HistoricalSafetyRecord rec1 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2000"), false);

        HistoricalSafetyRecord rec2 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, METRIC_NAME, new BigDecimal("80.0000"), false);

        repository.saveAndFlush(rec1);
        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(rec2);
        });
    }

    @Test
    @DisplayName("Duplicate State/UT records are strictly rejected under hierarchy constraint")
    void duplicateStateUtRecordIsRejected() {
        HistoricalSafetyRecord state1 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2000"), false);

        HistoricalSafetyRecord state2 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("79.0000"), false);

        repository.saveAndFlush(state1);
        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(state2);
        });
    }

    @Test
    @DisplayName("State/UT and District records with exact same geographic unit name coexist under distinct parentUnits")
    void stateAndDistrictWithSameNameCoexist() {
        // Finding 5: State/UT Delhi (parent India) vs District Delhi (parent Delhi)
        HistoricalSafetyRecord stateUt = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Delhi",
                CATEGORY, METRIC_NAME, new BigDecimal("24.0000"), false);

        HistoricalSafetyRecord district = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT, "Delhi", "Delhi",
                CATEGORY, METRIC_NAME, new BigDecimal("30.0000"), false);

        assertDoesNotThrow(() -> {
            repository.saveAndFlush(stateUt);
            repository.saveAndFlush(district);
        });

        var foundState = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT,
                "India", "Delhi", "Per Lakh Population");
        var foundDistrict = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.DISTRICT,
                "Delhi", "Delhi", "Per Lakh Population");

        assertTrue(foundState.isPresent());
        assertTrue(foundDistrict.isPresent());
        assertEquals(new BigDecimal("24.0000"), foundState.get().getMetricValue());
        assertEquals(new BigDecimal("30.0000"), foundDistrict.get().getMetricValue());
    }

    @Test
    @DisplayName("findSpecificMetric returns normal single-row State/UT match deterministically")
    void findSpecificMetricNormalSingleRowStateUtMatch() {
        HistoricalSafetyRecord rec = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2000"), false);
        repository.saveAndFlush(rec);

        var match = repository.findSpecificMetric(SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT,
                "India", "Karnataka", "Per Lakh Population");

        assertTrue(match.isPresent());
        assertEquals(new BigDecimal("77.2000"), match.get().getMetricValue());
    }

    @Test
    @DisplayName("findSpecificMetric is deterministic and does not throw when multiple rows match the pattern")
    void findSpecificMetricDeterministicWhenMultipleRowsMatch() {
        // Two distinct metrics matching "Per Lakh Population" under different metric names
        HistoricalSafetyRecord rec1 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Maharashtra",
                CATEGORY, "Primary Rate Per Lakh Population - 2024", new BigDecimal("55.1000"), false);
        HistoricalSafetyRecord rec2 = new HistoricalSafetyRecord(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Maharashtra",
                CATEGORY, "Secondary Rate Per Lakh Population - 2024", new BigDecimal("66.2000"), false);

        repository.saveAndFlush(rec1);
        repository.saveAndFlush(rec2);

        // findSpecificMetric must NOT throw IncorrectResultSizeDataAccessException
        var match = assertDoesNotThrow(() -> repository.findSpecificMetric(
                SOURCE, SOURCE_YEAR, GeographicLevel.STATE_UT, "India", "Maharashtra", "Per Lakh Population"));

        assertTrue(match.isPresent());
        // Deterministically chooses the first inserted row by id ASC
        assertEquals(new BigDecimal("55.1000"), match.get().getMetricValue());
    }
}
