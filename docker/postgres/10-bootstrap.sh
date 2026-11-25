#!/usr/bin/env bash
# May be sourced by the official entrypoint on Windows bind mounts.
(
    set -euo pipefail

    for secret_path in /run/secrets/postgres_migrator_password /run/secrets/postgres_runtime_password; do
        if [[ ! -r "$secret_path" || ! -s "$secret_path" ]]; then
            printf '%s\n' 'DemandFlow bootstrap: required role secret missing or empty.' >&2
            exit 1
        fi
    done
    migrator_password=$(cat /run/secrets/postgres_migrator_password)
    runtime_password=$(cat /run/secrets/postgres_runtime_password)
    if [[ -z "$migrator_password" || -z "$runtime_password" ]]; then
        printf '%s\n' 'DemandFlow bootstrap: required role secret empty.' >&2
        exit 1
    fi

    # Secrets travel only in this child's environment, never in argv or expanded files.
    if DF_BOOTSTRAP_MIGRATOR_PASSWORD="$migrator_password" \
       DF_BOOTSTRAP_RUNTIME_PASSWORD="$runtime_password" \
       PGOPTIONS='-c log_statement=none -c log_min_error_statement=panic -c log_error_verbosity=terse' \
       psql -X --no-password --username postgres --dbname demandflow \
           --set=ON_ERROR_STOP=1 --set=ECHO=none >/dev/null 2>&1 <<'SQL'
\set SHOW_CONTEXT never
\getenv migrator_password DF_BOOTSTRAP_MIGRATOR_PASSWORD
\getenv runtime_password DF_BOOTSTRAP_RUNTIME_PASSWORD
BEGIN;
SET password_encryption = 'scram-sha-256';
CREATE ROLE demandflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'migrator_password';
CREATE ROLE demandflow_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'runtime_password';
REVOKE CONNECT, TEMPORARY ON DATABASE demandflow FROM PUBLIC;
GRANT CONNECT ON DATABASE demandflow TO demandflow_migrator, demandflow_app;
GRANT CREATE ON DATABASE demandflow TO demandflow_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA infra AUTHORIZATION demandflow_migrator;
REVOKE ALL ON SCHEMA infra FROM PUBLIC;
ALTER ROLE demandflow_migrator IN DATABASE demandflow SET statement_timeout = '15s';
ALTER ROLE demandflow_migrator IN DATABASE demandflow SET lock_timeout = '5s';
ALTER ROLE demandflow_app IN DATABASE demandflow SET statement_timeout = '15s';
ALTER ROLE demandflow_app IN DATABASE demandflow SET lock_timeout = '5s';
COMMIT;
SQL
    then
        printf '%s\n' 'DemandFlow bootstrap: roles and infrastructure schema initialized.'
    else
        bootstrap_status=$?
        printf '%s\n' 'DemandFlow bootstrap: role provisioning failed; inspect configuration without exposing secrets.' >&2
        exit "$bootstrap_status"
    fi
)
