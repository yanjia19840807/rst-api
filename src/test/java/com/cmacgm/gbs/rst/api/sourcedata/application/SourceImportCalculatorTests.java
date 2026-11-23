package com.cmacgm.gbs.rst.api.sourcedata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.application.MyHrWorkbookParser.PersonRow;
import com.cmacgm.gbs.rst.api.sourcedata.application.MyHrWorkbookParser.PositionRow;
import com.cmacgm.gbs.rst.api.sourcedata.application.SharedKpiCalculator.SiteRef;
import com.cmacgm.gbs.rst.api.sourcedata.application.SharedKpiWorkbookParser.KpiRow;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalog;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;

class SourceImportCalculatorTests {

    private static final UUID RUN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");
    private static final Set<String> MYHR = Set.of(
            RstCenters.GBS_INDIA, RstCenters.GBS_LEBANON, RstCenters.GBS_ESTONIA,
            RstCenters.GBS_PHILIPPINES, RstCenters.GBS_PORTUGAL);

    private final MyHrOrgCalculator myHr = new MyHrOrgCalculator();
    private final SharedKpiCalculator skpi = new SharedKpiCalculator();

    @Test
    void myHrKeepsLatestQualifiedPeopleAndTwoLevelEdges() {
        MyHrOrgCalculator.Result result = myHr.compute(RUN, List.of(
                person(2, "P1", "S1", "Terminated", "IND-India", "A1", "0", LocalDate.of(2026, 1, 1)),
                person(3, "P1", "S1", "Active", "IND-India", "A1", "0", LocalDate.of(2026, 3, 1)),
                person(4, "P2", "S2", "Paid Leave", "IND-India", "SUP", "2", LocalDate.of(2026, 3, 1)),
                person(5, "P3", "S3", "Active", "IND-India", "SR", "4", LocalDate.of(2026, 3, 1)),
                person(6, "PC", "SC", "Active", "CHN-China", "CHN", "0", LocalDate.of(2026, 3, 1)),
                person(7, "PX", null, "Active", "IND-India", "X", "0", LocalDate.of(2026, 3, 1)),
                person(8, "PE", "SE", "Active", "IND-India", "", "0", LocalDate.of(2026, 3, 1)),
                person(9, "P4", "S4", "Active", "IND-India", "A2", "0", LocalDate.of(2026, 3, 1))),
                List.of(
                        new PositionRow(2, "A1", "A", "SUP"),
                        new PositionRow(3, "SUP", "A", "SR"),
                        new PositionRow(4, "SR", "A", "CHN"),
                        new PositionRow(5, "CHN", "A", null),
                        new PositionRow(6, "A2", "A", "CHN")),
                MYHR,
                NOW);

        assertThat(result.activate()).isTrue();
        assertThat(result.people()).extracting(OrgPerson::getCcgid).containsExactly("S1", "S2", "S3", "SE", "S4");
        assertThat(result.seats()).extracting(OrgSeat::getCcgid, OrgSeat::getRoleType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("S1", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("S2", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("S3", "SR_MANAGER"),
                        org.assertj.core.groups.Tuple.tuple("S4", "AGENT"));
        assertThat(result.parents()).extracting(
                        edge -> edge.getPositionId() + ":" + edge.getRoleType() + ">" + edge.getParentPositionId())
                .containsExactlyInAnyOrder("A1:AGENT>SUP", "SUP:SUPERVISOR>SR");
        assertThat(result.issues()).extracting(SourceIssue::getCode)
                .contains("MISSING_FIELD", "MISSING_PARENT");
    }

    @Test
    void myHrKeepsVacantParentPositionAndEdge() {
        MyHrOrgCalculator.Result result = myHr.compute(RUN, List.of(
                person(2, "P1", "S1", "Active", "IND-India", "A1", "0", LocalDate.of(2026, 3, 1)),
                person(3, "P4", "S4", "Active", "IND-India", "A9", "0", LocalDate.of(2026, 3, 1)),
                person(4, "P5", "S5", "Active", "IND-India", "A5", "0", LocalDate.of(2026, 3, 1)),
                person(5, "PC", "SC", "Active", "CHN-China", "CHN", "0", LocalDate.of(2026, 3, 1)),
                person(6, "P6", "S6", "Active", "IND-India", "A3", "0", LocalDate.of(2026, 3, 1)),
                person(7, "P7", "S7", "Active", "IND-India", "TWO", "1", LocalDate.of(2026, 3, 1)),
                person(8, "P8", "S8", "Active", "IND-India", "TWO", "1", LocalDate.of(2026, 3, 1))),
                List.of(
                        new PositionRow(2, "A1", "A", "SUP"),
                        new PositionRow(3, "SUP", "A", "SR"),
                        new PositionRow(4, "SR", "A", null),
                        new PositionRow(5, "A9", "A", "CHN"),
                        new PositionRow(6, "CHN", "A", null),
                        new PositionRow(7, "A5", "A", "OLD"),
                        new PositionRow(8, "OLD", "I", null),
                        new PositionRow(9, "A3", "A", "TWO"),
                        new PositionRow(10, "TWO", "A", null)),
                MYHR,
                NOW);

        assertThat(result.people()).extracting(OrgPerson::getCcgid)
                .containsExactly("S1", "S4", "S5", "S6", "S7", "S8");
        assertThat(result.seats()).extracting(OrgSeat::getCcgid, OrgSeat::getPositionId, OrgSeat::getRoleType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("S1", "A1", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("S4", "A9", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("S5", "A5", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("S6", "A3", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("S7", "TWO", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("S8", "TWO", "SUPERVISOR"));
        assertThat(result.positions()).extracting(OrgPosition::getPositionId, OrgPosition::getRoleType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("A1", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("SUP", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("SR", "SR_MANAGER"),
                        org.assertj.core.groups.Tuple.tuple("A9", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("A5", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("A3", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("TWO", "SUPERVISOR"));
        assertThat(result.parents()).extracting(
                        edge -> edge.getPositionId() + ":" + edge.getRoleType() + ">" + edge.getParentPositionId())
                .containsExactlyInAnyOrder("A1:AGENT>SUP", "SUP:SUPERVISOR>SR");
        assertThat(result.issues()).extracting(SourceIssue::getMessage)
                .contains(
                        "Parent position SUP has no occupant in a MyHR + SKPI center.",
                        "Parent position SR has no occupant in a MyHR + SKPI center.",
                        "Parent position CHN has no occupant in a MyHR + SKPI center.",
                        "Parent position OLD has no occupant in a MyHR + SKPI center.",
                        "Parent position TWO has more than one occupant.");
    }

    @Test
    void skpiMapsSiteSumsHcAndSkipsTimesheetCenters() {
        GbsProcessCatalog catalog = GbsProcessCatalog.parse(new java.io.ByteArrayInputStream(
                """
                ID,RST Applicability,Level 1,Level 2,Level 3,Domain
                768,Yes,Ocean,Booking,Standard Bookings,Finance
                4507,Yes,Buy,Qualify,Supplier Qualification - Loi Sapin 2,Procurement
                100,No,Ocean,Booking,Closed Bookings,Finance
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Map<String, SiteRef> sites = Map.of(
                "CHENNAI-1", new SiteRef("INMAA", RstCenters.GBS_INDIA),
                "CHENNAI-3", new SiteRef("INMAA", RstCenters.GBS_INDIA),
                "CHONGQING", new SiteRef("CNCKG", RstCenters.GBS_CHINA),
                "BEIRUT", new SiteRef("LBBRT", RstCenters.GBS_LEBANON));

        SharedKpiCalculator.Result result = skpi.compute(RUN, List.of(
                kpi(2, "Chennai-1", "Standard Bookings", "CMA CGM", "China", "1.5", "FINANCE"),
                kpi(3, "chennai-3", "Standard Bookings", "cma cgm", "china", "2", "Finance"),
                kpi(4, "CHONGQING", "Standard Bookings", "CMA CGM", "China", "9", "Finance"),
                kpi(5, "Infosys", "Standard Bookings", "CMA CGM", "India", "4", "Finance"),
                kpi(6, "BEIRUT", "Standard Bookings", "CMA CGM", "Lebanon", "nope", "Finance"),
                kpi(7, "BEIRUT", "Closed Bookings", "CMA CGM", "Lebanon", "3", "Finance"),
                kpi(8, "BEIRUT", "Supplier Qualification - Loi Sapin 2", "ANL", "France", "1",
                        "Shipping Ops, Log & Interm")),
                sites,
                MYHR,
                catalog,
                NOW);

        assertThat(result.activate()).isTrue();
        assertThat(result.period()).isEqualTo("202609");
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines()).filteredOn(line -> "INMAA".equals(line.getSite()))
                .extracting(line -> line.getHc(), line -> line.getCarrier(), line -> line.getCustomerCountry())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        new BigDecimal("3.500000"), "CMA CGM", "CHINA"));
        assertThat(result.processes()).filteredOn(process -> "4507".equals(process.getPl3Code()))
                .extracting(process -> process.getDomain())
                .containsExactly("Shipping Ops, Log & Interm");
        assertThat(result.issues()).extracting(SourceIssue::getCode)
                .containsExactly("UNMAPPED_SITE", "INVALID_HC");
        assertThat(SharedKpiCalculator.periodError(List.of(
                kpi(2, "Chennai-1", "Standard Bookings", "CMA CGM", "China", "1.5", "FINANCE"))))
                .isNull();
    }

    @Test
    void skpiRejectsMixedOrInvalidPeriods() {
        assertThat(SharedKpiCalculator.periodError(List.of(
                kpiPeriod(2, "202608"),
                kpiPeriod(3, "202609"))))
                .isEqualTo("Period must be the same YYYYMM on every row. Found 202608, 202609.");
        assertThat(SharedKpiCalculator.periodError(List.of(
                kpiPeriod(2, "202609"),
                kpiPeriod(4, " "))))
                .isEqualTo("Period must be the same YYYYMM on every row. Found 202609, blank.");
        assertThat(SharedKpiCalculator.periodError(List.of(kpiPeriod(2, "2026-09"))))
                .isEqualTo("Period must be the same YYYYMM on every row. Found 2026-09.");
        assertThat(SharedKpiCalculator.periodError(List.of())).isNull();
    }

    @Test
    void skpiPeriodDecidesWhichUploadIsCurrent() {
        assertThat(SharedKpiCalculator.replacesCurrent("202609", "202610")).isTrue();
        assertThat(SharedKpiCalculator.replacesCurrent("202609", "202609")).isTrue();
        assertThat(SharedKpiCalculator.replacesCurrent("202609", "202608")).isFalse();
        assertThat(SharedKpiCalculator.replacesCurrent(null, "202609")).isTrue();
        assertThat(SharedKpiCalculator.replacesCurrent("202609", null)).isFalse();
    }

    private static PersonRow person(
            int row,
            String personId,
            String username,
            String status,
            String country,
            String position,
            String directReports,
            LocalDate eventDate) {
        return new PersonRow(
                row, personId, username, status, "Last", "First", username + "@example.com",
                directReports, country, "Agent", position, eventDate);
    }

    private static KpiRow kpi(
            int row, String site, String process, String carrier, String country, String hc, String domain) {
        return new KpiRow(row, "202609", carrier, site, domain, process, country, hc);
    }

    private static KpiRow kpiPeriod(int row, String period) {
        return new KpiRow(row, period, "CMA CGM", "CHENNAI-1", "Finance", "Standard Bookings", "China", "1");
    }
}
