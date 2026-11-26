package io.github.alejorom.demandflow.tariffs.persistence;

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
}
