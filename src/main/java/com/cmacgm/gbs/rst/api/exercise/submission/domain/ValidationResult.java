package com.cmacgm.gbs.rst.api.exercise.submission.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Submit-time Exercise validation finding. */
@Entity
@Table(name = "validation_result")
public class ValidationResult {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 80)
    private ValidationRule ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ValidationSeverity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private Detail detail;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluated_by")
    private String evaluatedBy;

    protected ValidationResult() {
    }

    /**
     * Creates a submit-time finding.
     *
     * @param exerciseId Exercise under validation
     * @param ruleCode submit validation rule
     * @param passed whether the comparison succeeded
     * @param detail structured comparison payload
     * @param actorCcgid evaluator
     * @param now evaluation timestamp
     * @return validation finding
     */
    public static ValidationResult create(
            UUID exerciseId,
            ValidationRule ruleCode,
            boolean passed,
            Detail detail,
            String actorCcgid,
            Instant now) {
        ValidationResult result = new ValidationResult();
        result.id = UUID.randomUUID();
        result.exerciseId = exerciseId;
        result.ruleCode = ruleCode;
        result.severity = passed ? ValidationSeverity.OK : ruleCode.severity();
        result.detail = detail;
        result.evaluatedAt = now;
        result.evaluatedBy = actorCcgid;
        return result;
    }

    public UUID getId() { return id; }
    public ValidationRule getRuleCode() { return ruleCode; }
    public ValidationSeverity getSeverity() { return severity; }
    public Detail getDetail() { return detail; }

    /**
     * Structured rule payload stored in {@code detail_json}.
     *
     * @param reason skip or comparison outcome
     * @param comparedMonths overlapping months that were compared
     * @param mismatches months whose daily sum disagrees with monthly actual
     */
    public record Detail(String reason, int comparedMonths, List<MonthMismatch> mismatches) {
        public Detail {
            mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        }
    }

    /**
     * One overlapping month that failed the daily vs monthly check.
     *
     * @param month YYYY-MM
     * @param daily sum of daily actuals
     * @param monthly monthly actual
     */
    public record MonthMismatch(String month, String daily, String monthly) {
    }
}
