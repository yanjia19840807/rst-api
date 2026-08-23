package com.cmacgm.gbs.rst.api.tms.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
class TmsSessionApiIntegrationTests {

    private static final UUID TOOLKIT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SUBTASK_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedAgentAndToolkit() {
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
        jdbcTemplate.update("delete from timesheet_sync_issue");
        jdbcTemplate.update("delete from timesheet_kpi");
        jdbcTemplate.update("delete from timesheet_assignment");
        jdbcTemplate.update("delete from timesheet_scope");
        jdbcTemplate.update("delete from timesheet_occupancy");
        jdbcTemplate.update("delete from timesheet_position");
        jdbcTemplate.update("delete from timesheet_person");
        jdbcTemplate.update("delete from timesheet_sync_run");
        jdbcTemplate.update("delete from toolkit_shared_kpi_selection");
        jdbcTemplate.update("delete from toolkit_subtask");
        jdbcTemplate.update("delete from toolkit");

        Instant now = Instant.parse("2026-08-05T01:00:00Z");
        jdbcTemplate.update(
                """
                insert into toolkit
                    (id, name, supervisor_position_id, center, domain, pl1, pl2,
                     pl3_name, primary_pl3_code, combine_subtasks_time,
                     owner_ccgid, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TOOLKIT_ID,
                "Bank Rec Manual Check",
                "POS-SUP-001",
                "Kuala Lumpur",
                "Finance",
                "Accounting",
                "Record to Report",
                "Bank Reconciliation",
                "BANK_REC",
                false,
                "SUPERVISOR001",
                now,
                now,
                0);
        jdbcTemplate.update(
                """
                insert into toolkit_subtask
                    (id, toolkit_id, name, display_order, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                SUBTASK_ID,
                TOOLKIT_ID,
                "Manual match",
                1,
                now,
                now,
                0);
        jdbcTemplate.update(
                """
                insert into toolkit_shared_kpi_selection
                    (id, toolkit_id, carrier, site, customer_country,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                TOOLKIT_ID,
                "Carrier A",
                "KL",
                "Australia",
                now,
                now,
                0);
        UUID dailyRunId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID monthlyRunId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, kind, sync_date, attempt_no, status, row_count, started_at, completed_at)
                values (?, 'DAILY', current_date, 1, 'ACTIVE', 1, ?, ?)
                """,
                dailyRunId,
                now,
                now);
        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, kind, sync_date, attempt_no, status, row_count, started_at, completed_at)
                values (?, 'MONTHLY', current_date, 1, 'ACTIVE', 1, ?, ?)
                """,
                monthlyRunId,
                now,
                now);
        jdbcTemplate.update(
                "insert into timesheet_person (sync_run_id, ccgid, emp_id, name) values (?, ?, ?, ?)",
                dailyRunId,
                "SUPERVISOR001",
                "SUPERVISOR001",
                "Test Supervisor");
        jdbcTemplate.update(
                "insert into timesheet_person (sync_run_id, ccgid, emp_id, name) values (?, ?, ?, ?)",
                dailyRunId,
                "AGENT001",
                "AGENT001",
                "Test Agent");
        jdbcTemplate.update(
                """
                insert into timesheet_position
                    (sync_run_id, position_id, role_type, parent_position_id)
                values (?, 'POS-SUP-001', 'SUPERVISOR', 'POS-SRM-001')
                """,
                dailyRunId);
        jdbcTemplate.update(
                """
                insert into timesheet_occupancy
                    (sync_run_id, position_id, emp_ccgid, emp_id)
                values (?, 'POS-SUP-001', 'SUPERVISOR001', 'SUPERVISOR001')
                """,
                dailyRunId);
        jdbcTemplate.update(
                """
                insert into timesheet_scope
                    (sync_run_id, supervisor_position_id, pl3_code, pl3_name,
                     center, domain, pl1, pl2)
                values (?, 'POS-SUP-001', 'BANK_REC', 'Bank Reconciliation',
                        'Kuala Lumpur', 'Finance', 'Accounting', 'Record to Report')
                """,
                dailyRunId);
        jdbcTemplate.update(
                """
                insert into timesheet_assignment
                    (sync_run_id, emp_ccgid, emp_id, supervisor_position_id, pl3_code)
                values (?, 'AGENT001', 'AGENT001', 'POS-SUP-001', 'BANK_REC')
                """,
                dailyRunId);
        jdbcTemplate.update(
                """
                insert into timesheet_kpi
                    (sync_run_id, supervisor_position_id, pl3_code,
                     carrier, site, customer_country, hc)
                values (?, 'POS-SUP-001', 'BANK_REC', 'Carrier A', 'KL', 'Australia', 1)
                """,
                monthlyRunId);
    }

    @Test
    void completesTheAgentSessionLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/toolkits")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TOOLKIT_ID.toString()));

        String response = mockMvc.perform(post("/api/v1/tms/sessions")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolkitId": "%s",
                                  "subtaskId": "%s",
                                  "processedVolume": 25,
                                  "reference": "INV-100",
                                  "remarks": "API integration test"
                                }
                                """.formatted(TOOLKIT_ID, SUBTASK_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sessionId = JsonPath.read(response, "$.id");

        mockMvc.perform(post("/api/v1/tms/sessions")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolkitId": "%s",
                                  "subtaskId": "%s",
                                  "processedVolume": 1
                                }
                                """.formatted(TOOLKIT_ID, SUBTASK_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());

        mockMvc.perform(post("/api/v1/tms/sessions/{id}/pause", sessionId)
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"));

        mockMvc.perform(get("/api/v1/tms/sessions/current")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString().trim();
                    if (!body.isEmpty() && !"null".equals(body)) {
                        throw new AssertionError("Expected no running session, got: " + body);
                    }
                });

        String secondResponse = mockMvc.perform(post("/api/v1/tms/sessions")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolkitId": "%s",
                                  "subtaskId": "%s",
                                  "processedVolume": 3,
                                  "reference": "INV-101"
                                }
                                """.formatted(TOOLKIT_ID, SUBTASK_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondSessionId = JsonPath.read(secondResponse, "$.id");

        mockMvc.perform(post("/api/v1/tms/sessions/{id}/resume", sessionId)
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/tms/sessions/{id}/end", secondSessionId)
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(post("/api/v1/tms/sessions/{id}/resume", sessionId)
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));

        mockMvc.perform(post("/api/v1/tms/sessions/{id}/end", sessionId)
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/api/v1/tms/sessions")
                        .header("X-Dev-Ccgid", "AGENT001")
                        .header("X-Dev-Role", "AGENT")
                        .queryParam("status", "completed")
                        .queryParam("reference", "INV-100")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[0].id").value(sessionId));
    }

    @Test
    void publishesTheTmsOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/tms/sessions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tms/sessions/{id}/pause']").exists());
    }

    @Test
    void freezesAnExerciseForTheDevSupervisor() throws Exception {
        mockMvc.perform(get("/api/v1/timesheet/toolkit-hierarchy")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supervisorPositionId").value("POS-SUP-001"));

        String response = mockMvc.perform(post("/api/v1/supervisor/exercises")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolkitId": "%s",
                                  "sizingMonth": "2026-09",
                                  "slotStartDate": "2026-09-07",
                                  "slotWeeks": 4,
                                  "tmsFrom": "2026-08-01",
                                  "tmsTo": "2026-08-31"
                                }
                                """.formatted(TOOLKIT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercise.snapshot.toolkit.name").value("Bank Rec Manual Check"))
                .andExpect(jsonPath("$.exercise.snapshot.subtasks[0].name").value("Manual match"))
                .andReturn().getResponse().getContentAsString();
        String exerciseId = JsonPath.read(response, "$.exercise.id");

        mockMvc.perform(get("/api/v1/supervisor/exercises/{id}", exerciseId)
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timesheetSyncDate").exists());
    }

    @Test
    void rejectsAUserWithoutTheAgentRole() throws Exception {
        mockMvc.perform(get("/api/v1/toolkits")
                        .with(user("approver").roles("APPROVER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/access-denied"));
    }
}
