package com.cmacgm.gbs.rst.api.scenario.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Validation finding captured at EDIT / OFFICIAL / SUBMIT stages. */
@Entity
@Table(name = "validation_result")
public class ValidationResult {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Column(name = "validation_stage", nullable = false, length = 20)
    private String validationStage;

    @Column(name = "rule_code", nullable = false, length = 80)
    private String ruleCode;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "actual_value", length = 200)
    private String actualValue;

    @Column(name = "expected_value", length = 200)
    private String expectedValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    private String remarks;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluated_by")
    private UUID evaluatedBy;

    protected ValidationResult() {
    }

    /**
     * Creates a validation finding.
     *
     * @param exerciseId Exercise under validation
     * @param scenarioId optional scenario context
     * @param validationStage EDIT / OFFICIAL / SUBMIT
     * @param ruleCode stable rule code
     * @param severity INFO / WARNING / SEVERE
     * @param passed whether the rule passed
     * @param actualValue optional actual value text
     * @param expectedValue optional expected value text
     * @param remarks optional remarks (required for failed SEVERE at submit)
     * @param actorUserId evaluator
     * @param now evaluation timestamp
     * @return validation finding
     */
    public static ValidationResult create(
            UUID exerciseId,
            UUID scenarioId,
            String validationStage,
            String ruleCode,
            String severity,
            boolean passed,
            String actualValue,
            String expectedValue,
            String remarks,
            UUID actorUserId,
            Instant now) {
        ValidationResult result = new ValidationResult();
        result.id = UUID.randomUUID();
        result.exerciseId = exerciseId;
        result.scenarioId = scenarioId;
        result.validationStage = validationStage;
        result.ruleCode = ruleCode;
        result.severity = severity;
        result.passed = passed;
        result.actualValue = actualValue;
        result.expectedValue = expectedValue;
        result.remarks = remarks;
        result.evaluatedAt = now;
        result.evaluatedBy = actorUserId;
        return result;
    }

    public UUID getId() { return id; }
    public String getRuleCode() { return ruleCode; }
    public String getSeverity() { return severity; }
    public boolean isPassed() { return passed; }
    public String getRemarks() { return remarks; }
}
