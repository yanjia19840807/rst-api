package com.cmacgm.gbs.rst.api.delegation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class DelegationApiIntegrationTests {

    private static final UUID DAILY_RUN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000210");
    private static final Instant NOW = Instant.parse("2026-08-05T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("delete from rst_delegation");
        jdbcTemplate.update("delete from timesheet_sync_issue");
        jdbcTemplate.update("delete from timesheet_kpi");
        jdbcTemplate.update("delete from timesheet_scope");
        jdbcTemplate.update("delete from timesheet_position");
        jdbcTemplate.update("delete from timesheet_person");
        jdbcTemplate.update("delete from timesheet_sync_run");

        jdbcTemplate.update(
                """
                insert into timesheet_sync_run
                    (id, kind, center, sync_date, attempt_no, status, row_count, started_at, completed_at)
                values (?, 'DAILY', 'Kuala Lumpur', date '2026-08-05', 1, 'ACTIVE', 2, ?, ?)
                """,
                DAILY_RUN_ID,
                NOW,
                NOW);
        insertPerson("SUPERVISOR001", "Test Supervisor", "POS-SUP-001", "Kuala Lumpur");
        insertPerson("AGENT010", "Test Agent AGENT010", "POS-AGT-010", "Kuala Lumpur");
        insertPerson("AGENT011", "Test Agent AGENT011", "POS-AGT-011", "Kuala Lumpur");
        insertPerson("AGENT099", "Other Center Agent", "POS-AGT-099", "GBS CHINA");
    }

    @Test
    void supervisorGrantsRevokesAndDelegateCanActAs() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant until = Instant.now().plus(7, ChronoUnit.DAYS);
        String created = mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delegateCcgid":"AGENT010","validFrom":"%s","validUntil":"%s"}
                                """.formatted(from, until)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delegatorCcgid").value("SUPERVISOR001"))
                .andExpect(jsonPath("$.delegateCcgid").value("AGENT010"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/v1/delegations/received")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/v1/me")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT")
                        .header("X-Rst-Delegation-Id", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ccgid").value("SUPERVISOR001"))
                .andExpect(jsonPath("$.roles[0]").value("SUPERVISOR"))
                .andExpect(jsonPath("$.actor.ccgid").value("AGENT010"))
                .andExpect(jsonPath("$.delegationId").value(id));

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT")
                        .header("X-Rst-Delegation-Id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delegateCcgid":"SUPERVISOR001","validFrom":"%s","validUntil":"%s"}
                                """.formatted(from, until)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/delegation-write-forbidden"));

        mockMvc.perform(post("/api/v1/delegations/{id}/revoke", id)
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/me")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT")
                        .header("X-Rst-Delegation-Id", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/delegation-inactive"));
    }

    @Test
    void hoCannotGrantAndCannotDelegateToSelf() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant until = Instant.now().plus(7, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "HO001")
                        .header("X-Dev-Role", "HO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delegateCcgid":"AGENT010","validFrom":"%s","validUntil":"%s"}
                                """.formatted(from, until)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delegateCcgid":"SUPERVISOR001","validFrom":"%s","validUntil":"%s"}
                                """.formatted(from, until)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/cannot-delegate-self"));
    }

    @Test
    void delegateCannotGrantOnwardAndCannotPickSomeoneWhoAlreadyGranted() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant until = Instant.now().plus(7, ChronoUnit.DAYS);
        String bodyToAgent = """
                {"delegateCcgid":"AGENT010","validFrom":"%s","validUntil":"%s"}
                """.formatted(from, until);
        String bodyToAgent11 = """
                {"delegateCcgid":"AGENT011","validFrom":"%s","validUntil":"%s"}
                """.formatted(from, until);

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyToAgent))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyToAgent11))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/delegation-chain-forbidden"));

        jdbcTemplate.update("delete from rst_delegation");

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "AGENT010")
                        .header("X-Dev-Role", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyToAgent11))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyToAgent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/delegation-chain-forbidden"));
    }

    @Test
    void candidatesAndGrantStayInOwnCenter() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant until = Instant.now().plus(7, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/delegations/candidates")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.ccgid=='AGENT010')]").exists())
                .andExpect(jsonPath("$.items[?(@.ccgid=='AGENT099')]").isEmpty());

        mockMvc.perform(post("/api/v1/delegations")
                        .header("X-Dev-Ccgid", "SUPERVISOR001")
                        .header("X-Dev-Role", "SUPERVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delegateCcgid":"AGENT099","validFrom":"%s","validUntil":"%s"}
                                """.formatted(from, until)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://rst.cmacgm.com/problems/delegate-center-mismatch"));
    }

    private void insertPerson(String ccgid, String name, String positionId, String center) {
        jdbcTemplate.update(
                """
                insert into timesheet_person
                    (sync_run_id, ccgid, emp_id, name, position_id, center)
                values (?, ?, ?, ?, ?, ?)
                """,
                DAILY_RUN_ID,
                ccgid,
                ccgid,
                name,
                positionId,
                center);
    }
}
