package com.cmacgm.gbs.rst.api.supervisor.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupervisorApiIntegrationTests {

    private static final UUID SUPERVISOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTIVE_RUN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000010");
    private static final String SUPERVISOR_POSITION_ID = "POS-SUP-001";
    private static final String PL3_CODE = "BANK_REC";
    private static final Instant NOW = Instant.parse("2026-08-05T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedSupervisorAndActiveTimesheet() {
        jdbcTemplate.update("delete from tms_pause_interval");
        jdbcTemplate.update("delete from tms_session");
        jdbcTemplate.update("delete from workflow_action");
        jdbcTemplate.update("delete from workflow_step_assignment");
        jdbcTemplate.update("delete from workflow_instance");
        jdbcTemplate.update("delete from submission_scope");
        jdbcTemplate.update("delete from submission");
        jdbcTemplate.update("delete from official_package_section");
        jdbcTemplate.update("delete from official_package");
        jdbcTemplate.update("delete from validation_result");
        jdbcTemplate.update("delete from slot_simulation_result");
        jdbcTemplate.update("delete from daily_simulation_result");
        jdbcTemplate.update("delete from monthly_sizing_result");
        jdbcTemplate.update("delete from simulation_run");
        jdbcTemplate.update("delete from forecast_point");
        jdbcTemplate.update("delete from forecast_run");
        jdbcTemplate.update("delete from scenario_assumption");
        jdbcTemplate.update("update rst_exercise set official_scenario_id = null");
        jdbcTemplate.update("delete from scenario");
        jdbcTemplate.update("delete from cycle_time_baseline_file");
        jdbcTemplate.update("delete from cycle_time_baseline_sample");
        jdbcTemplate.update("delete from cycle_time_baseline");
        jdbcTemplate.update("delete from exercise_tms_session");
        jdbcTemplate.update("delete from exercise_volume_slot_input");
        jdbcTemplate.update("delete from exercise_volume_daily_input");
        jdbcTemplate.update("delete from exercise_volume_monthly_input");
        jdbcTemplate.update("delete from data_import_batch");
        jdbcTemplate.update("delete from file_artifact");
        jdbcTemplate.update("delete from exercise_holiday");
        jdbcTemplate.update("delete from exercise_calendar");
        jdbcTemplate.update("delete from exercise_production_support_item_scope");
        jdbcTemplate.update("delete from exercise_production_support_item");
        jdbcTemplate.update("delete from exercise_shift");
        jdbcTemplate.update("delete from exercise_team_setup");
        jdbcTemplate.update("delete from exercise_shared_kpi_line");
        jdbcTemplate.update("delete from exercise_subtask");
        jdbcTemplate.update("delete from exercise_toolkit_snapshot");
        jdbcTemplate.update("delete from rst_exercise");
        jdbcTemplate.update("delete from toolkit_shared_kpi_selection");
        jdbcTemplate.update("delete from toolkit_subtask");
        jdbcTemplate.update("delete from toolkit");
        jdbcTemplate.update("delete from timesheet_snapshot_row");
        jdbcTemplate.update("delete from timesheet_sync_run");
        jdbcTemplate.update("delete from app_user");

        jdbcTemplate.update(
                """
                insert into app_user
                    (id, ccgid, display_name, email, active, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                SUPERVISOR_ID,
                "SUPERVISOR001",
                "Test Supervisor",
                "supervisor@example.com",
                true,
                NOW,
                NOW);
        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, sync_date, status, row_count, started_at, completed_at)
                values (?, date '2026-08-05', 'ACTIVE', 3, ?, ?)
                """,
                ACTIVE_RUN_ID,
                NOW,
                NOW);
        insertTimesheetRow(
                "50000000-0000-0000-0000-000000000010",
                "AGENT010",
                "POS-AGENT-010",
                "Kuala Lumpur",
                "Carrier A",
                "Australia",
                "1.250000");
        insertTimesheetRow(
                "50000000-0000-0000-0000-000000000011",
                "AGENT011",
                "POS-AGENT-011",
                "Kuala Lumpur",
                "Carrier A",
                "Australia",
                "0.750000");
        insertTimesheetRow(
                "50000000-0000-0000-0000-000000000012",
                "AGENT012",
                "POS-AGENT-012",
                "Singapore",
                "Carrier B",
                "Germany",
                "3.000000");
    }

    @Test
    void exposesSupervisorToolkitHierarchyAndAggregatedSharedKpiCandidates() throws Exception {
        mockMvc.perform(get("/api/v1/timesheet/toolkit-hierarchy")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].supervisorPositionId").value(SUPERVISOR_POSITION_ID))
                .andExpect(jsonPath("$[0].pl3Code").value(PL3_CODE));

        mockMvc.perform(get("/api/v1/timesheet/shared-kpi-candidates")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .queryParam("pl3Code", PL3_CODE)
                        .queryParam("supervisorPositionId", SUPERVISOR_POSITION_ID)
                        .queryParam("customerCountry", "Australia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncDate").value("2026-08-05"))
                .andExpect(jsonPath("$.customerCountries[0]").value("Australia"))
                .andExpect(jsonPath("$.customerCountries[1]").value("Germany"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].carrier").value("Carrier A"))
                .andExpect(jsonPath("$.items[0].site").value("Kuala Lumpur"))
                .andExpect(jsonPath("$.items[0].customerCountry").value("Australia"))
                .andExpect(jsonPath("$.items[0].deliveryHc").value(2.0))
                .andExpect(jsonPath("$.items[0].valid").value(true));
    }

    @Test
    void createsToolkitWithChildrenAndRejectsDuplicateBusinessIdentity() throws Exception {
        String created = createToolkit("Bank Reconciliation Toolkit");
        String toolkitId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/v1/supervisor/toolkits/{id}", toolkitId)
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bank Reconciliation Toolkit"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.subtasks.length()").value(1))
                .andExpect(jsonPath("$.subtasks[0].name").value("Manual match"))
                .andExpect(jsonPath("$.sharedKpiSelections.length()").value(1))
                .andExpect(jsonPath("$.sharedKpiSelections[0].carrier").value("Carrier A"));

        mockMvc.perform(post("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createToolkitRequest("Duplicate Toolkit", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/toolkit-hierarchy-exists"));
    }

    @Test
    void atomicallyUpdatesToolkitChildrenAndRejectsStaleVersion() throws Exception {
        String created = createToolkit("Original Toolkit");
        String toolkitId = JsonPath.read(created, "$.id");
        String oldSubtaskId = JsonPath.read(created, "$.subtasks[0].id");
        Number oldVersion = JsonPath.read(created, "$.version");

        String updateRequest = """
                {
                  "name": "Updated Toolkit",
                  "description": "Updated atomically",
                  "combineSubtasksTime": true,
                  "version": %d,
                  "subtasks": [
                    {
                      "name": "Automated reconciliation",
                      "description": "New active subtask",
                      "displayOrder": 2
                    }
                  ],
                  "sharedKpiSelections": [
                    {
                      "carrier": "Carrier B",
                      "site": "Singapore",
                      "customerCountry": "Germany"
                    }
                  ]
                }
                """.formatted(oldVersion.longValue());

        mockMvc.perform(put("/api/v1/supervisor/toolkits/{id}", toolkitId)
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Toolkit"))
                .andExpect(jsonPath("$.combineSubtasksTime").value(true))
                .andExpect(jsonPath("$.version").value(greaterThan(oldVersion.intValue())))
                .andExpect(jsonPath("$.sharedKpiSelections.length()").value(1))
                .andExpect(jsonPath("$.sharedKpiSelections[0].carrier").value("Carrier B"))
                .andExpect(jsonPath("$.sharedKpiSelections[0].site").value("Singapore"))
                .andExpect(jsonPath("$.sharedKpiSelections[0].customerCountry").value("Germany"));

        Integer deletedSubtasks = jdbcTemplate.queryForObject(
                "select count(*) from toolkit_subtask where id = ? and deleted_at is not null",
                Integer.class,
                UUID.fromString(oldSubtaskId));
        Integer activeSubtasks = jdbcTemplate.queryForObject(
                "select count(*) from toolkit_subtask where toolkit_id = ? and deleted_at is null",
                Integer.class,
                UUID.fromString(toolkitId));
        Integer activeKpis = jdbcTemplate.queryForObject(
                """
                select count(*) from toolkit_shared_kpi_selection
                where toolkit_id = ? and deleted_at is null
                  and carrier = 'Carrier B' and site = 'Singapore'
                  and customer_country = 'Germany'
                """,
                Integer.class,
                UUID.fromString(toolkitId));
        org.junit.jupiter.api.Assertions.assertEquals(1, deletedSubtasks);
        org.junit.jupiter.api.Assertions.assertEquals(1, activeSubtasks);
        org.junit.jupiter.api.Assertions.assertEquals(1, activeKpis);

        mockMvc.perform(put("/api/v1/supervisor/toolkits/{id}", toolkitId)
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.replace("Updated Toolkit", "Stale overwrite")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/optimistic-lock-conflict"));

        mockMvc.perform(get("/api/v1/supervisor/toolkits/{id}", toolkitId)
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Toolkit"))
                .andExpect(jsonPath("$.subtasks.length()").value(2))
                .andExpect(jsonPath("$.subtasks[0].deletedAt").exists())
                .andExpect(jsonPath("$.subtasks[1].name").value("Automated reconciliation"))
                .andExpect(jsonPath("$.subtasks[1].deletedAt").doesNotExist());
    }

    @Test
    void freezesToolkitSubtasksAndHeadcountWhenExerciseIsCreated() throws Exception {
        String createdToolkit = createToolkit("Frozen Toolkit Name");
        String toolkitId = JsonPath.read(createdToolkit, "$.id");
        String exerciseRequest = exerciseRequest(toolkitId);

        String createdExercise = mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exerciseRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshot.toolkit.name").value("Frozen Toolkit Name"))
                .andExpect(jsonPath("$.snapshot.subtasks[0].name").value("Manual match"))
                .andExpect(jsonPath("$.snapshot.sharedKpis[0].deliveryHc").value(2.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String exerciseId = JsonPath.read(createdExercise, "$.id");

        jdbcTemplate.update(
                "update toolkit set name = ?, version = version + 1 where id = ?",
                "Live Toolkit Renamed",
                UUID.fromString(toolkitId));
        jdbcTemplate.update(
                "update toolkit_subtask set name = ? where toolkit_id = ? and deleted_at is null",
                "Live Subtask Renamed",
                UUID.fromString(toolkitId));
        jdbcTemplate.update(
                """
                update timesheet_snapshot_row set hc = 99
                where sync_run_id = ? and carrier = 'Carrier A'
                  and site = 'Kuala Lumpur' and customer_country = 'Australia'
                """,
                ACTIVE_RUN_ID);

        mockMvc.perform(get("/api/v1/supervisor/exercises/{id}", exerciseId)
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.toolkit.name").value("Frozen Toolkit Name"))
                .andExpect(jsonPath("$.snapshot.subtasks[0].name").value("Manual match"))
                .andExpect(jsonPath("$.snapshot.sharedKpis[0].deliveryHc").value(2.0))
                .andExpect(jsonPath("$.snapshot.timesheetSyncDate").value("2026-08-05"));
    }

    @Test
    void rejectsToolkitAndExerciseWithoutSharedKpiSelection() throws Exception {
        mockMvc.perform(post("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createToolkitRequest("Missing KPI Toolkit", false)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/shared-kpi-selection-required"));

        UUID toolkitWithoutKpi = UUID.fromString("10000000-0000-0000-0000-000000000099");
        UUID subtaskId = UUID.fromString("20000000-0000-0000-0000-000000000099");
        jdbcTemplate.update(
                """
                insert into toolkit
                    (id, name, supervisor_position_id, center, domain, pl1, pl2,
                     pl3_name, primary_pl3_code, combine_subtasks_time,
                     owner_user_id, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                toolkitWithoutKpi,
                "Legacy Toolkit Without KPI",
                SUPERVISOR_POSITION_ID,
                "Kuala Lumpur",
                "Finance",
                "Accounting",
                "Record to Report",
                "Bank Reconciliation",
                PL3_CODE,
                false,
                SUPERVISOR_ID,
                NOW,
                NOW,
                0);
        jdbcTemplate.update(
                """
                insert into toolkit_subtask
                    (id, toolkit_id, name, display_order, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                subtaskId,
                toolkitWithoutKpi,
                "Legacy subtask",
                1,
                NOW,
                NOW,
                0);

        mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exerciseRequest(toolkitWithoutKpi.toString())))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/shared-kpi-selection-required"));
    }

    private String createToolkit(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createToolkitRequest(name, true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String createToolkitRequest(String name, boolean withKpi) {
        String sharedKpis = withKpi
                ? """
                  "sharedKpiSelections": [
                    {
                      "carrier": "Carrier A",
                      "site": "Kuala Lumpur",
                      "customerCountry": "Australia"
                    }
                  ]
                  """
                : "\"sharedKpiSelections\": []";
        return """
                {
                  "name": "%s",
                  "description": "Integration test toolkit",
                  "supervisorPositionId": "%s",
                  "center": "Kuala Lumpur",
                  "domain": "Finance",
                  "pl1": "Accounting",
                  "pl2": "Record to Report",
                  "pl3Code": "%s",
                  "pl3Name": "Bank Reconciliation",
                  "combineSubtasksTime": false,
                  "subtasks": [
                    {
                      "name": "Manual match",
                      "description": "Match bank transactions",
                      "displayOrder": 1
                    }
                  ],
                  %s
                }
                """.formatted(name, SUPERVISOR_POSITION_ID, PL3_CODE, sharedKpis);
    }

    private String exerciseRequest(String toolkitId) {
        return """
                {
                  "toolkitId": "%s",
                  "sizingMonth": "2026-09",
                  "slotStartDate": "2026-09-07",
                  "slotWeeks": 4,
                  "tmsFrom": "2026-08-01",
                  "tmsTo": "2026-08-31"
                }
                """.formatted(toolkitId);
    }

    private void insertTimesheetRow(
            String id,
            String employeeCcgid,
            String employeePositionId,
            String site,
            String carrier,
            String country,
            String hc) {
        jdbcTemplate.update(
                """
                insert into timesheet_snapshot_row
                    (id, sync_run_id, emp_ccgid, emp_name, emp_position_id,
                     supervisor_ccgid, supervisor_name, supervisor_position_id,
                     center, site, domain, pl1, pl2, pl3_code, pl3_name,
                     carrier, customer_country, hc)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString(id),
                ACTIVE_RUN_ID,
                employeeCcgid,
                "Test Agent " + employeeCcgid,
                employeePositionId,
                "SUPERVISOR001",
                "Test Supervisor",
                SUPERVISOR_POSITION_ID,
                "Kuala Lumpur",
                site,
                "Finance",
                "Accounting",
                "Record to Report",
                PL3_CODE,
                "Bank Reconciliation",
                carrier,
                country,
                new java.math.BigDecimal(hc));
    }
}
