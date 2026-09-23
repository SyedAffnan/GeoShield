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

    @Test
    @DisplayName("Regression: Murder investigation with body parts and Fire Force assistance classifies as CRIME_AND_VIOLENCE")
    void murderWithFireForceAssistance_classifiesAsCrimeAndViolence() {
        String title = "Murder investigation launched after body parts found in river";
        String desc = "Police initiated a probe into the homicide. Fire Force assistance was requested to recover remains.";

        SafetyEventCategory category = classifier.classifyCategory(title, desc);
        assertThat(category).isEqualTo(SafetyEventCategory.CRIME_AND_VIOLENCE);
        assertThat(category).isNotEqualTo(SafetyEventCategory.FIRE_AND_EXPLOSION);
    }

    @Test
    @DisplayName("Regression: True fire story classifies as FIRE_AND_EXPLOSION")
    void trueFireStory_classifiesAsFireAndExplosion() {
        String title = "Fire broke out in a commercial building";
        String desc = "Firefighters battled raging flames as smoke engulfed the complex.";

        SafetyEventCategory category = classifier.classifyCategory(title, desc);
        assertThat(category).isEqualTo(SafetyEventCategory.FIRE_AND_EXPLOSION);
    }

    @Test
    @DisplayName("Regression: Fire department assisted police during murder investigation classifies as CRIME_AND_VIOLENCE")
    void fireDepartmentAssistedPolice_doesNotOverrideMurderSubject() {
        String title = "Fire department assisted police during murder investigation";
        String desc = "Divers recovered evidence linked to the killing of a local merchant.";

        SafetyEventCategory category = classifier.classifyCategory(title, desc);
        assertThat(category).isEqualTo(SafetyEventCategory.CRIME_AND_VIOLENCE);
        assertThat(category).isNotEqualTo(SafetyEventCategory.FIRE_AND_EXPLOSION);
    }

    @Test
    @DisplayName("Regression: Agency name alone does not trigger false disaster or fire category")
    void agencyNameAlone_doesNotTriggerDisasterCategory() {
        String title = "Disaster Management Authority conducts routine meeting with Fire Service";
        String desc = "Officials held annual administrative review of budget allocations.";

        SafetyEventCategory category = classifier.classifyCategory(title, desc);
        assertThat(category).isEqualTo(SafetyEventCategory.GENERAL_SAFETY);
    }
}
