-- A Telegram user can open the public catalogue before submitting a student
-- application. Store the Telegram identity and first-use notice separately
-- from the child's eventual student profile.
ALTER TABLE public.users
    ALTER COLUMN first_name DROP NOT NULL,
    ALTER COLUMN last_name DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS telegram_id_consent_at timestamptz,
    ADD COLUMN IF NOT EXISTS telegram_id_consent_version text,
    ADD COLUMN IF NOT EXISTS notifications_enabled boolean NOT NULL DEFAULT false;

INSERT INTO public.roles (name)
VALUES ('user')
ON CONFLICT (name) DO NOTHING;

CREATE OR REPLACE FUNCTION public.enforce_user_role_compatibility()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    assigned_role text;
    old_user_id bigint;
    old_role_id bigint;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        old_user_id := OLD.user_id;
        old_role_id := OLD.role_id;
    END IF;

    PERFORM 1
      FROM public.users
     WHERE user_id IN (NEW.user_id, old_user_id)
     ORDER BY user_id
     FOR UPDATE;

    SELECT name INTO assigned_role
      FROM public.roles
     WHERE role_id = NEW.role_id;

    IF assigned_role = 'user' AND EXISTS (
        SELECT 1
          FROM public.user_roles ur
         WHERE ur.user_id = NEW.user_id
           AND (TG_OP <> 'UPDATE' OR ur.user_id <> old_user_id OR ur.role_id <> old_role_id)
    ) THEN
        RAISE EXCEPTION 'user role cannot be combined with other roles'
            USING ERRCODE = 'check_violation';
    ELSIF assigned_role IN ('student', 'admin', 'teacher') AND EXISTS (
        SELECT 1
          FROM public.user_roles ur
          JOIN public.roles r USING (role_id)
         WHERE ur.user_id = NEW.user_id
           AND r.name = 'user'
           AND (TG_OP <> 'UPDATE' OR ur.user_id <> old_user_id OR ur.role_id <> old_role_id)
    ) THEN
        RAISE EXCEPTION 'user role cannot be combined with other roles'
            USING ERRCODE = 'check_violation';
    END IF;

    IF assigned_role = 'student' AND EXISTS (
        SELECT 1
          FROM public.user_roles ur
          JOIN public.roles r USING (role_id)
         WHERE ur.user_id = NEW.user_id
           AND r.name IN ('admin', 'teacher')
           AND (TG_OP <> 'UPDATE' OR ur.user_id <> old_user_id OR ur.role_id <> old_role_id)
    ) THEN
        RAISE EXCEPTION 'student role cannot be combined with admin or teacher'
            USING ERRCODE = 'check_violation';
    ELSIF assigned_role IN ('admin', 'teacher') AND EXISTS (
        SELECT 1
          FROM public.user_roles ur
          JOIN public.roles r USING (role_id)
         WHERE ur.user_id = NEW.user_id
           AND r.name = 'student'
           AND (TG_OP <> 'UPDATE' OR ur.user_id <> old_user_id OR ur.role_id <> old_role_id)
    ) THEN
        RAISE EXCEPTION 'admin or teacher role cannot be combined with student'
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;
