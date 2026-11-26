package io.github.alejorom.demandflow.tariffs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fixed reference data supplied exclusively by Flyway, with no mutation API. */
@Entity
@Table(name = "requirement_type", schema = "demandflow")
public class RequirementType {
    @Id
    private Short id;
    @Column(nullable = false, length = 7, unique = true)
    private String code;
    @Column(nullable = false, length = 7)
    private String name;

    protected RequirementType() { }

    public Short getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
