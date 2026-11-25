CREATE SCHEMA demandflow AUTHORIZATION demandflow_migrator;
REVOKE ALL ON SCHEMA demandflow FROM PUBLIC;
GRANT USAGE ON SCHEMA demandflow TO demandflow_app;
