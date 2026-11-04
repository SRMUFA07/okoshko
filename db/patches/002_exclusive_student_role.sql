-- Keep the agreed role rule in the database: student cannot coexist with
-- admin or teacher; admin and teacher may coexist. Locking the owning user
-- row serializes concurrent role assignments for the same user.
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

    -- Lock affected users in stable order to avoid concurrent check-then-write
    -- races and deadlocks when a role assignment is moved between users.
    PERFORM 1
      FROM public.users
     WHERE user_id IN (NEW.user_id, old_user_id)
     ORDER BY user_id
     FOR UPDATE;

    SELECT name INTO assigned_role
      FROM public.roles
     WHERE role_id = NEW.role_id;

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

DROP TRIGGER IF EXISTS user_roles_compatibility ON public.user_roles;
CREATE TRIGGER user_roles_compatibility
BEFORE INSERT OR UPDATE OF user_id, role_id ON public.user_roles
FOR EACH ROW
EXECUTE FUNCTION public.enforce_user_role_compatibility();
