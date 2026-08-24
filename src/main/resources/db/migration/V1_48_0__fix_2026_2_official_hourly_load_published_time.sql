-- Update published_at timestamp to August 24, 2026 05:00:00 UTC (00:00:00 America/Lima) for 2026-2 Official hourly load
update hourly_load hl
set published_at = timestamp '2026-08-24 05:00:00'
from academic_period_organization_unit apou
join academic_period ap on apou.academic_period_id = ap.id
where hl.academic_period_organization_unit_id = apou.id
  and hl.name = 'Carga Horaria 2026-2 Oficial'
  and ap.code = '2026-2';
