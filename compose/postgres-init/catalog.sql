-- User and database of catalog-service. Only this user can connect; it owns everything it creates through Flyway.
create role catalog login password 'catalog';   -- local demo only
create database catalog owner catalog;
revoke connect, temporary on database catalog from public;
