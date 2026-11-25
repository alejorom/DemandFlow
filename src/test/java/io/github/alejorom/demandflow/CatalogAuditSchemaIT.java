package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static io.github.alejorom.demandflow.CatalogSchemaIT.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Physical audit and privileges only; no application writer or business use cases. */
class CatalogAuditSchemaIT {
    @TempDir Path temporary;

    static String event(String mutation, String entity, String period, String rate,
                        String operation, String field, String before, String after, String origin) {
        // All arguments are test-owned SQL literals, never application or browser inputs.
        return "insert into demandflow.catalog_audit_event (mutation_id,entity_type,period_id,rate_id,"
                + "operation,field,before_value,after_value,occurred_at,actor_origin) values ('" + mutation
                + "','" + entity + "'," + period + "," + rate + ",'" + operation + "','" + field + "',"
                + before + "," + after + ",'2027-01-01T00:00:00.123456Z','" + origin + "')";
    }

    static String periodEvent(long period) {
        return event(UUID.randomUUID().toString(), "TARIFF_PERIOD", Long.toString(period), "null",
                "CREATE", "label", "null", "'Periodo'", "LOCAL_USER");
    }

    static String columns(Connection connection, String table) throws SQLException {
        try (var query = connection.prepareStatement("select string_agg(a.attname || ':' || "
                + "format_type(a.atttypid,a.atttypmod),',' order by a.attnum) from pg_attribute a "
                + "join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace "
                + "where n.nspname='demandflow' and c.relname=? and a.attnum>0 and not a.attisdropped")) {
            query.setString(1, table);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    @Test
    void completePhysicalModelHasOnlyApprovedColumnsIndexesAndOwners() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 3)).migrate();
            try (var connection = connect(port, "demandflow_migrator", secrets.migrator)) {
                assertThat(columns(connection, "tariff_period")).isEqualTo("id:bigint,label:character varying(100),"
                        + "start_date:date,end_date:date,active:boolean,version:bigint");
                assertThat(columns(connection, "requirement_type"))
                        .isEqualTo("id:smallint,code:character varying(7),name:character varying(7)");
                assertThat(columns(connection, "hourly_rate")).isEqualTo("id:bigint,period_id:bigint,type_id:smallint,"
                        + "amount_cop:numeric(15,2),active:boolean,version:bigint");
                assertThat(columns(connection, "catalog_audit_event")).isEqualTo("id:bigint,mutation_id:uuid,"
                        + "entity_type:character varying(16),period_id:bigint,rate_id:bigint,"
                        + "operation:character varying(12),field:character varying(32),before_value:text,"
                        + "after_value:text,occurred_at:timestamp(6) with time zone,actor_origin:character varying(10)");
                assertThat(scalar(connection, "select count(*) from pg_tables where schemaname='demandflow' "
                        + "and tableowner='demandflow_migrator'")).isEqualTo("4");
                assertThat(scalar(connection, "select count(*) from pg_indexes where schemaname='demandflow'"))
                        .isEqualTo("11");
                assertThat(scalar(connection, "select string_agg(column_name,',' order by ordinal_position) from "
                        + "information_schema.columns where table_schema='demandflow' "
                        + "and table_name='catalog_audit_event' and is_nullable='YES'"))
                        .isEqualTo("period_id,rate_id,before_value");
                assertThat(scalar(connection, "select count(*) from pg_constraint c join pg_namespace n "
                        + "on n.oid=c.connamespace where n.nspname='demandflow' and contype='f' "
                        + "and confdeltype='a' and confupdtype='a'")).isEqualTo("4");
            }
        }
    }

    @Test
    void runtimeCanWriteOnlyApprovedColumnsAndCannotAlterHistoryOrFixedTypes() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 3)).migrate();
            try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                long id = period(runtime, "2027-01-01", "2027-12-31", true);
                execute(runtime, "insert into demandflow.hourly_rate(period_id,type_id,amount_cop,active,version) "
                        + "values (" + id + ",1,0.01,true,0)");
                long rate = Long.parseLong(scalar(runtime, "select id from demandflow.hourly_rate"));
                execute(runtime, periodEvent(id));
                execute(runtime, event(UUID.randomUUID().toString(), "HOURLY_RATE", "null", Long.toString(rate),
                        "CREATE", "amount_cop", "null", "'0.01'", "SYSTEM"));
                execute(runtime, "update demandflow.tariff_period set label='Correccion',version=version+1 where id=" + id);
                execute(runtime, "update demandflow.hourly_rate set amount_cop=1.25,active=false,version=version+1");
                runtime.setAutoCommit(false);
                execute(runtime, "select id from demandflow.tariff_period where id=" + id + " for update");
                runtime.rollback();
                runtime.setAutoCommit(true);
                for (String sql : new String[] {
                        "update demandflow.hourly_rate set period_id=" + id,
                        "update demandflow.hourly_rate set type_id=2",
                        "update demandflow.hourly_rate set id=id",
                        "update demandflow.tariff_period set id=id",
                        "insert into demandflow.tariff_period(id,label,start_date,end_date) values (999,'x','2030-01-01','2030-01-01')",
                        "insert into demandflow.requirement_type values (3,'Otro','Otro')",
                        "update demandflow.requirement_type set name=name",
                        "delete from demandflow.requirement_type",
                        "delete from demandflow.hourly_rate",
                        "delete from demandflow.tariff_period",
                        "update demandflow.catalog_audit_event set after_value='Cambiado'",
                        "delete from demandflow.catalog_audit_event",
                        "truncate demandflow.catalog_audit_event",
                        "select * from infra.flyway_schema_history",
                        "create table demandflow.forbidden_runtime_table(id int)",
                        "select setval('demandflow.catalog_audit_event_id_seq',999)"}) {
                    rejected(runtime, "42501", sql);
                }
                assertThat(scalar(runtime, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("2");
                assertThat(scalar(runtime, "select count(*) from demandflow.requirement_type")).isEqualTo("2");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void auditChecksRejectInvalidReferencesFieldsNoChangesAndDuplicateMutationFields() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, catalogMigrations(temporary.resolve("migrations"), 3)).migrate();
            try (var connection = connect(port, "demandflow_migrator", secrets.migrator)) {
                long id = period(connection, "2027-01-01", "2027-12-31", true);
                String key = UUID.randomUUID().toString();
                String valid = event(key, "TARIFF_PERIOD", Long.toString(id), "null", "UPDATE", "label",
                        "'Anterior'", "'Posterior'", "LOCAL_USER");
                execute(connection, valid);
                rejected(connection, "23505", valid);
                for (String sql : new String[] {
                        event(key,"OTHER",Long.toString(id),"null","UPDATE","label","'A'","'B'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD","null","null","UPDATE","label","'A'","'B'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"1","UPDATE","label","'A'","'B'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"null","REMOVE","label","'A'","'B'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"null","UPDATE","amount_cop","'A'","'B'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"null","UPDATE","active","'true'","'true'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"null","UPDATE","active","null","'true'","LOCAL_USER"),
                        event(key,"TARIFF_PERIOD",Long.toString(id),"null","CREATE","active","null","'true'","UNKNOWN")}) {
                    rejected(connection, "23514", sql);
                }
                rejected(connection, "23502", event(key,"TARIFF_PERIOD",Long.toString(id),"null",
                        "CREATE","active","null","null","SYSTEM"));
                rejected(connection, "23503", periodEvent(99999));
                rejected(connection, "23503", event(UUID.randomUUID().toString(),"HOURLY_RATE","null","99999",
                        "CREATE","amount_cop","null","'1.00'","SYSTEM"));
                rejected(connection, "23503", "delete from demandflow.tariff_period where id=" + id);
                execute(connection, "set time zone 'America/Bogota'");
                assertThat(scalar(connection, "select to_char(occurred_at at time zone 'UTC',"
                        + "'YYYY-MM-DD HH24:MI:SS.US') from demandflow.catalog_audit_event"))
                        .isEqualTo("2027-01-01 00:00:00.123456");
            }
        }
    }
}
