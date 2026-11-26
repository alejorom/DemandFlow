package io.github.alejorom.demandflow.tariffs.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TariffPeriodRepository extends Repository<TariffPeriod, Long> {
    TariffPeriod save(TariffPeriod period);
    Optional<TariffPeriod> findById(Long id);
    List<TariffPeriod> findAllByOrderByIdAsc();

    @Query("select (count(p) > 0) from TariffPeriod p where (:excludedId is null or p.id <> :excludedId) "
            + "and p.startDate <= :endDate and p.endDate >= :startDate")
    boolean overlaps(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                     @Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TariffPeriod p where p.id = :id")
    Optional<TariffPeriod> findByIdForUpdate(@Param("id") Long id);
}
