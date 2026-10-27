WITH organization AS (
    INSERT INTO organizations (id, name, slug, time_zone, weekend_days, work_starts_at, created_at, updated_at)
    VALUES (gen_random_uuid(), 'Corridor Technologies', 'corridor', 'Africa/Cairo', '{5,6}', '09:00', now(), now())
    RETURNING id
)
INSERT INTO users (id, organization_id, email, display_name, role, status, password_hash, created_at, updated_at)
SELECT gen_random_uuid(), organization.id, seed.email, seed.display_name, seed.role, 'ACTIVE',
       '{argon2}$argon2id$v=19$m=16384,t=2,p=1$025H7wqkIUCkxmN/SPgn4w$eMugljXwH277k6lg6NGUf4TJx9srSjbiUzdNRNj5RzM',
       now(), now()
FROM organization,
     (VALUES ('admin@corridor.local', 'Muhammed Samer', 'ADMIN'),
             ('manager@corridor.local', 'Salma Ibrahim', 'MANAGER'),
             ('nour@corridor.local', 'Nour Hassan', 'EMPLOYEE'),
             ('omar@corridor.local', 'Omar Adel', 'EMPLOYEE')) AS seed (email, display_name, role);
