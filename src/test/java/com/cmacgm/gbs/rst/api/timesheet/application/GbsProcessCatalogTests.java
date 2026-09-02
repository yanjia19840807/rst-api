package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetProcessProperties;
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
    void parseRejectsMissingApplicabilityHeader() {
        String csv = "ID,Process\n320,AP\n";
        assertThatThrownBy(() -> GbsProcessCatalog.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RST Applicability");
    }

    @Test
    void loadClasspathMockIncludesKnownRstProcess() {
        TimesheetProcessProperties properties = new TimesheetProcessProperties();
        GbsProcessCatalog catalog = new GbsProcessCatalogSource(properties, new DefaultResourceLoader()).load();

        assertThat(catalog.applies("497")).isTrue();
        assertThat(catalog.rstYesPl3Codes()).isNotEmpty();
    }
}
