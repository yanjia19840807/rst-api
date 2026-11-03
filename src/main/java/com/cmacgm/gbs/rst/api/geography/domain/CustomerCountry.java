package com.cmacgm.gbs.rst.api.geography.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fixed Customer Country catalog. Timesheet sync does not maintain this table. */
@Entity
@Table(name = "customer_country")
public class CustomerCountry {

    @Id
    @Column(length = 120)
    private String name;

    protected CustomerCountry() {
    }

    public CustomerCountry(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
