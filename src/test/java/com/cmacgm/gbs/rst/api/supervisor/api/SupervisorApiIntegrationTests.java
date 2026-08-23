package com.cmacgm.gbs.rst.api.supervisor.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    private static final String SUPERVISOR_CCGID = "SUPERVISOR001";
    private static final UUID DAILY_RUN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000010");
    private static final UUID MONTHLY_RUN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000011");
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
        jdbcTemplate.update("delete from validation_result");
        jdbcTemplate.update("delete from slot_simulation_result");
        jdbcTemplate.update("delete from daily_simulation_result");
        jdbcTemplate.update("delete from monthly_sizing_result");
        jdbcTemplate.update("delete from simulation_run");
        jdbcTemplate.update("delete from forecast_point");
        jdbcTemplate.update("delete from forecast_run");
        jdbcTemplate.update("update rst_exercise set official_scenario_id = null");
        jdbcTemplate.update("delete from scenario");
        jdbcTemplate.update("delete from cycle_time_baseline_file");
        jdbcTemplate.update("delete from cycle_time_baseline");
        jdbcTemplate.update("delete from exercise_tms_session");
        jdbcTemplate.update("delete from exercise_volume_slot_input");
        jdbcTemplate.update("delete from exercise_volume_daily_input");
        jdbcTemplate.update("delete from exercise_volume_monthly_input");
        jdbcTemplate.update("delete from data_import_batch");
        jdbcTemplate.update("delete from file_artifact");
        jdbcTemplate.update("delete from exercise_holiday");
        jdbcTemplate.update("delete from exercise_production_support_item");
        jdbcTemplate.update("delete from exercise_team_setup");
        jdbcTemplate.update("delete from exercise_shared_kpi_line");
        jdbcTemplate.update("delete from exercise_subtask");
        jdbcTemplate.update("delete from exercise_toolkit_snapshot");
        jdbcTemplate.update("delete from rst_exercise");
        jdbcTemplate.update("delete from toolkit_shared_kpi_selection");
        jdbcTemplate.update("delete from toolkit_subtask");
        jdbcTemplate.update("delete from toolkit");
        jdbcTemplate.update("delete from timesheet_sync_issue");
        jdbcTemplate.update("delete from timesheet_kpi");
        jdbcTemplate.update("delete from timesheet_assignment");
        jdbcTemplate.update("delete from timesheet_scope");
        jdbcTemplate.update("delete from timesheet_occupancy");
        jdbcTemplate.update("delete from timesheet_position");
        jdbcTemplate.update("delete from timesheet_person");
        jdbcTemplate.update("delete from timesheet_sync_run");

        insertSyncRun(DAILY_RUN_ID, "DAILY", 3);
        insertSyncRun(MONTHLY_RUN_ID, "MONTHLY", 2);
        insertPerson(DAILY_RUN_ID, SUPERVISOR_CCGID, "Test Supervisor");
        insertPerson(DAILY_RUN_ID, "AGENT010", "Test Agent AGENT010");
        insertPerson(DAILY_RUN_ID, "AGENT011", "Test Agent AGENT011");
        insertPerson(DAILY_RUN_ID, "AGENT012", "Test Agent AGENT012");
        insertPosition(DAILY_RUN_ID, SUPERVISOR_POSITION_ID, "SUPERVISOR", "POS-SRM-001");
        insertPosition(DAILY_RUN_ID, "POS-SRM-001", "SR_MANAGER", "POS-DH-001");
        insertPosition(DAILY_RUN_ID, "POS-DH-001", "DOMAIN_HEAD", null);
        insertOccupancy(DAILY_RUN_ID, SUPERVISOR_POSITION_ID, SUPERVISOR_CCGID);
        insertScope(DAILY_RUN_ID);
        insertAssignment(DAILY_RUN_ID, "AGENT010");
        insertAssignment(DAILY_RUN_ID, "AGENT011");
        insertAssignment(DAILY_RUN_ID, "AGENT012");
        insertKpi(MONTHLY_RUN_ID, "Carrier A", "Kuala Lumpur", "Australia", "2.000000");
        insertKpi(MONTHLY_RUN_ID, "Carrier B", "Singapore", "Germany", "3.000000");
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
                .andExpect(jsonPath("$.items[0].deliveryHc").value(2.0));
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
    void filtersSupervisorToolkitListByNameAndPl3() throws Exception {
        createToolkit("Bank Reconciliation Toolkit");

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Bank Reconciliation Toolkit"))
                .andExpect(jsonPath("$.pl3Names[0]").value("Bank Reconciliation"));

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .queryParam("name", "reconcil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .queryParam("name", "invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.pl3Names[0]").value("Bank Reconciliation"));

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .queryParam("pl3Name", "Bank Reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .queryParam("pl3Name", "Invoice Processing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void allowsReusingNameAndHierarchyAfterSoftDelete() throws Exception {
        String created = createToolkit("Reusable Toolkit");
        String toolkitId = JsonPath.read(created, "$.id");

        mockMvc.perform(delete("/api/v1/supervisor/toolkits/{id}", toolkitId)
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/v1/supervisor/toolkits")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createToolkitRequest("Reusable Toolkit", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Reusable Toolkit"));
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
                .andExpect(jsonPath("$.exercise.snapshot.toolkit.name").value("Frozen Toolkit Name"))
                .andExpect(jsonPath("$.exercise.snapshot.subtasks[0].name").value("Manual match"))
                .andExpect(jsonPath("$.exercise.snapshot.sharedKpis[0].deliveryHc").value(2.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String exerciseId = JsonPath.read(createdExercise, "$.exercise.id");
        UUID createdExerciseId = UUID.fromString(exerciseId);

        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "select count(*) from exercise_volume_monthly_input where exercise_id = ?",
                        Integer.class,
                        createdExerciseId));
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "select count(*) from exercise_volume_daily_input where exercise_id = ?",
                        Integer.class,
                        createdExerciseId));
        org.junit.jupiter.api.Assertions.assertEquals(
                728,
                jdbcTemplate.queryForObject(
                        "select count(*) from exercise_volume_slot_input where exercise_id = ?",
                        Integer.class,
                        createdExerciseId));

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
                update timesheet_kpi set hc = 99
                where sync_run_id = ? and carrier = 'Carrier A'
                  and site = 'Kuala Lumpur' and customer_country = 'Australia'
                """,
                MONTHLY_RUN_ID);

        mockMvc.perform(get("/api/v1/supervisor/exercises/{id}", exerciseId)
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.toolkit.name").value("Frozen Toolkit Name"))
                .andExpect(jsonPath("$.snapshot.subtasks[0].name").value("Manual match"))
                .andExpect(jsonPath("$.snapshot.sharedKpis[0].deliveryHc").value(2.0))
                .andExpect(jsonPath("$.snapshot.timesheetSyncDate").value("2026-08-05"));
    }

    @Test
    void linksCompletedTmsSessionsInConfiguredPeriodOnCreate() throws Exception {
        String toolkitId = JsonPath.read(createToolkit("TMS Population Toolkit"), "$.id");
        UUID toolkitUuid = UUID.fromString(toolkitId);
        String agentCcgid = "AGENT-TMS-001";
        UUID inRangeId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID outOfRangeId = UUID.fromString("60000000-0000-0000-0000-000000000002");
        UUID discardedId = UUID.fromString("60000000-0000-0000-0000-000000000003");

        insertCompletedTmsSession(
                inRangeId,
                "TMS-IN-RANGE",
                agentCcgid,
                toolkitUuid,
                Instant.parse("2026-08-15T10:00:00Z"));
        insertCompletedTmsSession(
                outOfRangeId,
                "TMS-OUT-RANGE",
                agentCcgid,
                toolkitUuid,
                Instant.parse("2026-07-15T10:00:00Z"));
        insertTmsSession(
                discardedId,
                "TMS-DISCARDED",
                agentCcgid,
                toolkitUuid,
                "DISCARDED",
                Instant.parse("2026-08-16T10:00:00Z"));

        String exerciseId = JsonPath.read(createExercise(toolkitId), "$.exercise.id");

        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1),
                jdbcTemplate.queryForObject(
                        """
                        select count(*) from exercise_tms_session
                        where exercise_id = ? and tms_session_id = ?
                        """,
                        Integer.class,
                        UUID.fromString(exerciseId),
                        inRangeId));
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(0),
                jdbcTemplate.queryForObject(
                        """
                        select count(*) from exercise_tms_session
                        where exercise_id = ? and tms_session_id in (?, ?)
                        """,
                        Integer.class,
                        UUID.fromString(exerciseId),
                        outOfRangeId,
                        discardedId));
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1),
                jdbcTemplate.queryForObject(
                        """
                        select count(*) from cycle_time_baseline
                        where exercise_id = ? and is_active = true and baseline_type = 'SYSTEM'
                        """,
                        Integer.class,
                        UUID.fromString(exerciseId)));
    }

    @Test
    void seedsArchiveAssociatedDataWithoutShiftOrManualCycleTime() throws Exception {
        String toolkitId = JsonPath.read(createToolkit("Archive Source Toolkit"), "$.id");
        String sourceId = JsonPath.read(createExercise(toolkitId), "$.exercise.id");
        UUID sourceExerciseId = UUID.fromString(sourceId);
        seedArchivedAssociatedData(sourceExerciseId);

        String targetId = JsonPath.read(createExercise(toolkitId), "$.exercise.id");
        UUID targetExerciseId = UUID.fromString(targetId);

        org.junit.jupiter.api.Assertions.assertEquals(
                sourceExerciseId,
                jdbcTemplate.queryForObject(
                        "select initialized_from_exercise_id from rst_exercise where id = ?",
                        UUID.class,
                        targetExerciseId));
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1),
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from exercise_production_support_item
                        where exercise_id = ? and deleted_at is null and activity = 'Archive reporting'
                        """,
                        Integer.class,
                        targetExerciseId));
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(0),
                jdbcTemplate.queryForObject(
                        """
                        select count(*) from cycle_time_baseline
                        where exercise_id = ? and baseline_type = 'MANUAL'
                        """,
                        Integer.class,
                        targetExerciseId));
    }

    @Test
    void rejectsCreateWhenSharedKpiNoLongerMatchesActiveTimesheet() throws Exception {
        String toolkitId = JsonPath.read(createToolkit("Stale KPI Toolkit"), "$.id");
        jdbcTemplate.update(
                """
                update toolkit_shared_kpi_selection
                   set deleted_at = ?, deleted_by = ?, updated_at = ?, updated_by = ?
                 where toolkit_id = ? and deleted_at is null
                """,
                NOW,
                SUPERVISOR_CCGID,
                NOW,
                SUPERVISOR_CCGID,
                UUID.fromString(toolkitId));
        jdbcTemplate.update(
                """
                insert into toolkit_shared_kpi_selection
                    (id, toolkit_id, carrier, site, customer_country,
                     created_at, created_by, updated_at, updated_by, version)
                values (?, ?, 'Carrier Z', 'Nowhere', 'Mars', ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                UUID.fromString(toolkitId),
                NOW,
                SUPERVISOR_CCGID,
                NOW,
                SUPERVISOR_CCGID);

        mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exerciseRequest(toolkitId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/stale-shared-kpi-selection"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "Update the Toolkit Shared KPI")));

        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(0),
                jdbcTemplate.queryForObject(
                        "select count(*) from rst_exercise where toolkit_id = ?",
                        Integer.class,
                        UUID.fromString(toolkitId)));
    }

    @Test
    void rejectsSlotPeriodLongerThanTwelveWeeks() throws Exception {
        String toolkitId = JsonPath.read(createToolkit("Slot Limit Toolkit"), "$.id");
        mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exerciseRequest(toolkitId).replace(
                                "\"slotWeeks\": 4", "\"slotWeeks\": 13")))
                .andExpect(status().is(422));
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
                     owner_ccgid, created_at, updated_at, version)
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
                SUPERVISOR_CCGID,
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

    private String createExercise(String toolkitId) throws Exception {
        return mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exerciseRequest(toolkitId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void seedArchivedAssociatedData(UUID exerciseId) {
        UUID supportItemId = UUID.randomUUID();
        jdbcTemplate.update(
                "update rst_exercise set workflow_status = 'APPROVED', validated_at = ? where id = ?",
                NOW,
                exerciseId);
        jdbcTemplate.update(
                """
                insert into exercise_production_support_item
                    (id, exercise_id, lineage_id, category, activity, frequency_code,
                     volume, unit_of_measure, workload_per_unit_minutes,
                     created_at, created_by, updated_at, updated_by, version)
                values (?, ?, ?, 'Operations', 'Archive reporting', 'MONTHLY',
                        10, 'case', 5,
                        ?, ?, ?, ?, 0)
                """,
                supportItemId,
                exerciseId,
                supportItemId,
                NOW,
                SUPERVISOR_CCGID,
                NOW,
                SUPERVISOR_CCGID);
        jdbcTemplate.update(
                """
                insert into cycle_time_baseline
                    (id, exercise_id, baseline_type, median_seconds, sample_count,
                     calculation_method, manual_reason, is_active, calculated_at, calculated_by)
                values (?, ?, 'MANUAL', 120, null, 'MANUAL_ENTRY', 'Archive manual CT', true, ?, ?)
                """,
                UUID.randomUUID(),
                exerciseId,
                NOW,
                SUPERVISOR_CCGID);
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

    private void insertCompletedTmsSession(
            UUID id, String sessionNo, String agentCcgid, UUID toolkitId, Instant startedAt) {
        insertTmsSession(id, sessionNo, agentCcgid, toolkitId, "COMPLETED", startedAt);
    }

    private void insertTmsSession(
            UUID id,
            String sessionNo,
            String agentCcgid,
            UUID toolkitId,
            String status,
            Instant startedAt) {
        jdbcTemplate.update(
                """
                insert into tms_session
                    (id, session_no, agent_ccgid, toolkit_id, processed_volume,
                     reference, remarks, status, started_at, ended_at,
                     net_duration_seconds,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, 10, 'REF', '', ?, ?, ?,
                        600,
                        ?, ?, 0)
                """,
                id,
                sessionNo,
                agentCcgid,
                toolkitId,
                status,
                startedAt,
                startedAt.plusSeconds(600),
                NOW,
                NOW);
    }

    private void insertSyncRun(UUID id, String kind, int rowCount) {
        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, kind, sync_date, attempt_no, status, row_count, started_at, completed_at)
                values (?, ?, date '2026-08-05', 1, 'ACTIVE', ?, ?, ?)
                """,
                id,
                kind,
                rowCount,
                NOW,
                NOW);
    }

    private void insertPerson(UUID runId, String ccgid, String name) {
        jdbcTemplate.update(
                "insert into timesheet_person (sync_run_id, ccgid, emp_id, name) values (?, ?, ?, ?)",
                runId,
                ccgid,
                ccgid,
                name);
    }

    private void insertPosition(UUID runId, String positionId, String roleType, String parentPositionId) {
        jdbcTemplate.update(
                """
                insert into timesheet_position
                    (sync_run_id, position_id, role_type, parent_position_id)
                values (?, ?, ?, ?)
                """,
                runId,
                positionId,
                roleType,
                parentPositionId);
    }

    private void insertOccupancy(UUID runId, String positionId, String ccgid) {
        jdbcTemplate.update(
                """
                insert into timesheet_occupancy
                    (sync_run_id, position_id, emp_ccgid, emp_id)
                values (?, ?, ?, ?)
                """,
                runId,
                positionId,
                ccgid,
                ccgid);
    }

    private void insertScope(UUID runId) {
        jdbcTemplate.update(
                """
                insert into timesheet_scope
                    (sync_run_id, supervisor_position_id, pl3_code, pl3_name,
                     center, domain, pl1, pl2)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                SUPERVISOR_POSITION_ID,
                PL3_CODE,
                "Bank Reconciliation",
                "Kuala Lumpur",
                "Finance",
                "Accounting",
                "Record to Report");
    }

    private void insertAssignment(UUID runId, String empCcgid) {
        jdbcTemplate.update(
                """
                insert into timesheet_assignment
                    (sync_run_id, emp_ccgid, emp_id, supervisor_position_id, pl3_code)
                values (?, ?, ?, ?, ?)
                """,
                runId,
                empCcgid,
                empCcgid,
                SUPERVISOR_POSITION_ID,
                PL3_CODE);
    }

    private void insertKpi(UUID runId, String carrier, String site, String country, String hc) {
        jdbcTemplate.update(
                """
                insert into timesheet_kpi
                    (sync_run_id, supervisor_position_id, pl3_code,
                     carrier, site, customer_country, hc)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                SUPERVISOR_POSITION_ID,
                PL3_CODE,
                carrier,
                site,
                country,
                new java.math.BigDecimal(hc));
    }
}
