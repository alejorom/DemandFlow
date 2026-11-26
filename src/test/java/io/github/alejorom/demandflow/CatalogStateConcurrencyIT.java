package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.OptimisticLockingFailureException;

import static io.github.alejorom.demandflow.CatalogConcurrencySupport.*;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** T4; TP6.S2, HR7.S1/S2: actual waiting transactions must reload state/version after the lock. */
class CatalogStateConcurrencyIT {
    @TempDir Path temporary;

    @ParameterizedTest
    @CsvSource({"period,edit,edit", "period,edit,deactivate", "period,deactivate,edit", "period,reactivate,deactivate",
            "rate,edit,edit", "rate,edit,deactivate", "rate,deactivate,edit", "rate,reactivate,deactivate"})
    void staleEditOrTransitionCannotOverwriteWinnerAfterWaitingForLock(String entity, String first, String second) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var p = periods.create("Base", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var r = rates.create(p.id(), (short) 1, new BigDecimal("12.30"));
                if (first.equals("reactivate")) {
                    if (entity.equals("period")) { periods.deactivate(p.id(), p.version()); }
                    else { rates.deactivate(r.id(), r.version()); }
                }
                var initialPeriod = periods.get(p.id());
                var initialRate = rates.get(r.id());
                long events = eventCount(port, secrets);
                TransactionBody a = () -> entity.equals("period")
                        ? periodCommand(periods, initialPeriod, first) : rateCommand(rates, initialRate, first);
                TransactionBody b = () -> entity.equals("period")
                        ? periodCommand(periods, initialPeriod, second) : rateCommand(rates, initialRate, second);
                var result = commands(context, port, secrets, a, b);
                assertThat(result.first().failure()).isNull();
                assertThat(result.second().failure()).isInstanceOf(OptimisticLockingFailureException.class);
                if (entity.equals("period")) {
                    var winner = (TariffPeriodService.PeriodView) result.first().value();
                    assertThat(periods.get(p.id())).isEqualTo(winner);
                    assertThat(winner.version()).isEqualTo(initialPeriod.version() + 1);
                    assertThat(rates.get(r.id()).active()).isEqualTo(initialRate.active());
                    assertThat(rates.get(r.id()).version()).isEqualTo(initialRate.version());
                } else {
                    var winner = (HourlyRateService.RateView) result.first().value();
                    assertThat(rates.get(r.id())).isEqualTo(winner);
                    assertThat(winner.version()).isEqualTo(initialRate.version() + 1);
                    assertThat(periods.get(p.id())).isEqualTo(initialPeriod);
                }
                // A fresh physical connection after both futures completed: one effective change, no loser event.
                assertThat(eventCount(port, secrets)).isEqualTo(events + 1);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    String before = first.equals("edit") ? (entity.equals("period") ? "Base" : "12.30")
                            : Boolean.toString(first.equals("deactivate"));
                    String after = first.equals("edit") ? (entity.equals("period") ? "Edited" : "99.99")
                            : Boolean.toString(first.equals("reactivate"));
                    assertThat(scalar(independent, "select before_value || ':' || after_value from demandflow.catalog_audit_event order by id desc limit 1"))
                            .isEqualTo(before + ":" + after);
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @ParameterizedTest
    @CsvSource({"create,period-first", "reactivate,period-first", "create,rate-first", "reactivate,rate-first"})
    void rateCreationOrReactivationAndPeriodDeactivationRespectBothCommitOrders(String action, String order) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var period = periods.create("Ordering", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                HourlyRateService.RateView inactive = null;
                if (action.equals("reactivate")) {
                    var created = rates.create(period.id(), (short) 1, new BigDecimal("12.30"));
                    inactive = rates.deactivate(created.id(), created.version());
                }
                var initial = inactive;
                long events = eventCount(port, secrets);
                TransactionBody rate = () -> action.equals("create") ? rates.create(period.id(), (short) 1, new BigDecimal("12.30"))
                        : rates.reactivate(initial.id(), initial.version());
                TransactionBody deactivate = () -> periods.deactivate(period.id(), period.version());
                boolean periodFirst = order.equals("period-first");
                var result = commands(context, port, secrets, periodFirst ? deactivate : rate, periodFirst ? rate : deactivate);
                assertThat(result.first().failure()).isNull();
                var finalPeriod = periods.get(period.id());
                assertThat(finalPeriod.active()).isFalse();
                assertThat(finalPeriod.version()).isEqualTo(1L);
                if (periodFirst) {
                    assertThat(result.second().failure()).isInstanceOf(IllegalStateException.class).hasMessageContaining("active period");
                    if (action.equals("create")) { assertThat(rates.list()).isEmpty(); }
                    else {
                        var finalRate = rates.get(initial.id());
                        assertThat(finalRate.active()).isFalse();
                        assertThat(finalRate.available()).isFalse();
                        assertThat(finalRate.version()).isEqualTo(initial.version());
                        assertThat(finalRate.amountCop()).isEqualTo(initial.amountCop());
                    }
                    assertThat(eventCount(port, secrets)).isEqualTo(events + 1);
                } else {
                    assertThat(result.second().failure()).isNull();
                    var confirmed = (HourlyRateService.RateView) result.first().value();
                    var finalRate = rates.get(confirmed.id());
                    assertThat(finalRate.active()).isTrue();
                    assertThat(finalRate.periodActive()).isFalse();
                    assertThat(finalRate.available()).isFalse();
                    assertThat(finalRate.version()).isEqualTo(action.equals("create") ? 0L : initial.version() + 1);
                    assertThat(finalRate.periodId()).isEqualTo(period.id());
                    assertThat(finalRate.typeId()).isEqualTo((short) 1);
                    assertThat(finalRate.amountCop()).isEqualTo(new BigDecimal("12.30"));
                    assertThat(eventCount(port, secrets)).isEqualTo(events + (action.equals("create") ? 5 : 2));
                }
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select entity_type || ':' || operation from demandflow.catalog_audit_event order by id desc limit 1"))
                            .isEqualTo("TARIFF_PERIOD:DEACTIVATE");
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate r join demandflow.tariff_period p "
                            + "on p.id=r.period_id where r.active and p.active")).isEqualTo("0");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static Object periodCommand(TariffPeriodService service, TariffPeriodService.PeriodView p, String command) {
        return switch (command) {
            case "edit" -> service.edit(p.id(), p.version(), "Edited", p.startDate(), p.endDate());
            case "deactivate" -> service.deactivate(p.id(), p.version());
            case "reactivate" -> service.reactivate(p.id(), p.version());
            default -> throw new AssertionError(command);
        };
    }

    private static Object rateCommand(HourlyRateService service, HourlyRateService.RateView r, String command) {
        return switch (command) {
            case "edit" -> service.correctAmount(r.id(), r.version(), new BigDecimal("99.99"));
            case "deactivate" -> service.deactivate(r.id(), r.version());
            case "reactivate" -> service.reactivate(r.id(), r.version());
            default -> throw new AssertionError(command);
        };
    }

    private static long eventCount(int port, Secrets secrets) throws Exception {
        try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
            return Long.parseLong(scalar(independent, "select count(*) from demandflow.catalog_audit_event"));
        }
    }
}
