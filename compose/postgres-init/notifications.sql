-- User and database of notifications-service. Only this user can connect; it owns everything it creates through Flyway.
create role notifications login password 'notifications';   -- local demo only
create database notifications owner notifications;
revoke connect, temporary on database notifications from public;
