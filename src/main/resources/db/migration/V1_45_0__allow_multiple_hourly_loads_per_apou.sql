-- Allow multiple hourly loads per academic period (e.g., one per semester semester partition or named load).
-- The composite unique index (academic_period_organization_unit_id, name) already enforces the correct constraint.
-- Dropping the redundant single-column unique index that prevented more than one load per APOU.
drop index if exists hourly_load_academic_period_organization_unit_id_uindex;
