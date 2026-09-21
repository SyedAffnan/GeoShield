package com.geoshield.news.service;

import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.SafetyEventCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventClassifierTest {

    private EventClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new EventClassifier();
    }

    @Test
    @DisplayName("Classifies natural disasters correctly")
    void classifiesNaturalDisaster() {
        SafetyEventCategory cat = classifier.classifyCategory("Massive landslide blocks highway", "Cloudburst reported in upper hills");
        assertThat(cat).isEqualTo(SafetyEventCategory.NATURAL_DISASTER);
    }

    @Test
    @DisplayName("Classifies traffic transit incidents correctly")
    void classifiesTrafficTransit() {
        SafetyEventCategory cat = classifier.classifyCategory("Bus collision on bypass road", "Two vehicles involved in head-on crash");
        assertThat(cat).isEqualTo(SafetyEventCategory.TRAFFIC_AND_TRANSIT);
    }

    @Test
    @DisplayName("Classifies crime and violence correctly")
    void classifiesCrimeAndViolence() {
        SafetyEventCategory cat = classifier.classifyCategory("Robbery reported at jewelry shop", "Armed gang snatched cash and fled");
        assertThat(cat).isEqualTo(SafetyEventCategory.CRIME_AND_VIOLENCE);
    }

    @Test
    @DisplayName("Classifies fire correctly")
    void classifiesFire() {
        SafetyEventCategory cat = classifier.classifyCategory("Major blaze breaks out in commercial complex", "Five fire tenders rushed to scene");
        assertThat(cat).isEqualTo(SafetyEventCategory.FIRE_AND_EXPLOSION);
    }

    @Test
    @DisplayName("Classifies civil disturbance correctly")
    void classifiesCivilDisturbance() {
        SafetyEventCategory cat = classifier.classifyCategory("Farmers stage road blockade protest", "Tear gas fired as agitation turns tense");
        assertThat(cat).isEqualTo(SafetyEventCategory.CIVIL_DISTURBANCE);
    }

    @Test
    @DisplayName("Classifies factual severe impact as CRITICAL")
    void classifiesCriticalSeverity() {
        EventSeverity sev = classifier.classifySeverity("Four killed in truck collision", "Police confirm fatalities at the spot");
        assertThat(sev).isEqualTo(EventSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Classifies road blockage or hospitalization as HIGH")
    void classifiesHighSeverity() {
        EventSeverity sev = classifier.classifySeverity("Highway blocked after landslide", "Several commuters injured and hospitalized");
        assertThat(sev).isEqualTo(EventSeverity.HIGH);
    }

    @Test
    @DisplayName("Classifies minor incidents as MODERATE")
    void classifiesModerateSeverity() {
        EventSeverity sev = classifier.classifySeverity("Theft at tourist hotel", "Police arrested two suspects following investigation");
        assertThat(sev).isEqualTo(EventSeverity.MODERATE);
    }
}
