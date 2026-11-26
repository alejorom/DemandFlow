package io.github.alejorom.demandflow.audit.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.alejorom.demandflow.audit.persistence.CatalogAuditEvent;
import io.github.alejorom.demandflow.audit.persistence.CatalogAuditInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** T1/T5: trusted application snapshots, never a map of arbitrary request parameters. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CatalogAuditWriter {
    public enum Operation { CREATE, UPDATE, DEACTIVATE, REACTIVATE }
    public enum ActorOrigin { LOCAL_USER, SYSTEM }
    public record PeriodState(String label, LocalDate startDate, LocalDate endDate, boolean active) { }
    public record RateState(Long periodId, Short typeId, BigDecimal amountCop, boolean active) { }

    private final CatalogAuditInsert insert;
    private final Clock clock;

    public CatalogAuditWriter(CatalogAuditInsert insert, Clock clock) {
        this.insert = insert;
        this.clock = clock;
    }

    public void recordPeriod(Long id, Operation operation, PeriodState before, PeriodState after, ActorOrigin origin) {
        append("TARIFF_PERIOD", id, operation, before == null ? null : fields(before), fields(after), origin);
    }

    public void recordRate(Long id, Operation operation, RateState before, RateState after, ActorOrigin origin) {
        if (before != null && (!Objects.equals(before.periodId(), after.periodId())
                || !Objects.equals(before.typeId(), after.typeId()))) {
            throw new IllegalArgumentException("Audit cannot describe a rate reassignment");
        }
        append("HOURLY_RATE", id, operation, before == null ? null : fields(before), fields(after), origin);
    }

    private void append(String entity, Long id, Operation operation, Map<String, String> before,
            Map<String, String> after, ActorOrigin origin) {
        Objects.requireNonNull(id, "Audit entity id is required");
        Objects.requireNonNull(operation, "Audit operation is required");
        Objects.requireNonNull(origin, "Audit origin is required");
        if ((operation == Operation.CREATE) != (before == null)) {
            throw new IllegalArgumentException("Audit creation requires an absent previous state");
        }
        var changes = new LinkedHashMap<String, String>();
        after.forEach((field, value) -> {
            if (before == null || !value.equals(before.get(field))) {
                changes.put(field, value);
            }
        });
        if (operation == Operation.UPDATE && changes.containsKey("active")) {
            throw new IllegalArgumentException("Audit state changes require an explicit transition");
        }
        if (operation == Operation.DEACTIVATE || operation == Operation.REACTIVATE) {
            String expected = Boolean.toString(operation == Operation.REACTIVATE);
            if (changes.size() != 1 || !expected.equals(changes.get("active"))) {
                throw new IllegalArgumentException("Audit transition must describe only an effective state change");
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        UUID mutation = UUID.randomUUID();
        var instant = clock.instant().truncatedTo(ChronoUnit.MICROS);
        changes.forEach((field, value) -> insert.append(new CatalogAuditEvent(mutation, entity,
                entity.equals("TARIFF_PERIOD") ? id : null, entity.equals("HOURLY_RATE") ? id : null,
                operation.name(), field, before == null ? null : before.get(field), value, instant, origin.name())));
    }

    private static Map<String, String> fields(PeriodState state) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("label", Objects.requireNonNull(state.label()));
        fields.put("start_date", state.startDate().toString());
        fields.put("end_date", state.endDate().toString());
        fields.put("active", Boolean.toString(state.active()));
        return fields;
    }

    private static Map<String, String> fields(RateState state) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("period_id", state.periodId().toString());
        fields.put("type_id", state.typeId().toString());
        fields.put("amount_cop", state.amountCop().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        fields.put("active", Boolean.toString(state.active()));
        return fields;
    }
}
