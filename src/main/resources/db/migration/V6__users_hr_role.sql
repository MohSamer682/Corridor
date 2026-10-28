ALTER TABLE users DROP CONSTRAINT ck_users_role;

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE'));
