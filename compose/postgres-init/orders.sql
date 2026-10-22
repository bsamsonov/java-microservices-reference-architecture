-- User and database of orders-service. Only this user can connect; it owns everything it creates through Flyway.
create role orders login password 'orders';   -- local demo only
create database orders owner orders;
revoke connect, temporary on database orders from public;
