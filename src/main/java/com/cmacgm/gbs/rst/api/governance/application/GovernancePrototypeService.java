package com.cmacgm.gbs.rst.api.governance.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Static prototype payloads for governance report screens (no DB yet).
 */
@Service
public class GovernancePrototypeService {

    /**
     * @return dashboard mock payload
     */
    public Map<String, Object> dashboard() {
        return map(
                "metrics", List.of(
                        metric("RST completion", "62%", "3,384 / 5,474 applicable PL3 completed this quarter", "good"),
                        metric("Never done", "395", "Applicable PL3 with no RST record", "bad"),
                        metric("Stuck in validation", "312", "Completed but not validated", "warn"),
                        metric("Capacity Creation YTD", "+128.4", "HC created through validated RST", "good"),
                        metric("YTD % vs Actual Delivery HC", "4.2%", "Capacity Creation YTD / actual delivery HC", "good")),
                "centers", List.of(
                        center("GBS China", 546, 354, "65%", 82, 35, 51, 24, true),
                        center("GBS Costa Rica", 11, 5, "45%", 2, 1, 2, 1, false),
                        center("GBS Estonia", 71, 31, "44%", 10, 9, 14, 7, false),
                        center("GBS India", 4030, 2610, "65%", 710, 245, 340, 125, true),
                        center("GBS Lebanon", 652, 287, "44%", 112, 93, 108, 52, false),
                        center("GBS Philippines", 105, 62, "59%", 16, 8, 13, 6, true),
                        center("GBS Portugal", 59, 35, "59%", 8, 4, 8, 4, true)),
                "domainsByCenter", map(
                        "GBS China", List.of(
                                domain("Customer Care", 168, 97, "58%", 18),
                                domain("Finance", 242, 176, "73%", 9),
                                domain("Procurement", 136, 81, "60%", 8)),
                        "GBS India", List.of(
                                domain("Customer Care", 1820, 1104, "61%", 120),
                                domain("Finance", 1560, 1120, "72%", 80),
                                domain("Procurement", 650, 386, "59%", 45)),
                        "GBS Philippines", List.of(
                                domain("Customer Care", 62, 38, "61%", 5),
                                domain("Finance", 28, 16, "57%", 2),
                                domain("Procurement", 15, 8, "53%", 1))));
    }

    /**
     * @return repository mock rows
     */
    public List<Map<String, Object>> repository() {
        return List.of(
                repoRow("RST-Q3-CHN-001", "CMA CGM", "MUMBAI-2", "GBS India", "FINANCE",
                        "Record to report", "Bank Reconciliation", "BANK RECONCILIATION",
                        "Bank Rec Manual Check", "AUSTRALIA", "16.55", "9.30", "1.21",
                        "+6.04", "+36.5%", "+8.4%", "2026-07-22"),
                repoRow("RST-Q3-CHN-001", "CMA CGM", "CHENNAI-1", "GBS India", "FINANCE",
                        "Record to report", "Bank Reconciliation", "BANK RECONCILIATION",
                        "Bank Rec Manual Check", "AUSTRALIA", "2.27", "1.28", "0.16",
                        "+0.83", "+36.6%", "+6.1%", "2026-07-22"),
                repoRow("RST-Q3-CHN-001", "ANL", "MUMBAI-2", "GBS India", "FINANCE",
                        "Record to report", "Bank Reconciliation", "BANK RECONCILIATION",
                        "Bank Rec Auto Exception", "AUSTRALIA", "1.59", "0.89", "0.11",
                        "+0.59", "+37.1%", "+5.8%", "2026-07-22"),
                repoRow("RST-Q3-IND-018", "CMA CGM", "CHONGQING", "China", "FINANCE",
                        "Procure to pay", "Accounts Payable Classification", "AP CLASSIFICATION",
                        "AP Classification Desk", "CHINA", "14.83", "13.00", "6.20",
                        "-4.37", "-29.5%", "+12.3%", "2026-06-18"),
                repoRow("RST-Q3-PHL-006", "CMA CGM", "BEIRUT", "Lebanon", "CUSTOMER CARE",
                        "Documentation", "Service Delivery Export", "EXPORT DOC - STANDARD SCOPE",
                        "Booking Amendment Desk", "TURKIYE", "19.12", "12.00", "7.50",
                        "-0.38", "-2.0%", "+18.7%", "2026-07-10"));
    }

