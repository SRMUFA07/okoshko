-- Existing desired_time values represent local Ufa wall-clock time.
-- Store the corresponding instant while retaining the Ufa zone in application config.
DO $$
DECLARE
    current_type text;
BEGIN
    SELECT data_type
      INTO current_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'requests'
       AND column_name = 'desired_time';

    IF current_type = 'timestamp without time zone' THEN
        ALTER TABLE public.requests
            ALTER COLUMN desired_time TYPE timestamp with time zone
            USING desired_time AT TIME ZONE 'Asia/Yekaterinburg';
    END IF;
END;
$$;
