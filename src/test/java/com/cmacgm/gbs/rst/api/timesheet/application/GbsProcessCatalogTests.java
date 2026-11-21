package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.process.ProcessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class GbsProcessCatalogTests {

    @Test
    void parseKeepsYesIdsAndIgnoresNoAndBlank() {
        String csv =
                """
                Domain,Process,RST Applicability,ID
                Finance,AP Helpdesk,Yes,320
                Finance,AP Payment,No,321
                Finance,Blank,,322
                Customer Care,Booking Amendments,YES,367
                Shipping,Project EIT,yes,497.0
                """;

        GbsProcessCatalog catalog = GbsProcessCatalog.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(catalog.rstYesPl3Codes()).containsExactlyInAnyOrder("320", "367", "497");
        assertThat(catalog.applies("320")).isTrue();
        assertThat(catalog.applies("367")).isTrue();
        assertThat(catalog.applies("497")).isTrue();
        assertThat(catalog.applies("321")).isFalse();
        assertThat(catalog.applies("322")).isFalse();
        assertThat(catalog.applies("missing")).isFalse();
        assertThat(catalog.applies("")).isFalse();
    }

    @Test
    void level3NameSuppliesCodeAndKeepsSkpiDomainSpelling() {
        String csv =
                """
                ID,RST Applicability,Level 1,Level 2,Level 3,Domain
                4507,Yes,PL1,PL2,Supplier Qualification - Loi Sapin 2,Procurement
                768,Yes,,,Standard Bookings,Finance
                769,Yes,A,B,Standard Bookings,Finance
                321,No,A,B,Closed Process,Finance
                """;

        GbsProcessCatalog catalog = GbsProcessCatalog.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(catalog.level3ByName("standard bookings"))
                .isEmpty();
        assertThat(catalog.level3Ambiguous("Standard Bookings")).isTrue();
        assertThat(catalog.level3ByName("Supplier Qualification - Loi Sapin 2"))
                .get()
                .extracting(GbsProcessCatalog.Level3::pl3Code, GbsProcessCatalog.Level3::domain)
                .containsExactly("4507", "Procurement");
        assertThat(catalog.level3ByName("Closed Process")).isEmpty();
        assertThat(catalog.canonicalDomain("FINANCE")).isEqualTo("Finance");
        assertThat(catalog.level3ByName("Standard Bookings".toLowerCase())).isEmpty();
        GbsProcessCatalog.Level3 blankLevels = GbsProcessCatalog.parse(new ByteArrayInputStream(
                "ID,RST Applicability,Level 3,Domain,Level 1,Level 2\n768,Yes,Standard Bookings,Finance,,\n"
                        .getBytes(StandardCharsets.UTF_8)))
                .level3ByName("Standard Bookings")
                .orElseThrow();
        assertThat(blankLevels.pl1()).isEmpty();
        assertThat(blankLevels.pl2()).isEmpty();
    }

    @Test
    void parseRejectsMissingApplicabilityHeader() {
        String csv = "ID,Process\n320,AP\n";
        assertThatThrownBy(() -> GbsProcessCatalog.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RST Applicability");
    }

    @Test
    void fromSharePointFieldsAcceptsInternalNames() {
        GbsProcessCatalog catalog = GbsProcessCatalog.fromSharePointFields(List.of(
                Map.of("id", 313, "RSTApplicability", "Yes"),
                Map.of("ID", "321", "RST Applicability", "No"),
                Map.of("id", "497.0", "RST_x0020_Applicability", "YES")));

        assertThat(catalog.rstYesPl3Codes()).containsExactlyInAnyOrder("313", "497");
        assertThat(catalog.applies("313")).isTrue();
        assertThat(catalog.applies("321")).isFalse();
    }

    @Test
    void fromSharePointFieldsReadsEncodedLevelNames() {
        GbsProcessCatalog catalog = GbsProcessCatalog.fromSharePointFields(List.of(Map.of(
                "id", "768",
                "RSTApplicability", "Yes",
                "Level_x0020_3", "Standard Bookings",
                "Level_x0020_1", "Ocean",
                "Level_x0020_2", "Booking",
                "Domain", "Finance")));

        GbsProcessCatalog.Level3 level3 = catalog.level3ByName("standard bookings").orElseThrow();
        assertThat(level3.pl3Code()).isEqualTo("768");
        assertThat(level3.pl1()).isEqualTo("Ocean");
        assertThat(level3.pl2()).isEqualTo("Booking");
        assertThat(level3.domain()).isEqualTo("Finance");
    }

    @Test
    void fromSharePointFieldsRejectsMissingApplicability() {
        assertThatThrownBy(() -> GbsProcessCatalog.fromSharePointFields(List.of(Map.of("id", "313"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RST Applicability");
    }

    @Test
    void loadClasspathMockIncludesKnownRstProcess() {
        ProcessProperties properties = new ProcessProperties();
        GbsProcessCatalog catalog = new GbsProcessCatalogSource(
                properties, new DefaultResourceLoader(), null).load();

        assertThat(catalog.applies("497")).isTrue();
        assertThat(catalog.rstYesPl3Codes()).isNotEmpty();
    }

    @Test
    void remoteTrueWithoutGraphFailsClearly() {
        ProcessProperties properties = new ProcessProperties();
        properties.setRemote(true);

        assertThatThrownBy(() -> new GbsProcessCatalogSource(
                        properties, new DefaultResourceLoader(), null)
                .load())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Microsoft Graph");
    }
}
