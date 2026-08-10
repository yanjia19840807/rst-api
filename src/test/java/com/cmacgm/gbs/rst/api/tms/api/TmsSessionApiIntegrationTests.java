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

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
        jdbcTemplate.update("delete from timesheet_snapshot_row");
        jdbcTemplate.update("delete from timesheet_sync_run");
        jdbcTemplate.update("delete from toolkit_shared_kpi_selection");
        jdbcTemplate.update("delete from toolkit_subtask");
        jdbcTemplate.update("delete from toolkit");
        jdbcTemplate.update("delete from app_user");

        Instant now = Instant.parse("2026-08-05T01:00:00Z");
        jdbcTemplate.update(
                """
                insert into app_user
                    (id, ccgid, display_name, email, active, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                USER_ID,
                "AGENT001",
                "Test Agent",
                "agent@example.com",
                true,
                now,
                now);
        jdbcTemplate.update(
                """
                insert into app_user
                    (id, ccgid, display_name, email, active, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "SUPERVISOR001",
                "Test Supervisor",
                "supervisor@example.com",
                true,
                now,
                now);
        jdbcTemplate.update(
                """
                insert into toolkit
                    (id, name, supervisor_position_id, center, domain, pl1, pl2,
                     pl3_name, primary_pl3_code, combine_subtasks_time,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, sync_date, status, row_count, started_at, completed_at)
                values (?, current_date, 'ACTIVE', 1, ?, ?)
                """,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                now,
                now);
        jdbcTemplate.update(
                """
                insert into timesheet_snapshot_row
                    (id, sync_run_id, emp_ccgid, emp_name, emp_position_id,
                     supervisor_ccgid, supervisor_name, supervisor_position_id,
                     center, site, domain, pl1, pl2, pl3_code, pl3_name,
                     carrier, customer_country, hc)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "AGENT001",
                "Test Agent",
                "POS-AGENT-001",
                "SUPERVISOR001",
                "Test Supervisor",
                "POS-SUP-001",
                "Kuala Lumpur",
                "KL",
                "Finance",
                "Accounting",
                "Record to Report",
                "BANK_REC",
                "Bank Reconciliation",
                "Carrier A",
                "Australia",
                1);
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
        mockMvc.perform(get("/api/v1/timesheet/supervisor/hierarchy")
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
                .andExpect(jsonPath("$.snapshot.toolkit.name").value("Bank Rec Manual Check"))
                .andExpect(jsonPath("$.snapshot.subtasks[0].name").value("Manual match"))
                .andReturn().getResponse().getContentAsString();
        String exerciseId = JsonPath.read(response, "$.id");

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
