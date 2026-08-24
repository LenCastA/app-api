-- Set published_at timestamp to March 12, 2026 (Peru timezone America/Lima, UTC-5) for 2026-1 hourly loads
update hourly_load
set published_at = timestamp '2026-03-12 17:00:00'
where academic_period_organization_unit_id in (
    select apou.id
    from academic_period_organization_unit apou
    join academic_period ap on apou.academic_period_id = ap.id
    where ap.code = '2026-1'
);
