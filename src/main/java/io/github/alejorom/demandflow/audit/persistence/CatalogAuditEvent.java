package io.github.alejorom.demandflow.audit.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** T5: immutable audit row; scalar foreign keys keep audit independent of tariffs. */
@Entity
@Immutable
@Table(name = "catalog_audit_event", schema = "demandflow")
public class CatalogAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "mutation_id", nullable = false, updatable = false)
    private UUID mutationId;
    @Column(name = "entity_type", nullable = false, length = 16, updatable = false)
    private String entityType;
    @Column(name = "period_id", updatable = false)
    private Long periodId;
    @Column(name = "rate_id", updatable = false)
    private Long rateId;
    @Column(nullable = false, length = 12, updatable = false)
    private String operation;
    @Column(nullable = false, length = 32, updatable = false)
    private String field;
    @Column(name = "before_value", columnDefinition = "text", updatable = false)
    private String beforeValue;
    @Column(name = "after_value", nullable = false, columnDefinition = "text", updatable = false)
    private String afterValue;
    @Column(name = "occurred_at", nullable = false, precision = 6, updatable = false)
    private Instant occurredAt;
    @Column(name = "actor_origin", nullable = false, length = 10, updatable = false)
    private String actorOrigin;

    protected CatalogAuditEvent() { }

    public CatalogAuditEvent(UUID mutationId, String entityType, Long periodId, Long rateId,
            String operation, String field, String beforeValue, String afterValue,
            Instant occurredAt, String actorOrigin) {
        this.mutationId = mutationId;
        this.entityType = entityType;
        this.periodId = periodId;
        this.rateId = rateId;
        this.operation = operation;
        this.field = field;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.occurredAt = occurredAt;
        this.actorOrigin = actorOrigin;
    }

    public Long getId() { return id; }
    public UUID getMutationId() { return mutationId; }
    public String getEntityType() { return entityType; }
    public Long getPeriodId() { return periodId; }
    public Long getRateId() { return rateId; }
    public String getOperation() { return operation; }
    public String getField() { return field; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorOrigin() { return actorOrigin; }
}
