package com.geoshield.news.service;

import com.geoshield.news.dto.RelevanceTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationRelevanceFilterTest {

    private LocationRelevanceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LocationRelevanceFilter();
    }

    @Test
    @DisplayName("Area in headline returns HIGH relevance")
    void areaInHeadlineReturnsHigh() {
        String area = "Shimla";
        String title = "Massive rockfall blocks highway in Shimla district";
        String desc = "Traffic has been diverted following heavy rain.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.HIGH);
        assertThat(filter.isAcceptableRelevance(tier)).isTrue();
    }

    @Test
    @DisplayName("Area in first 140 chars of description returns MEDIUM relevance")
    void areaInDescriptionSnippetReturnsMedium() {
        String area = "Bengaluru";
        String title = "Heavy rainfall causes severe waterlogging and power outages";
        String desc = "Several localities in Bengaluru woke up to flooded streets on Monday after torrential downpour.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.MEDIUM);
        assertThat(filter.isAcceptableRelevance(tier)).isTrue();
    }

    @Test
    @DisplayName("Area only deep in description returns LOW relevance and is dropped")
    void areaDeepInDescriptionReturnsLow() {
        String area = "Pune";
        String title = "Maharashtra cabinet clears new infrastructure projects across the state";
        String desc = "The state government held an extensive five-hour meeting in Mumbai on Monday afternoon discussing statewide road development plans across Nashik and Nagpur corridors, and toward the very end of the lengthy press briefing officials briefly mentioned Pune in connection with future transport studies.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.LOW);
        assertThat(filter.isAcceptableRelevance(tier)).isFalse();
    }

    @Test
    @DisplayName("Area not mentioned at all returns UNKNOWN")
    void areaNotMentionedReturnsUnknown() {
        String area = "Jaipur";
        String title = "Protest breaks out in Lucknow over transport fares";
        String desc = "Commuters faced difficulty in eastern Uttar Pradesh.";

        RelevanceTier tier = filter.calculateRelevance(area, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
        assertThat(filter.isAcceptableRelevance(tier)).isFalse();
    }

    @Test
    @DisplayName("Sanitizes HTML tags and entities properly")
    void sanitizesHtmlTagsProperly() {
        String raw = "<b>Breaking News:</b> Accident on NH-44 &amp; traffic halted.<script>alert('xss')</script>";
        String clean = filter.sanitizeText(raw);

        assertThat(clean).doesNotContain("<b>", "</b>", "<script>", "&amp;");
        assertThat(clean).contains("Breaking News:", "Accident on NH-44", "traffic halted.");
    }

    @Test
    @DisplayName("Whole-word matching avoids substring false positives like Goa in Goalpost")
    void wholeWordMatchingAvoidsFalsePositives() {
        assertThat(filter.matchesWord("Massive crowd gathers at Goa beach resort", "Goa")).isTrue();
        assertThat(filter.matchesWord("Footballer scored into the open goalpost", "Goa")).isFalse();
        assertThat(filter.matchesWord("Goalkeeping blunder cost team the match", "Goa")).isFalse();
    }

    @Test
    @DisplayName("Hierarchical relevance: Locality in headline yields HIGH relevance")
    void hierarchicalRelevanceLocalityInHeadlineYieldsHigh() {
        String locality = "Manali";
        String district = "Kullu";
        String stateUt = "Himachal Pradesh";
        String title = "Heavy snowfall blocks Rohtang Pass near Manali";
        String desc = "Authorities issue alert across Kullu district in Himachal Pradesh.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.HIGH);
    }

    @Test
    @DisplayName("Hierarchical relevance: District in headline yields MEDIUM relevance")
    void hierarchicalRelevanceDistrictInHeadlineYieldsMedium() {
        String locality = "Solang";
        String district = "Kullu";
        String stateUt = "Himachal Pradesh";
        String title = "Cloudburst reported in upper Kullu valley";
        String desc = "Emergency rescue teams sent across Himachal Pradesh.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.MEDIUM);
    }

    @Test
    @DisplayName("Hierarchical relevance: Only State/UT matched yields LOW relevance")
    void hierarchicalRelevanceStateMatchedYieldsLow() {
        String locality = "Solang";
        String district = "Kullu";
        String stateUt = "Himachal Pradesh";
        String title = "Himachal Pradesh assembly passes new tourism safety guidelines";
        String desc = "The state government announced initiatives for northern districts.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.LOW);
    }

    @Test
    @DisplayName("Hierarchical relevance: None matched yields UNKNOWN relevance")
    void hierarchicalRelevanceNoneMatchedYieldsUnknown() {
        String locality = "Solang";
        String district = "Kullu";
        String stateUt = "Himachal Pradesh";
        String title = "Heavy monsoon rain inundates coastal Mumbai";
        String desc = "Maharashtra disaster response teams deployed.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
    }

    @Test
    @DisplayName("Regression 1: Bhavani River in Palakkad, Kerala must NOT match Bhavani, Erode, Tamil Nadu as LOCALITY")
    void regression_bhavaniRiverKerala_doesNotMatchBhavaniTamilNadu() {
        String locality = "Bhavani";
        String district = "Erode";
        String stateUt = "Tamil Nadu";
        String title = "Body parts in Palakkad river: Bhavani River murder investigation";
        String desc = "Police probe gruesome crime near Attappadi, Palakkad, Kerala.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
        assertThat(filter.isAcceptableForLevel("LOCALITY", tier)).isFalse();
        assertThat(filter.isAcceptableForLevel("DISTRICT", tier)).isFalse();
    }

    @Test
    @DisplayName("Regression 2: Bhavani, Erode, Tamil Nadu article matches as LOCALITY / HIGH")
    void regression_bhavaniErodeTamilNadu_matchesAsLocalityHigh() {
        String locality = "Bhavani";
        String district = "Erode";
        String stateUt = "Tamil Nadu";
        String title = "Bhavani town safety inspection completed ahead of festival";
        String desc = "Officials in Erode district, Tamil Nadu reviewed crowd control measures.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.HIGH);
        assertThat(filter.isAcceptableForLevel("LOCALITY", tier)).isTrue();
    }

    @Test
    @DisplayName("Regression 3: Erode, Tamil Nadu article matches as DISTRICT / MEDIUM")
    void regression_erodeTamilNadu_matchesAsDistrictMedium() {
        String locality = "Bhavani";
        String district = "Erode";
        String stateUt = "Tamil Nadu";
        String title = "Heavy rain causes localized waterlogging in Erode";
        String desc = "Emergency response teams deployed across Tamil Nadu district.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.MEDIUM);
        assertThat(filter.isAcceptableForLevel("LOCALITY", tier)).isFalse();
        assertThat(filter.isAcceptableForLevel("DISTRICT", tier)).isTrue();
    }

    @Test
    @DisplayName("Regression 4: Palakkad, Kerala article yields UNKNOWN for Tamil Nadu tourist")
    void regression_palakkadKerala_yieldsUnknownForTamilNaduTourist() {
        String locality = "Bhavani";
        String district = "Erode";
        String stateUt = "Tamil Nadu";
        String title = "Landslide reported near silent valley road";
        String desc = "Transport halted across Palakkad, Kerala.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
        assertThat(filter.isAcceptableForLevel("LOCALITY", tier)).isFalse();
        assertThat(filter.isAcceptableForLevel("DISTRICT", tier)).isFalse();
    }

    @Test
    @DisplayName("Regression 5: Generic India-only story yields UNKNOWN for locality/district")
    void regression_genericIndiaStory_yieldsUnknownForLocalityAndDistrict() {
        String locality = "Bhavani";
        String district = "Erode";
        String stateUt = "Tamil Nadu";
        String title = "India introduces national transport safety advisory";
        String desc = "Central authorities release annual guidelines.";

        RelevanceTier tier = filter.calculateHierarchicalRelevance(locality, district, stateUt, title, desc);
        assertThat(tier).isEqualTo(RelevanceTier.UNKNOWN);
        assertThat(filter.isAcceptableForLevel("LOCALITY", tier)).isFalse();
        assertThat(filter.isAcceptableForLevel("DISTRICT", tier)).isFalse();
        assertThat(filter.isAcceptableForLevel("NATIONAL", tier)).isTrue();
    }
}
