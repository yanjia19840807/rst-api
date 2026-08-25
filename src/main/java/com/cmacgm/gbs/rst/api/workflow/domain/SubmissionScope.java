package com.cmacgm.gbs.rst.api.workflow.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Immutable authorization scope frozen at submit time. */
@Entity
@Table(name = "submission_scope")
public class SubmissionScope {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_instance_id", nullable = false)
    private ProcessInstance workflowInstance;

    @Column(name = "scope_key", nullable = false, length = 64)
    private String scopeKey;

    @Column(name = "scope_level", nullable = false, length = 20)
    private String scopeLevel;

    @Column(length = 120)
    private String center;

    @Column(length = 80)
    private String site;

    @Column(length = 120)
    private String domain;

    @Column(length = 200)
    private String pl1;

    @Column(length = 200)
    private String pl2;

    @Column(name = "pl3_code", length = 80)
    private String pl3Code;

    @Column(name = "pl3_name", length = 200)
    private String pl3Name;

    @Column(length = 120)
    private String carrier;

    @Column(name = "customer_country", length = 120)
    private String customerCountry;

    protected SubmissionScope() {
    }

    /**
     * Creates a detached submission scope row.
     *
     * @param scopeKey hash of normalized scope values
     * @param scopeLevel deepest populated level
     * @param center center
     * @param site site
     * @param domain domain
     * @param pl1 PL1
     * @param pl2 PL2
     * @param pl3Code PL3 code
     * @param pl3Name PL3 name
     * @param carrier carrier
     * @param customerCountry customer country
     * @return scope entity
     */
    public static SubmissionScope create(
            String scopeKey,
            String scopeLevel,
            String center,
            String site,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String carrier,
            String customerCountry) {
        SubmissionScope scope = new SubmissionScope();
        scope.id = UUID.randomUUID();
        scope.scopeKey = scopeKey;
        scope.scopeLevel = scopeLevel;
        scope.center = center;
        scope.site = site;
        scope.domain = domain;
        scope.pl1 = pl1;
        scope.pl2 = pl2;
        scope.pl3Code = pl3Code;
        scope.pl3Name = pl3Name;
        scope.carrier = carrier;
        scope.customerCountry = customerCountry;
        return scope;
    }

    /**
     * Binds this scope row to its workflow instance.
     *
     * @param workflowInstance parent process
     */
    public void attach(ProcessInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public UUID getId() { return id; }
    public String getScopeKey() { return scopeKey; }
    public String getScopeLevel() { return scopeLevel; }
    public String getCenter() { return center; }
    public String getSite() { return site; }
    public String getDomain() { return domain; }
    public String getPl1() { return pl1; }
    public String getPl2() { return pl2; }
    public String getPl3Code() { return pl3Code; }
    public String getPl3Name() { return pl3Name; }
    public String getCarrier() { return carrier; }
    public String getCustomerCountry() { return customerCountry; }
}