    /**
     * @return support repository mock payload
     */
    public Map<String, Object> supportRepository() {
        return map(
                "totalSupportFte", "642.8",
                "topCategory", "Quality Control",
                "topCategoryFte", "148.6 FTE",
                "categorySummaries", List.of(
                        category("Communication", "96.4", "15.0%", "Client query follow-up"),
                        category("Operational Support", "88.7", "13.8%", "Queue coordination"),
                        category("Quality Control", "148.6", "23.1%", "Case audit"),
                        category("Reporting", "54.3", "8.4%", "SKPI pack"),
                        category("Small Process", "42.5", "6.6%", "Ad hoc low-volume tasks"),
                        category("Training", "63.8", "9.9%", "New joiner coaching"),
                        category("Tool Support", "76.2", "11.9%", "Tool issue follow-up"),
                        category("Project Support", "38.9", "6.1%", "Migration support"),
                        category("Performance Monitoring", "33.4", "5.2%", "Daily KPI review")),
                "rows", List.of(
                        supportRow("RST-Q3-CHN-001", "GBS China", "Finance", "BANK RECONCILIATION",
                                "Bank Rec Manual Check", "Quality Control", "Case audit", "Weekly",
                                "80", "Cases", "0.51", "Rotational QA sampling", "2026-07-22"),
                        supportRow("RST-Q3-CHN-001", "GBS China", "Finance", "BANK RECONCILIATION",
                                "Bank Rec Manual Check", "Reporting", "SKPI pack", "Monthly",
                                "4", "Packs", "0.09", "Leadership review prep", "2026-07-22"),
                        supportRow("RST-Q3-CHN-004", "GBS China", "Customer Care", "BOOKING AMENDMENTS",
                                "Booking Amendment Desk", "Communication", "Client query support", "Daily",
                                "12", "Queries", "0.46", "Business follow-up outside production", "2026-07-15")));
    }

    /**
     * @return benchmarking mock payload
     */
    public Map<String, Object> benchmarking() {
        return map(
                "selectedPl3", "BANK RECONCILIATION",
                "bestDailyCapacity", "244",
                "bestDailyCapacityHint", "GBS India",
                "medianCycleTime", "142s",
                "productionSupportRatio", "13%",
                "rows", List.of(
                        bench("GBS China", "China", "FINANCE", "BANK RECONCILIATION", "142s", "183", "13.0%", "-1.02"),
                        bench("GBS China", "Singapore", "FINANCE", "BANK RECONCILIATION", "140s", "185", "13.2%", "-0.43"),
                        bench("GBS India", "India", "FINANCE", "BANK RECONCILIATION", "118s", "220", "7.9%", "+0.10"),
                        bench("GBS Portugal", "Portugal", "FINANCE", "BANK RECONCILIATION", "132s", "196", "10.4%", "+0.20")));
    }

    /**
     * @return validation workflow mock rows
     */
    public List<Map<String, Object>> validationWorkflow() {
        return List.of(
                stuck("RST-Q3-CHN-001", "GBS China", "FINANCE", "BANK RECONCILIATION",
                        "Bank Rec Manual Check", "Local Transformation Head", "xxx", 9,
                        "-1.70", "-17.0%", "+7.4%", "2026-07-20"),
                stuck("RST-Q3-PHL-006", "GBS Philippines", "CUSTOMER CARE", "BOOKING AMENDMENTS",
                        "Booking Amendment Desk", "Local Transformation Head", "xxx", 17,
                        "-8.50", "-35.4%", "+18.0%", "2026-07-05"),
                stuck("RST-Q3-PRT-011", "GBS Portugal", "CUSTOMER CARE", "BLANK FORMS",
                        "Blank Forms Desk", "Center Delivery Head", "xxx", 6,
                        "+0.20", "+1.1%", "+3.1%", "2026-07-25"));
    }

    private static Map<String, Object> metric(String label, String value, String hint, String tone) {
        return map("label", label, "value", value, "hint", hint, "tone", tone);
    }

