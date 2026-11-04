ALTER TABLE public.requests
    ADD COLUMN IF NOT EXISTS idempotency_key uuid;

CREATE UNIQUE INDEX IF NOT EXISTS requests_student_idempotency_key_uidx
    ON public.requests (student_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
