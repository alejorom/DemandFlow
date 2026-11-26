package io.github.alejorom.demandflow.tariffs.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface HourlyRateRepository extends Repository<HourlyRate, Long> {
    HourlyRate save(HourlyRate rate);
    Optional<HourlyRate> findById(Long id);
    List<HourlyRate> findAllByOrderByIdAsc();
    boolean existsByPeriod_IdAndType_Id(Long periodId, Short typeId);

    @Query("select r.period.id from HourlyRate r where r.id = :id")
    Optional<Long> findPeriodIdById(@Param("id") Long id);

    /** One statement observes both states, including inactive records; null id selects the whole catalog. */
    @Query("select r.id as id, p.id as periodId, p.label as periodLabel, t.id as typeId, t.name as typeName, "
            + "r.amountCop as amountCop, r.active as active, p.active as periodActive, r.version as version, "
            + "case when r.active = true and p.active = true then true else false end as available "
            + "from HourlyRate r join r.period p join r.type t where (:id is null or r.id = :id) order by r.id")
    List<RateRead> readCatalog(@Param("id") Long id);

    interface RateRead {
        Long getId();
        Long getPeriodId();
        String getPeriodLabel();
        Short getTypeId();
        String getTypeName();
        BigDecimal getAmountCop();
        boolean isActive();
        boolean isPeriodActive();
        boolean isAvailable();
        Long getVersion();
    }
}
