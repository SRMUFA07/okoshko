-- The normalized model lists user_id as a candidate key for teachers.
-- Safe to re-run; existing duplicate user_id values must be resolved first.
CREATE UNIQUE INDEX IF NOT EXISTS teachers_user_id_key
    ON public.teachers (user_id);
