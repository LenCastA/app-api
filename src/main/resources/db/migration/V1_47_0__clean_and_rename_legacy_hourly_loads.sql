-- Step 1: Identify and temporarily store schedule_ids associated with the legacy hourly loads to be deleted
create temp table target_legacy_schedules on commit drop as
select distinct ss.schedule_id
from schedule_subject ss
join hourly_load hl on ss.hourly_load_id = hl.id
where hl.name in ('CARGA HORARIA 2026-1 (1)', 'Carga Horaria 2026-2');

-- Step 2: Delete schedule_subject rows for the target legacy hourly loads
delete from schedule_subject
where hourly_load_id in (
    select id from hourly_load where name in ('CARGA HORARIA 2026-1 (1)', 'Carga Horaria 2026-2')
);

-- Step 3: Identify target schedules that no longer have any remaining schedule_subject references
create temp table orphan_legacy_schedules on commit drop as
select tls.schedule_id
from target_legacy_schedules tls
where not exists (
    select 1 from schedule_subject ss where ss.schedule_id = tls.schedule_id
);

-- Step 4: Delete class_sessions ONLY for the target orphan schedules
delete from class_session
where schedule_id in (
    select schedule_id from orphan_legacy_schedules
);

-- Step 5: Delete ONLY the target orphan schedules
delete from schedule
where id in (
    select schedule_id from orphan_legacy_schedules
);

-- Step 6: Delete the two legacy hourly_load records
delete from hourly_load
where name in ('CARGA HORARIA 2026-1 (1)', 'Carga Horaria 2026-2');

-- Step 7: Rename legacy 'Carga Horaria 2026-1' to 'Carga Horaria 2026-1 Oficial'
update hourly_load
set name = 'Carga Horaria 2026-1 Oficial'
where name = 'Carga Horaria 2026-1';
