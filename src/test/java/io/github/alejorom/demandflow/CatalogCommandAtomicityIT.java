package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.CatalogApplicationIT.databaseSnapshot;
import static io.github.alejorom.demandflow.CatalogSchemaIT.execute;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T4/T5, CA3/CA4: call the real service proxy directly, without a test transaction enclosing commands. */
class CatalogCommandAtomicityIT {
    @TempDir Path temporary;

    @Test
    void everyCommandRollsBackOnBusinessAuditAndCommitFailuresAndNeverReturnsPrematureSuccess() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var p = periods.create("Activa", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var q0 = periods.create("Inactiva", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
                var free = periods.create("Libre", LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31));
                var r = rates.create(p.id(), (short) 1, new BigDecimal("12.30"));
                var s0 = rates.create(p.id(), (short) 2, new BigDecimal("45.60"));
                var s = rates.deactivate(s0.id(), s0.version());
                var q = periods.deactivate(q0.id(), q0.version());
                List<FailureCase> commands = List.of(
                        new FailureCase("TARIFF_PERIOD", "CREATE", "label", () -> periods.create("Fallida", LocalDate.of(2035, 1, 1), LocalDate.of(2035, 12, 31))),
                        new FailureCase("TARIFF_PERIOD", "CREATE", "start_date", () -> periods.create("Fallida", LocalDate.of(2035, 1, 1), LocalDate.of(2035, 12, 31))),
                        new FailureCase("HOURLY_RATE", "CREATE", "period_id", () -> rates.create(free.id(), (short) 1, new BigDecimal("99.99"))),
                        new FailureCase("HOURLY_RATE", "CREATE", "type_id", () -> rates.create(free.id(), (short) 1, new BigDecimal("99.99"))),
                        new FailureCase("TARIFF_PERIOD", "UPDATE", "start_date", () -> periods.edit(p.id(), p.version(), "Editada fallida", LocalDate.of(2027, 2, 1), p.endDate())),
                        new FailureCase("TARIFF_PERIOD", "DEACTIVATE", "active", () -> periods.deactivate(p.id(), p.version())),
                        new FailureCase("TARIFF_PERIOD", "REACTIVATE", "active", () -> periods.reactivate(q.id(), q.version())),
                        new FailureCase("HOURLY_RATE", "UPDATE", "amount_cop", () -> rates.correctAmount(r.id(), r.version(), new BigDecimal("99.99"))),
                        new FailureCase("HOURLY_RATE", "DEACTIVATE", "active", () -> rates.deactivate(r.id(), r.version())),
                        new FailureCase("HOURLY_RATE", "REACTIVATE", "active", () -> rates.reactivate(s.id(), s.version())));
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    defineAuditFailure(migrator, "RETURN NEW;");
                    execute(migrator, "create trigger test_command_audit_failure before insert on demandflow.catalog_audit_event "
                            + "for each row execute function demandflow.test_command_audit_failure()");
                    for (var command : commands) {
                        // Only test-owned constants are substituted into fixture DDL. No production SQL/API is added.
                        defineAuditFailure(migrator, """
                                IF NEW.entity_type='%s' AND NEW.operation='%s' AND NEW.field='%s' THEN
                                    IF EXISTS (SELECT 1 FROM demandflow.catalog_audit_event WHERE mutation_id=NEW.mutation_id) THEN
                                        RAISE EXCEPTION 'isolated command middle audit failure' USING ERRCODE='P0001';
                                    ELSE
                                        RAISE EXCEPTION 'isolated command first audit failure' USING ERRCODE='P0001';
                                    END IF;
                                END IF;
                                RETURN NEW;
                                """.formatted(command.entity(), command.operation(), command.field()));
                        boolean middle = List.of("start_date", "type_id").contains(command.field());
                        verifyRollback(port, secrets, command.command(), middle
                                ? "isolated command middle audit failure" : "isolated command first audit failure");
                    }
                    defineAuditFailure(migrator, "RETURN NEW;");

                    execute(migrator, """
                            create function demandflow.test_command_business_failure() returns trigger language plpgsql as $$
                            begin
                                raise exception 'isolated command business failure' using errcode='P0001';
                            end $$
                            """);
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "create trigger test_command_business_failure before insert or update on demandflow."
                                + table + " for each row execute function demandflow.test_command_business_failure()");
                    }
                    // All eight command kinds fail at the database write, after application validation and before audit.
                    for (var command : commands.stream().filter(c -> !c.operation().equals("CREATE")
                            || List.of("label", "period_id").contains(c.field())).toList()) {
                        verifyRollback(port, secrets, command.command(), "isolated command business failure");
                    }
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "drop trigger test_command_business_failure on demandflow." + table);
                    }

                    // Deferred business constraints see already-inserted audit at commit, but roll everything back.
                    execute(migrator, """
                            create function demandflow.test_command_business_commit_failure() returns trigger language plpgsql as $$
                            begin
                                if exists (select 1 from demandflow.catalog_audit_event
                                        where (TG_TABLE_NAME='tariff_period' and period_id=NEW.id)
                                           or (TG_TABLE_NAME='hourly_rate' and rate_id=NEW.id)) then
                                    raise exception 'isolated business commit failure after audit' using errcode='P0001';
                                end if;
                                raise exception 'missing audit at commit' using errcode='P0002';
                            end $$
                            """);
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "create constraint trigger test_command_business_commit_failure after insert on demandflow."
                                + table + " deferrable initially deferred for each row execute function demandflow.test_command_business_commit_failure()");
                    }
                    for (int index : new int[]{0, 2}) {
                        verifyRollback(port, secrets, commands.get(index).command(), "isolated business commit failure after audit");
                    }
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "drop trigger test_command_business_commit_failure on demandflow." + table);
                    }

                    execute(migrator, """
                            create function demandflow.test_command_audit_commit_failure() returns trigger language plpgsql as $$
                            begin
                                if NEW.field='active' and NEW.operation='CREATE' then
                                    if (select count(*) from demandflow.catalog_audit_event where mutation_id=NEW.mutation_id) <> 4 then
                                        raise exception 'missing audit fields at commit' using errcode='P0002';
                                    end if;
                                    raise exception 'isolated audit commit failure with four rows' using errcode='P0001';
                                end if;
                                return NEW;
                            end $$
                            """);
                    execute(migrator, "create constraint trigger test_command_audit_commit_failure after insert on demandflow.catalog_audit_event "
                            + "deferrable initially deferred for each row execute function demandflow.test_command_audit_commit_failure()");
                    for (int index : new int[]{0, 2}) {
                        verifyRollback(port, secrets, commands.get(index).command(), "isolated audit commit failure with four rows");
                    }
                    execute(migrator, "drop trigger test_command_audit_commit_failure on demandflow.catalog_audit_event");
                }
                var successful = rates.correctAmount(r.id(), r.version(), new BigDecimal("99.99"));
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(successful.version()).isEqualTo(r.version() + 1);
                    assertThat(scalar(independent, "select amount_cop || ':' || version from demandflow.hourly_rate where type_id=1"))
                            .isEqualTo("99.99:1");
                    assertThat(scalar(independent, "select before_value || ':' || after_value from demandflow.catalog_audit_event "
                            + "where entity_type='HOURLY_RATE' and operation='UPDATE'"))
                            .isEqualTo("12.30:99.99");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static void defineAuditFailure(java.sql.Connection migrator, String body) throws Exception {
        execute(migrator, "create or replace function demandflow.test_command_audit_failure() returns trigger language plpgsql as $$ begin "
                + body + " end $$");
    }

    private static void verifyRollback(int port, Secrets secrets, Runnable command, String expectedMessage) throws Exception {
        var before = databaseSnapshot(port, secrets);
        // The exception must escape the real service proxy, including exceptions raised only during commit.
        var failure = assertThrows(RuntimeException.class, command::run);
        Throwable sql = failure;
        while (!(sql instanceof SQLException) && sql.getCause() != null) { sql = sql.getCause(); }
        assertThat(sql).isInstanceOf(SQLException.class).hasMessageContaining(expectedMessage);
        assertThat(((SQLException) sql).getSQLState()).isEqualTo("P0001");
        assertThat(databaseSnapshot(port, secrets)).isEqualTo(before);
    }

    private record FailureCase(String entity, String operation, String field, Runnable command) { }
}
