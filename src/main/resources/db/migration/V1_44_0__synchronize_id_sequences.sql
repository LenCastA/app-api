do
$$
declare
    table_record record;
    sequence_name text;
    maximum_id bigint;
begin
    for table_record in
        select table_schema, table_name
        from information_schema.columns
        where table_schema = 'public'
          and column_name = 'id'
        loop
            sequence_name := pg_get_serial_sequence(
                format('%I.%I', table_record.table_schema, table_record.table_name),
                'id'
            );

            if sequence_name is not null then
                execute format(
                    'select max(id) from %I.%I',
                    table_record.table_schema,
                    table_record.table_name
                ) into maximum_id;

                if maximum_id is null then
                    execute format('select setval(%L, 1, false)', sequence_name);
                else
                    execute format('select setval(%L, %s, true)', sequence_name, maximum_id);
                end if;
            end if;
        end loop;
end
$$;
