-- Applications belong to the authenticated Telegram account. A student
-- profile is created only when an administrator approves an application.
ALTER TABLE public.students
    ADD COLUMN IF NOT EXISTS child_first_name character varying(100),
    ADD COLUMN IF NOT EXISTS child_last_name character varying(100);

ALTER TABLE public.requests
    ALTER COLUMN student_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS applicant_user_id bigint,
    ADD COLUMN IF NOT EXISTS child_first_name character varying(100),
    ADD COLUMN IF NOT EXISTS child_last_name character varying(100),
    ADD COLUMN IF NOT EXISTS parent_phone character varying(32),
    ADD COLUMN IF NOT EXISTS consent_accepted_at timestamptz,
    ADD COLUMN IF NOT EXISTS consent_text_version text;

UPDATE public.requests r
   SET applicant_user_id = NULL,
       child_first_name = COALESCE(r.child_first_name, u.first_name),
       child_last_name = COALESCE(r.child_last_name, u.last_name)
  FROM public.students s
  JOIN public.users u ON u.user_id = s.user_id
 WHERE r.student_id = s.student_id
   AND r.consent_accepted_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'requests_applicant_user_id_fkey'
           AND conrelid = 'public.requests'::regclass
    ) THEN
        ALTER TABLE public.requests
            ADD CONSTRAINT requests_applicant_user_id_fkey
            FOREIGN KEY (applicant_user_id) REFERENCES public.users(user_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'requests_applicant_data_complete'
           AND conrelid = 'public.requests'::regclass
    ) THEN
        ALTER TABLE public.requests
            ADD CONSTRAINT requests_applicant_data_complete CHECK (
                applicant_user_id IS NULL OR (
                    child_first_name IS NOT NULL
                    AND child_last_name IS NOT NULL
                    AND parent_phone IS NOT NULL
                    AND consent_accepted_at IS NOT NULL
                    AND consent_text_version IS NOT NULL
                )
            );
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS requests_applicant_idempotency_key_uidx
    ON public.requests (applicant_user_id, idempotency_key)
    WHERE applicant_user_id IS NOT NULL AND idempotency_key IS NOT NULL;