    private static Map<String, Object> center(
            String center,
            int applicablePl3,
            int completedThisQuarter,
            String completionPct,
            int completed3To6Months,
            int neverDone,
            int completed6To12Months,
            int completedOver1Year,
            boolean onTrack) {
        return map(
                "center", center,
                "applicablePl3", applicablePl3,
                "completedThisQuarter", completedThisQuarter,
                "completionPct", completionPct,
                "completed3To6Months", completed3To6Months,
                "neverDone", neverDone,
                "completed6To12Months", completed6To12Months,
                "completedOver1Year", completedOver1Year,
                "onTrack", onTrack);
    }

    private static Map<String, Object> domain(
            String domain, int applicablePl3, int completed, String pct, int neverDone) {
        return map(
                "domain", domain,
                "applicablePl3", applicablePl3,
                "completed", completed,
                "pct", pct,
                "neverDone", neverDone);
    }

    private static Map<String, Object> repoRow(
            String exerciseId,
            String carrier,
            String site,
            String country,
            String domain,
            String pl1,
            String pl2,
            String pl3,
            String toolkit,
            String kpi,
            String deliveryHc,
            String rsHc,
            String support,
            String capacityCreation,
            String capacityPct,
            String volumeYoY,
            String submittedDate) {
        return map(
                "exerciseId", exerciseId,
                "carrier", carrier,
                "site", site,
                "country", country,
                "domain", domain,
                "pl1", pl1,
                "pl2", pl2,
                "pl3", pl3,
                "toolkit", toolkit,
                "kpi", kpi,
                "deliveryHc", deliveryHc,
                "rsHc", rsHc,
                "support", support,
                "capacityCreation", capacityCreation,
                "capacityPct", capacityPct,
                "volumeYoY", volumeYoY,
                "submittedDate", submittedDate);
    }

    private static Map<String, Object> category(
            String category, String supportFte, String pctOfSupport, String topActivity) {
        return map(
                "category", category,
                "supportFte", supportFte,
                "pctOfSupport", pctOfSupport,
                "topActivity", topActivity);
    }

    private static Map<String, Object> supportRow(
            String exerciseNo,
            String gbsSite,
            String domain,
            String pl3,
            String toolkit,
            String standardCategory,
            String activity,
            String frequency,
            String volume,
            String uom,
            String fte,
            String comments,
            String submittedDate) {
        return map(
                "exerciseNo", exerciseNo,
                "gbsSite", gbsSite,
                "domain", domain,
                "pl3", pl3,
                "toolkit", toolkit,
                "standardCategory", standardCategory,
                "activity", activity,
                "frequency", frequency,
                "volume", volume,
                "uom", uom,
                "fte", fte,
                "comments", comments,
                "submittedDate", submittedDate);
    }

    private static Map<String, Object> bench(
            String gbs,
            String sharedKpiLine,
            String domain,
            String pl3,
            String cycleTime,
            String dailyCapacityPerAgent,
            String productionSupportRatio,
            String capacityCreation) {
        return map(
                "gbs", gbs,
                "sharedKpiLine", sharedKpiLine,
                "domain", domain,
                "pl3", pl3,
                "cycleTime", cycleTime,
                "dailyCapacityPerAgent", dailyCapacityPerAgent,
                "productionSupportRatio", productionSupportRatio,
                "capacityCreation", capacityCreation);
    }

    private static Map<String, Object> stuck(
            String exerciseNo,
            String gbs,
            String domain,
            String pl3,
            String toolkit,
            String currentStep,
            String currentOwner,
            int agingDays,
            String capacityCreation,
            String capacityPct,
            String volumeYoY,
            String submittedDate) {
        return map(
                "exerciseNo", exerciseNo,
                "gbs", gbs,
                "domain", domain,
                "pl3", pl3,
                "toolkit", toolkit,
                "currentStep", currentStep,
                "currentOwner", currentOwner,
                "agingDays", agingDays,
                "capacityCreation", capacityCreation,
                "capacityPct", capacityPct,
                "volumeYoY", volumeYoY,
                "submittedDate", submittedDate);
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }
}
