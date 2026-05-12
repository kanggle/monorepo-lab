-- Dev/standalone seed: three partners covering all partner_type values so
-- manual walk-throughs exercise the partner routes plus the supplier/customer
-- validation paths downstream (inbound ASN supplier check, outbound order
-- customer check).
-- Activated via spring.flyway.locations in application-dev.yml.
-- ON CONFLICT DO NOTHING keeps this idempotent across re-runs.
--
-- partner_code values are intentionally aligned with the inbound + outbound
-- V99__seed_dev_masterref.sql baseline (SUP-001 / CUST-001) so dev e2e flows
-- between master and the consumer services without code drift. BOTH-001 is
-- new — only emitted into the master read-model once a downstream consumer
-- pulls the partner.created event.

INSERT INTO partners (
    id, partner_code, name, partner_type,
    business_number, contact_name, contact_email, contact_phone, address,
    status, version, created_at, created_by, updated_at, updated_by
) VALUES
(
    '01910000-0000-7000-8000-000000000801',
    'SUP-001',
    'ACME Supplier Co.',
    'SUPPLIER',
    '123-45-67890',
    'Jane Kim',
    'jane@acme.example.com',
    '+82-2-1234-5678',
    'Seoul, Korea',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000901',
    'CUST-001',
    'Best Buyer Inc.',
    'CUSTOMER',
    '987-65-43210',
    'John Park',
    'john@bestbuyer.example.com',
    '+82-2-9876-5432',
    'Busan, Korea',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
),
(
    '01910000-0000-7000-8000-000000000951',
    'BOTH-001',
    'Omni Trading Partner',
    'BOTH',
    '555-55-55555',
    'Sam Lee',
    'sam@omnitrading.example.com',
    '+82-2-5555-5555',
    'Incheon, Korea',
    'ACTIVE',
    0,
    '2026-04-18T00:00:00Z',
    'seed-dev',
    '2026-04-18T00:00:00Z',
    'seed-dev'
)
ON CONFLICT (partner_code) DO NOTHING;
