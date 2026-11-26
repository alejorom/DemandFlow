package io.github.alejorom.demandflow.tariffs.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.ActorOrigin;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.Operation;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.RateState;
import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import io.github.alejorom.demandflow.tariffs.persistence.HourlyRateRepository;
import io.github.alejorom.demandflow.tariffs.persistence.HourlyRateRepository.RateRead;
import io.github.alejorom.demandflow.tariffs.persistence.RequirementTypeRepository;
import io.github.alejorom.demandflow.tariffs.persistence.TariffPeriodRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** HU-002; T4: period lock precedes rate loading/writing; availability is never stored. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HourlyRateService {
    private final HourlyRateRepository rates;
    private final TariffPeriodRepository periods;
    private final RequirementTypeRepository types;
    private final CatalogAuditWriter audit;
    @PersistenceContext private EntityManager entityManager;

    public HourlyRateService(HourlyRateRepository rates, TariffPeriodRepository periods,
                             RequirementTypeRepository types, CatalogAuditWriter audit) {
        this.rates = rates;
        this.periods = periods;
        this.types = types;
        this.audit = audit;
    }

    public RateView create(Long periodId, Short typeId, BigDecimal amountCop) {
        requireId(periodId);
        var period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Period not found"));
        requireActive(period);
        if (typeId == null || (typeId != 1 && typeId != 2)) {
            throw new IllegalArgumentException("Rate type must be Soporte or Feature");
        }
        var type = types.findById(typeId).orElseThrow(() -> new EntityNotFoundException("Rate type not found"));
        var rate = new HourlyRate(period, type, amountCop);
        if (rates.existsByPeriod_IdAndType_Id(periodId, typeId)) {
            throw new CatalogConflictException(CatalogConflictException.Reason.RATE_DUPLICATE);
        }
        rates.save(rate);
        entityManager.flush();
        audit.recordRate(rate.getId(), Operation.CREATE, null, snapshot(rate), ActorOrigin.LOCAL_USER);
        entityManager.flush();
        return read(rate.getId());
    }

    @Transactional(readOnly = true)
    public List<RateView> list() {
        return rates.readCatalog(null).stream().map(HourlyRateService::view).toList();
    }

    @Transactional(readOnly = true)
    public RateView get(Long id) {
        requireId(id);
        return read(id);
    }

    @Transactional(readOnly = true)
    public List<TypeView> listTypes() {
        return types.findAllByOrderByIdAsc().stream()
                .map(type -> new TypeView(type.getId(), type.getCode(), type.getName())).toList();
    }

    public RateView correctAmount(Long id, Long expectedVersion, BigDecimal amountCop) {
        var rate = lock(id, expectedVersion);
        var before = snapshot(rate);
        if (rate.correctAmount(amountCop)) {
            writeAudit(rate, Operation.UPDATE, before);
        }
        return read(id);
    }

    public RateView deactivate(Long id, Long expectedVersion) {
        return changeState(id, expectedVersion, false);
    }

    public RateView reactivate(Long id, Long expectedVersion) {
        return changeState(id, expectedVersion, true);
    }

    private RateView changeState(Long id, Long expectedVersion, boolean active) {
        var rate = lock(id, expectedVersion);
        var before = snapshot(rate);
        if (active) {
            requireActive(rate.getPeriod());
            rate.reactivate();
        } else {
            rate.deactivate();
        }
        writeAudit(rate, active ? Operation.REACTIVATE : Operation.DEACTIVATE, before);
        return read(id);
    }

    private HourlyRate lock(Long id, Long expectedVersion) {
        requireId(id);
        // Read only the immutable association scalar before acquiring the period lock, never a rate entity.
        Long periodId = rates.findPeriodIdById(id).orElseThrow(() -> new EntityNotFoundException("Rate not found"));
        periods.findByIdForUpdate(periodId).orElseThrow(() -> new EntityNotFoundException("Period not found"));
        var rate = rates.findById(id).orElseThrow(() -> new EntityNotFoundException("Rate not found"));
        if (expectedVersion == null || !Objects.equals(expectedVersion, rate.getVersion())) {
            throw new OptimisticLockingFailureException("Rate version is missing or stale; reload the record");
        }
        return rate;
    }

    private void writeAudit(HourlyRate rate, Operation operation, RateState before) {
        entityManager.flush();
        audit.recordRate(rate.getId(), operation, before, snapshot(rate), ActorOrigin.LOCAL_USER);
        entityManager.flush();
    }

    private RateView read(Long id) {
        return rates.readCatalog(id).stream().findFirst().map(HourlyRateService::view)
                .orElseThrow(() -> new EntityNotFoundException("Rate not found"));
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Catalog identifier is required");
        }
    }

    private static void requireActive(TariffPeriod period) {
        if (!period.isActive()) {
            throw new CatalogConflictException(CatalogConflictException.Reason.PERIOD_INACTIVE);
        }
    }

    private static RateState snapshot(HourlyRate rate) {
        return new RateState(rate.getPeriod().getId(), rate.getType().getId(), rate.getAmountCop(), rate.isActive());
    }

    private static RateView view(RateRead row) {
        return new RateView(row.getId(), row.getPeriodId(), row.getPeriodLabel(), row.getTypeId(), row.getTypeName(),
                row.getAmountCop(), row.isActive(), row.isPeriodActive(), row.isAvailable(), row.getVersion());
    }

    public record TypeView(Short id, String code, String name) { }
    public record RateView(Long id, Long periodId, String periodLabel, Short typeId, String typeName,
                           BigDecimal amountCop, boolean active, boolean periodActive, boolean available, Long version) { }
}
