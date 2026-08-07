package com.cmacgm.gbs.rst.api.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Typed key/value assumption belonging to a Scenario. */
@Entity
@Table(name = "scenario_assumption")
public class ScenarioAssumption {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "parameter_code", nullable = false, length = 80)
    private String parameterCode;

    @Column(name = "numeric_value", precision = 24, scale = 10)
    private BigDecimal numericValue;

    @Column(name = "text_value")
    private String textValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @Column(length = 30)
    private String unit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ScenarioAssumption() {
    }

    /**
     * Creates a numeric assumption (detached until attached to a Scenario).
     *
     * @param parameterCode controlled parameter code
     * @param numericValue numeric value
     * @param unit optional unit
     * @param actorUserId creating user
     * @param now creation timestamp
     * @return assumption entity
     */
    public static ScenarioAssumption numeric(
            String parameterCode, BigDecimal numericValue, String unit, UUID actorUserId, Instant now) {
        ScenarioAssumption assumption = base(parameterCode, actorUserId, now);
        assumption.numericValue = numericValue;
        assumption.unit = unit;
        return assumption;
    }

    /**
     * Creates a text assumption.
     *
     * @param parameterCode controlled parameter code
     * @param textValue text value
     * @param actorUserId creating user
     * @param now creation timestamp
     * @return assumption entity
     */
    public static ScenarioAssumption text(
            String parameterCode, String textValue, UUID actorUserId, Instant now) {
        ScenarioAssumption assumption = base(parameterCode, actorUserId, now);
        assumption.textValue = textValue;
        return assumption;
    }

    /**
     * Creates a boolean assumption.
     *
     * @param parameterCode controlled parameter code
     * @param booleanValue boolean value
     * @param actorUserId creating user
     * @param now creation timestamp
     * @return assumption entity
     */
    public static ScenarioAssumption bool(
            String parameterCode, boolean booleanValue, UUID actorUserId, Instant now) {
        ScenarioAssumption assumption = base(parameterCode, actorUserId, now);
        assumption.booleanValue = booleanValue;
        return assumption;
    }

    private static ScenarioAssumption base(String parameterCode, UUID actorUserId, Instant now) {
        ScenarioAssumption assumption = new ScenarioAssumption();
        assumption.id = UUID.randomUUID();
        assumption.parameterCode = parameterCode;
        assumption.createdAt = now;
        assumption.createdBy = actorUserId;
        assumption.updatedAt = now;
        assumption.updatedBy = actorUserId;
        return assumption;
    }

    void attach(Scenario scenario) {
        this.scenario = scenario;
    }

    public UUID getId() { return id; }
    public String getParameterCode() { return parameterCode; }
    public BigDecimal getNumericValue() { return numericValue; }
    public String getTextValue() { return textValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public LocalDate getDateValue() { return dateValue; }
    public String getUnit() { return unit; }
}
