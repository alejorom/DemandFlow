package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.RequirementType;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import io.github.alejorom.demandflow.tariffs.persistence.HourlyRateRepository;
import io.github.alejorom.demandflow.tariffs.persistence.RequirementTypeRepository;
import io.github.alejorom.demandflow.tariffs.persistence.TariffPeriodRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** T4: observable lock/load order and rejection before mutation, not merely annotations. */
class CatalogCommandTests {
    @ParameterizedTest
    @ValueSource(strings = {"correct", "deactivate", "reactivate"})
    void rateCommandsProjectThenLockThenLoadAndFlushBusinessBeforeAudit(String command) {
        var periods = mock(TariffPeriodRepository.class);
        var rates = mock(HourlyRateRepository.class);
        var audit = mock(CatalogAuditWriter.class);
        var em = mock(EntityManager.class);
        var period = new TariffPeriod("Periodo", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        var type = mock(RequirementType.class);
        ReflectionTestUtils.setField(period, "id", 7L);
        when(type.getId()).thenReturn((short) 1);
        var rate = new HourlyRate(period, type, new BigDecimal("12.30"));
        ReflectionTestUtils.setField(rate, "id", 9L);
        ReflectionTestUtils.setField(rate, "version", 0L);
        if (command.equals("reactivate")) { rate.deactivate(); }
        when(rates.findPeriodIdById(9L)).thenReturn(Optional.of(7L));
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(rates.findById(9L)).thenReturn(Optional.of(rate));
        when(rates.readCatalog(9L)).thenReturn(List.of(mock(HourlyRateRepository.RateRead.class)));
        var service = new HourlyRateService(rates, periods, mock(RequirementTypeRepository.class), audit);
        ReflectionTestUtils.setField(service, "entityManager", em);
        switch (command) {
            case "correct" -> service.correctAmount(9L, 0L, new BigDecimal("99.99"));
            case "deactivate" -> service.deactivate(9L, 0L);
            case "reactivate" -> service.reactivate(9L, 0L);
            default -> throw new AssertionError(command);
        }
        var order = inOrder(rates, periods, em, audit);
        order.verify(rates).findPeriodIdById(9L);
        order.verify(periods).findByIdForUpdate(7L);
        order.verify(rates).findById(9L);
        order.verify(em).flush();
        order.verify(audit).recordRate(eq(9L), any(), any(), any(), eq(CatalogAuditWriter.ActorOrigin.LOCAL_USER));
        order.verify(em).flush();
        order.verify(rates).readCatalog(9L);
        verify(periods, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(classes = {TariffPeriodService.class, HourlyRateService.class})
    void commandsUseReadCommittedAndPublicQueriesAreReadOnly(Class<?> service) {
        assertThat(service.getAnnotation(Transactional.class).isolation()).isEqualTo(Isolation.READ_COMMITTED);
        for (var method : service.getDeclaredMethods()) {
            if (List.of("list", "get", "listTypes").contains(method.getName())) {
                assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
            }
        }
    }
}
