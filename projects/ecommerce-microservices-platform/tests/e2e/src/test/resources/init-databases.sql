-- TASK-MONO-195: create the two service databases inside one Postgres container.
-- The container superuser (e2e) owns both; each service's Flyway migrations build
-- its own schema on boot (ddl-auto=validate). Runs once at container init
-- (/docker-entrypoint-initdb.d), after the default 'postgres' DB already exists.
CREATE DATABASE order_db;
CREATE DATABASE shipping_db;
