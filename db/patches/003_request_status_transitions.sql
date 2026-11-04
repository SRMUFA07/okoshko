-- Requests start pending and may transition once to approved or rejected.
-- Reapplying this patch is safe.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'requests_status_allowed'
           AND conrelid = 'public.requests'::regclass
    ) THEN
        ALTER TABLE public.requests
            ADD CONSTRAINT requests_status_allowed
            CHECK (status IN ('pending', 'approved', 'rejected'));
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.enforce_request_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'pending' THEN
            RAISE EXCEPTION 'new requests must start with pending status'
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'pending' AND NEW.status IN ('approved', 'rejected') THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'invalid request status transition: % -> %', OLD.status, NEW.status
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS requests_status_transition ON public.requests;
CREATE TRIGGER requests_status_transition
BEFORE INSERT OR UPDATE OF status ON public.requests
FOR EACH ROW
EXECUTE FUNCTION public.enforce_request_status_transition();
