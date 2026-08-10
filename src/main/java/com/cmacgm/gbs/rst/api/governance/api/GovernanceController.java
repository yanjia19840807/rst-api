package com.cmacgm.gbs.rst.api.governance.api;

import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.governance.application.GovernancePrototypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prototype governance report endpoints (static mock payloads for LTH/HO UI).
 */
@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final GovernancePrototypeService governance;

    /**
     * Creates the governance controller.
     *
     * @param governance prototype data service
     */
    public GovernanceController(GovernancePrototypeService governance) {
        this.governance = governance;
    }

    /**
     * Global dashboard metrics and center aging tables.
     *
     * @return dashboard payload
     */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return governance.dashboard();
    }

    /**
     * RST repository Shared KPI rows.
     *
     * @return repository rows
     */
    @GetMapping("/repository")
    public List<Map<String, Object>> repository() {
        return governance.repository();
    }

    /**
     * Support repository summary and granular rows.
     *
     * @return support repository payload
     */
    @GetMapping("/support-repository")
    public Map<String, Object> supportRepository() {
        return governance.supportRepository();
    }

    /**
     * Same-PL3 benchmarking payload.
     *
     * @return benchmarking payload
     */
    @GetMapping("/benchmarking")
    public Map<String, Object> benchmarking() {
        return governance.benchmarking();
    }

    /**
     * Validation workflow stuck-exercise rows.
     *
     * @return validation workflow rows
     */
    @GetMapping("/validation-workflow")
    public List<Map<String, Object>> validationWorkflow() {
        return governance.validationWorkflow();
    }
}
