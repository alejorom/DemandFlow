package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PostgreSQL constraints of T2; application validation and concurrency are later groups. */
class CatalogSchemaIT {
    @TempDir Path temporary;

    static Path catalogMigrations(Path directory, int through) throws Exception {
        technicalV1(directory);
        Files.copy(Path.of("src/main/resources/db/migration/V2__create_tariff_catalog.sql"),
                directory.resolve("V2__create_tariff_catalog.sql"));
        if (through >= 3) {
            Files.copy(Path.of("src/main/resources/db/migration/V3__create_catalog_audit.sql"),
                    directory.resolve("V3__create_catalog_audit.sql"));
        }
        return directory;
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static void rejected(Connection connection, String state, String sql) {
        SQLException failure = assertThrows(SQLException.class, () -> execute(connection, sql));
        assertThat(failure.getSQLState()).isEqualTo(state);
    }

    static long period(Connection connection, String start, String end, boolean active) throws SQLException {
        try (var statement = connection.prepareStatement("insert into demandflow.tariff_period "
                + "(label,start_date,end_date,active) values (?,?::date,?::date,?) returning id")) {
            statement.setString(1, "Periodo repetible");
            statement.setString(2, start);
            statement.setString(3, end);
            statement.setBoolean(4, active);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    @Test
    void physicalColumnsTypesDefaultsKeysAndIndexesMatchT2() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 2)).migrate();
            try (var connection = connect(port, "demandflow_migrator", secrets.migrator)) {
                assertThat(scalar(connection, "select string_agg(tablename,',' order by tablename) "
                        + "from pg_tables where schemaname='demandflow'"))
                        .isEqualTo("hourly_rate,requirement_type,tariff_period");
                assertThat(scalar(connection, "select string_agg(id || ':' || code || ':' || name,',' order by id) "
                        + "from demandflow.requirement_type"))
                        .isEqualTo("1:Soporte:Soporte,2:Feature:Feature");
                assertThat(scalar(connection, "select count(*) from demandflow.hourly_rate")).isEqualTo("0");
                assertThat(scalar(connection, "select numeric_precision || ':' || numeric_scale from "
                        + "information_schema.columns where table_schema='demandflow' "
                        + "and table_name='hourly_rate' and column_name='amount_cop'")) .isEqualTo("15:2");
                assertThat(scalar(connection, "select count(*) from information_schema.columns "
                        + "where table_schema='demandflow' and is_nullable='YES'")).isEqualTo("0");
                assertThat(scalar(connection, "select count(*) from information_schema.columns where "
                        + "table_schema='demandflow' and is_identity='YES'")).isEqualTo("2");
                assertThat(scalar(connection, "select count(*) from pg_constraint c join pg_namespace n "
                        + "on n.oid=c.connamespace where n.nspname='demandflow' and contype='f' "
                        + "and confdeltype='a' and confupdtype='a'")).isEqualTo("2");
                assertThat(scalar(connection, "select count(*) from pg_indexes where schemaname='demandflow'"))
                        .isEqualTo("7");
                assertThat(scalar(connection, "select indexdef from pg_indexes where "
                        + "schemaname='demandflow' and indexname='ex_tariff_period_dates'"))
                        .contains("USING gist", "daterange").doesNotContain(" WHERE ");
                long id = period(connection, "2027-01-01", "2027-01-01", true);
                assertThat(scalar(connection, "select active || ':' || version from demandflow.tariff_period "
                        + "where id=" + id)).isEqualTo("true:0");
                rejected(connection, "23514", "insert into demandflow.requirement_type values (3,'Otro','Otro')");
                rejected(connection, "23514", "update demandflow.requirement_type set name='Feature' where id=1");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void allPeriodStatesReserveInclusiveDatesAndAllowOnlyNonOverlappingIntervals() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 2)).migrate();
            try (var connection = connect(port, "demandflow_migrator", secrets.migrator)) {
                long first = period(connection, "2027-06-01", "2027-06-30", false);
                for (String[] range : new String[][] {{"2027-06-01","2027-06-30"},
                        {"2027-06-10","2027-06-20"}, {"2027-05-01","2027-07-31"},
                        {"2027-05-01","2027-06-10"}, {"2027-06-20","2027-07-31"},
                        {"2027-06-30","2027-07-01"}}) {
                    SQLException error = assertThrows(SQLException.class,
                            () -> period(connection, range[0], range[1], true));
                    assertThat(error.getSQLState()).isEqualTo("23P01");
                }
                long adjacent = period(connection, "2027-07-01", "2027-07-01", true);
                execute(connection, "update demandflow.tariff_period set label='Etiqueta' where id=" + first);
                rejected(connection, "23P01", "update demandflow.tariff_period set start_date='2027-06-30' "
                        + "where id=" + adjacent);
                for (String sql : new String[] {
                        "update demandflow.tariff_period set start_date='2027-07-01' where id=" + first,
                        "update demandflow.tariff_period set end_date='infinity' where id=" + adjacent,
                        "update demandflow.tariff_period set label='   ' where id=" + first,
                        "update demandflow.tariff_period set version=-1 where id=" + first}) {
                    rejected(connection, "23514", sql);
                }
                rejected(connection, "23502", "update demandflow.tariff_period set end_date=null where id=" + first);
                rejected(connection, "22001", "update demandflow.tariff_period set label=repeat('x',101) where id=" + first);
                execute(connection, "update demandflow.tariff_period set label=repeat('x',100) where id=" + first);
                period(connection, "2028-01-01", "2028-12-31", true);
                period(connection, "2029-01-01", "2029-06-30", false);
                period(connection, "2030-02-01", "2030-02-10", true);
            }
        }
    }

    @Test
    void ratesKeepGlobalCombinationExactMoneyAndReferentialIntegrity() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 2)).migrate();
            try (var connection = connect(port, "demandflow_migrator", secrets.migrator)) {
                long id = period(connection, "2027-01-01", "2027-12-31", true);
                String insert = "insert into demandflow.hourly_rate(period_id,type_id,amount_cop) values (" + id + ",1,";
                for (String value : new String[] {"0", "-0.01", "'NaN'"}) {
                    rejected(connection, "23514", insert + value + ")");
                }
                for (String value : new String[] {"10000000000000.00", "'Infinity'", "'-Infinity'"}) {
                    rejected(connection, "22003", insert + value + ")");
                }
                rejected(connection, "23502", insert + "null)");
                execute(connection, insert + "0.01)");
                assertThat(scalar(connection, "select amount_cop from demandflow.hourly_rate")).isEqualTo("0.01");
                rejected(connection, "23505", insert + "1.00)");
                execute(connection, "update demandflow.hourly_rate set active=false");
                rejected(connection, "23505", insert + "1.00)");
                execute(connection, "insert into demandflow.hourly_rate(period_id,type_id,amount_cop) values ("
                        + id + ",2,9999999999999.99)");
                assertThat(scalar(connection, "select amount_cop from demandflow.hourly_rate where type_id=2"))
                        .isEqualTo("9999999999999.99");
                rejected(connection, "23503", "delete from demandflow.tariff_period where id=" + id);
                rejected(connection, "23503", "delete from demandflow.requirement_type where id=1");
                rejected(connection, "23503", "insert into demandflow.hourly_rate(period_id,type_id,amount_cop) "
                        + "values (99999,1,1)");
                rejected(connection, "23503", "insert into demandflow.hourly_rate(period_id,type_id,amount_cop) "
                        + "values (" + id + ",3,1)");
                rejected(connection, "23514", "update demandflow.hourly_rate set version=-1");
            }
        }
    }
}
