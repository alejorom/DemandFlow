package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.alejorom.demandflow.audit.persistence.CatalogAuditEvent;
import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.RequirementType;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import io.github.alejorom.demandflow.tariffs.persistence.HourlyRateRepository;
import io.github.alejorom.demandflow.tariffs.persistence.RequirementTypeRepository;
import io.github.alejorom.demandflow.tariffs.persistence.TariffPeriodRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static io.github.alejorom.demandflow.CatalogSchemaIT.execute;
import static io.github.alejorom.demandflow.CatalogSchemaIT.rejected;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T2/T3/T4/T8; runtime JPA mapping/repositories, not application use cases or audit. */
class CatalogPersistenceIT {
    @TempDir Path temporary;

    @Test
    void realMetamodelValidatesAndRuntimeSqlPersistsExactValuesWithoutReassignments() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                assertThat(context.containsBean("catalogPersistenceConfiguration")).isTrue();
                assertThat(context.containsBean("catalogProfileProbe")).isTrue();
                assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");
                assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
                var factory = context.getBean(EntityManagerFactory.class);
                assertThat(factory.getMetamodel().getEntities().stream().map(entity -> entity.getJavaType())
                        .collect(Collectors.toSet())).isEqualTo(Set.of(TariffPeriod.class, RequirementType.class, HourlyRate.class,
                                CatalogAuditEvent.class));
                for (String field : new String[] {"period", "type"}) {
                    var association = HourlyRate.class.getDeclaredField(field);
                    var mapping = association.getAnnotation(ManyToOne.class);
                    assertThat(mapping.fetch()).isEqualTo(FetchType.LAZY);
                    assertThat(mapping.optional()).isFalse();
                    assertThat(mapping.cascade()).isEmpty();
                    assertThat(association.getAnnotation(JoinColumn.class).updatable()).isFalse();
                }
                var periods = context.getBean(TariffPeriodRepository.class);
                var types = context.getBean(RequirementTypeRepository.class);
                var rates = context.getBean(HourlyRateRepository.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                Long rateId = tx.execute(status -> {
                    assertThat(types.findAllByOrderByIdAsc()).extracting(RequirementType::getCode)
                            .containsExactly("Soporte", "Feature");
                    assertThat(types.findById((short) 3)).isEmpty();
                    var period = periods.save(new TariffPeriod("\uD83D\uDE00".repeat(100),
                            LocalDate.of(2027,1,1), LocalDate.of(2027,12,31)));
                    assertThat(period.getId()).isNotNull();
                    assertThat(period.getVersion()).isZero();
                    assertThat(rates.findAllByOrderByIdAsc()).isEmpty();
                    var rate = rates.save(new HourlyRate(period, types.findById((short) 1).orElseThrow(),
                            HourlyRate.parseAmount("0.01")));
                    assertThat(rate.getId()).isNotNull();
                    assertThat(rate.getVersion()).isZero();
                    return rate.getId();
                });
                Long periodId = tx.execute(status -> {
                    var rate = rates.findById(rateId).orElseThrow();
                    assertThat(Hibernate.isInitialized(rate.getPeriod())).isFalse();
                    assertThat(Hibernate.isInitialized(rate.getType())).isFalse();
                    assertThat(rate.getAmountCop()).isEqualTo(new BigDecimal("0.01"));
                    assertThat(rates.existsByPeriod_IdAndType_Id(rate.getPeriod().getId(), (short) 1)).isTrue();
                    assertThat(rates.existsByPeriod_IdAndType_Id(rate.getPeriod().getId(), (short) 2)).isFalse();
                    Long id = rates.findPeriodIdById(rateId).orElseThrow();
                    assertThat(rates.findPeriodIdById(Long.MAX_VALUE)).isEmpty();
                    assertThat(rate.correctAmount(HourlyRate.parseAmount("9999999999999.99"))).isTrue();
                    var period = periods.findById(id).orElseThrow();
                    assertThat(period.getLabel()).isEqualTo("\uD83D\uDE00".repeat(100));
                    period.edit("Correccion", period.getStartDate(), period.getEndDate());
                    period.deactivate();
                    return id;
                });
                try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(runtime, "select current_user")).isEqualTo("demandflow_app");
                    assertThat(scalar(runtime, "select amount_cop || ':' || version from demandflow.hourly_rate"))
                            .isEqualTo("9999999999999.99:1");
                    assertThat(scalar(runtime, "select active || ':' || version from demandflow.tariff_period"))
                            .isEqualTo("false:1");
                    execute(runtime, "update demandflow.hourly_rate set active=false where id=" + rateId);
                }
                tx.executeWithoutResult(status -> {
                    var rate = rates.findById(rateId).orElseThrow();
                    assertThat(rate.isActive()).isFalse();
                    assertThat(rates.findAllByOrderByIdAsc()).extracting(HourlyRate::getId).containsExactly(rateId);
                    assertThat(periods.findAllByOrderByIdAsc()).extracting(TariffPeriod::isActive).containsExactly(false);
                    assertThat(rate.getPeriod().getId()).isEqualTo(periodId);
                    assertThat(rate.getType().getId()).isEqualTo((short) 1);
                    assertThat(rate.correctAmount(new BigDecimal("9999999999999.99"))).isFalse();
                    var manager = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
                    manager.flush();
                    assertThat(rate.getVersion()).isEqualTo(1L);
                    assertThat(periods.overlaps(LocalDate.of(2027,6,1), LocalDate.of(2027,6,2), null)).isTrue();
                    assertThat(periods.overlaps(LocalDate.of(2027,1,1), LocalDate.of(2027,12,31), periodId)).isFalse();
                    assertThat(periods.overlaps(LocalDate.of(2027,12,31), LocalDate.of(2028,1,1), null)).isTrue();
                    assertThat(periods.overlaps(LocalDate.of(2028,1,1), LocalDate.of(2028,1,1), null)).isFalse();
                });
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    assertThat(scalar(migrator, "select string_agg(version,',' order by installed_rank) "
                            + "from infra.flyway_schema_history where success")).isEqualTo("1,2,3");
                    assertThat(scalar(migrator, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("0");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void periodRepositoryTakesRealRowLockWithoutIncrementingVersion() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodRepository.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                Long id = tx.execute(status -> periods.save(new TariffPeriod("Lock",
                        LocalDate.of(2027,1,1), LocalDate.of(2027,1,1))).getId());
                tx.executeWithoutResult(status -> {
                    var locked = periods.findByIdForUpdate(id).orElseThrow();
                    assertThat(locked.getVersion()).isZero();
                    try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                        independent.setAutoCommit(false);
                        rejected(independent, "55P03", "select id from demandflow.tariff_period where id=" + id + " for update nowait");
                        independent.rollback();
                    } catch (Exception failure) {
                        throw new AssertionError("Independent lock verification failed", failure);
                    }
                    assertThat(periods.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
                });
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    independent.setAutoCommit(false);
                    execute(independent, "select id from demandflow.tariff_period where id=" + id + " for update nowait");
                    assertThat(scalar(independent, "select version from demandflow.tariff_period where id=" + id)).isEqualTo("0");
                    independent.rollback();
                }
            }
        }
    }

    @Test
    void isoDateBoundariesRoundTripAndInclusiveRangeUsesRepresentableSuccessor() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodRepository.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                // Keep the original failing boundary as a driver regression, without inserting infinity.
                try (var runtime = connect(port, "demandflow_app", secrets.runtime);
                     var probe = runtime.prepareStatement("select ?::date::text, isfinite(?::date)")) {
                    assertThat(scalar(runtime, "select isfinite(date '4714-11-24 BC')")).isEqualTo("t");
                    for (LocalDate unsupported : new LocalDate[] {LocalDate.of(-4713,11,24), LocalDate.of(-4713,12,31)}) {
                        probe.setObject(1, unsupported);
                        probe.setObject(2, unsupported);
                        try (var rows = probe.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getString(1)).isEqualTo("-infinity");
                            assertThat(rows.getBoolean(2)).isFalse();
                        }
                        assertThrows(IllegalArgumentException.class, () -> new TariffPeriod("Fuera", unsupported, unsupported));
                    }
                }
                for (LocalDate day : new LocalDate[] {LocalDate.of(-4712,1,1), LocalDate.of(1582,10,10),
                        LocalDate.of(5874897,12,30)}) {
                    Long id = tx.execute(status -> periods.save(new TariffPeriod("Limite", day, day)).getId());
                    tx.executeWithoutResult(status -> {
                        var actual = periods.findById(id).orElseThrow();
                        assertThat(actual.getStartDate()).isEqualTo(day);
                        assertThat(actual.getEndDate()).isEqualTo(day);
                    });
                }
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    rejected(migrator, "22008", "insert into demandflow.tariff_period(label,start_date,end_date) "
                            + "values ('Fuera','5874897-12-31','5874897-12-31')");
                }
                assertThrows(IllegalArgumentException.class, () -> new TariffPeriod("Fuera",
                        LocalDate.of(5874897,12,31), LocalDate.of(5874897,12,31)));
            }
        }
    }
}
