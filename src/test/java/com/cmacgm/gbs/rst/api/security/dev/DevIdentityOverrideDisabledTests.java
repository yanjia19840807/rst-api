package com.cmacgm.gbs.rst.api.security.dev;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.dev-identity.override-enabled=false")
class DevIdentityOverrideDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ignoresRequestHeadersWhenOverrideDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("X-Dev-Ccgid", "ADMIN001")
                        .header("X-Dev-Role", "ADMIN")
                        .header("X-Dev-Center", "GBS CHINA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ccgid").value("SUPERVISOR001"))
                .andExpect(jsonPath("$.roles[0]").value("SUPERVISOR"))
                .andExpect(jsonPath("$.center").value("Kuala Lumpur"))
                .andExpect(jsonPath("$.devOverrideEnabled").value(false));
    }
}
