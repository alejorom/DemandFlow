package io.github.alejorom.demandflow.tariffs.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.ActorOrigin;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.Operation;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.PeriodState;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import io.github.alejorom.demandflow.tariffs.persistence.TariffPeriodRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** HU-001; T1/T3/T4/T5: the proxy commits business and audit before returning to its caller. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TariffPeriodService {
    private final TariffPeriodRepository periods;
    private final CatalogAuditWriter audit;
    @PersistenceContext private EntityManager entityManager;

    public TariffPeriodService(TariffPeriodRepository periods, CatalogAuditWriter audit) {
        this.periods = periods;
        this.audit = audit;
    }

    public PeriodView create(String label, LocalDate startDate, LocalDate endDate) {
        var period = new TariffPeriod(label, startDate, endDate);
        checkOverlap(period);
        periods.save(period);
        entityManager.flush();
        audit.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
        entityManager.flush();
        return view(period);
    }

    @Transactional(readOnly = true)
    public List<PeriodView> list() {
        return periods.findAllByOrderByIdAsc().stream().map(TariffPeriodService::view).toList();
    }

    @Transactional(readOnly = true)
    public PeriodView get(Long id) {
        requireId(id);
        return view(periods.findById(id).orElseThrow(() -> new EntityNotFoundException("Period not found")));
    }

    public PeriodView edit(Long id, Long expectedVersion, String label, LocalDate startDate, LocalDate endDate) {
        var period = lock(id, expectedVersion);
        var before = snapshot(period);
        // Validate and pre-query before dirtying a managed entity: AUTO flush must not bypass the pre-check.
        var proposed = new TariffPeriod(label, startDate, endDate);
        checkOverlap(proposed.getStartDate(), proposed.getEndDate(), id);
        boolean changed = period.edit(proposed.getLabel(), startDate, endDate);
        if (changed) {
            entityManager.flush();
            audit.recordPeriod(id, Operation.UPDATE, before, snapshot(period), ActorOrigin.LOCAL_USER);
            entityManager.flush();
        }
        return view(period);
    }

    public PeriodView deactivate(Long id, Long expectedVersion) {
        return changeState(id, expectedVersion, false);
    }

    public PeriodView reactivate(Long id, Long expectedVersion) {
        return changeState(id, expectedVersion, true);
    }

    private PeriodView changeState(Long id, Long expectedVersion, boolean active) {
        var period = lock(id, expectedVersion);
        // Reuse the same integrity rules, without a fabricated check for future demand references.
        new TariffPeriod(period.getLabel(), period.getStartDate(), period.getEndDate());
        checkOverlap(period);
        var before = snapshot(period);
        if (active) {
            period.reactivate();
        } else {
            period.deactivate();
        }
        entityManager.flush();
        audit.recordPeriod(id, active ? Operation.REACTIVATE : Operation.DEACTIVATE,
                before, snapshot(period), ActorOrigin.LOCAL_USER);
        entityManager.flush();
        return view(period);
    }

    private TariffPeriod lock(Long id, Long expectedVersion) {
        requireId(id);
        var period = periods.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Period not found"));
        if (expectedVersion == null || !Objects.equals(expectedVersion, period.getVersion())) {
            throw new OptimisticLockingFailureException("Period version is missing or stale; reload the record");
        }
        return period;
    }

    private void checkOverlap(TariffPeriod period) {
        checkOverlap(period.getStartDate(), period.getEndDate(), period.getId());
    }

    private void checkOverlap(LocalDate startDate, LocalDate endDate, Long excludedId) {
        if (periods.overlaps(startDate, endDate, excludedId)) {
            throw new IllegalStateException("Period dates overlap another period");
        }
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Period identifier is required");
        }
    }

    private static PeriodState snapshot(TariffPeriod period) {
        return new PeriodState(period.getLabel(), period.getStartDate(), period.getEndDate(), period.isActive());
    }

    private static PeriodView view(TariffPeriod period) {
        return new PeriodView(period.getId(), period.getLabel(), period.getStartDate(), period.getEndDate(),
                period.isActive(), period.getVersion());
    }

    public record PeriodView(Long id, String label, LocalDate startDate, LocalDate endDate,
                             boolean active, Long version) { }
}
