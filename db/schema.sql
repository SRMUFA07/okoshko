--
-- PostgreSQL database dump
--

\restrict uspF1DUf11AoHXiSz6fLKbYtcNOUUtQWohFWrEQovzTU3oQQCaTUhcu8gQ77rHB

-- Dumped from database version 18.0 (Postgres.app)
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: enforce_request_status_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_request_status_transition() RETURNS trigger
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


--
-- Name: enforce_user_role_compatibility(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_user_role_compatibility() RETURNS trigger
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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.groups (
    group_id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    name character varying(150) NOT NULL,
    english_level character varying(50),
    class_schedule text,
    lesson_link text
);


--
-- Name: groups_group_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.groups ALTER COLUMN group_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.groups_group_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: homework_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.homework_assignments (
    homework_id bigint NOT NULL,
    group_id bigint NOT NULL,
    assignment_name character varying(200) NOT NULL,
    assignment_description text NOT NULL,
    due_date timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX homework_group_due_day_uidx
    ON public.homework_assignments
       (group_id, ((due_date AT TIME ZONE 'Asia/Yekaterinburg')::date));


--
-- Name: homework_assignments_homework_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.homework_assignments ALTER COLUMN homework_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.homework_assignments_homework_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: lessons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lessons (
    lesson_id bigint NOT NULL,
    group_id bigint NOT NULL,
    lesson_date date NOT NULL,
    lesson_time time without time zone NOT NULL,
    lesson_link text
);


--
-- Name: lessons_lesson_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.lessons ALTER COLUMN lesson_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.lessons_lesson_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: request_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.request_groups (
    request_id bigint NOT NULL,
    group_id bigint NOT NULL
);


--
-- Name: requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requests (
    request_id bigint NOT NULL,
    student_id bigint,
    applicant_user_id bigint,
    child_first_name character varying(100),
    child_last_name character varying(100),
    parent_phone character varying(32),
    consent_accepted_at timestamp with time zone,
    consent_text_version text,
    submitted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(50) DEFAULT 'pending'::character varying NOT NULL,
    desired_time timestamp with time zone,
    idempotency_key uuid,
    CONSTRAINT requests_status_allowed CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'approved'::character varying, 'rejected'::character varying])::text[]))),
    CONSTRAINT requests_applicant_data_complete CHECK ((applicant_user_id IS NULL) OR (child_first_name IS NOT NULL AND child_last_name IS NOT NULL AND parent_phone IS NOT NULL AND consent_accepted_at IS NOT NULL AND consent_text_version IS NOT NULL))
);


--
-- Name: requests_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.requests ALTER COLUMN request_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.requests_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id bigint NOT NULL,
    name character varying(100) NOT NULL
);


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.roles ALTER COLUMN role_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.roles_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: student_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_groups (
    student_id bigint NOT NULL,
    group_id bigint NOT NULL
);


--
-- Name: students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.students (
    student_id bigint NOT NULL,
    age integer,
    english_level character varying(50),
    user_id bigint NOT NULL,
    child_first_name character varying(100),
    child_last_name character varying(100),
    CONSTRAINT students_age_check CHECK ((age > 0))
);


--
-- Name: students_student_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.students ALTER COLUMN student_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.students_student_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: teachers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teachers (
    teacher_id bigint NOT NULL,
    user_id bigint NOT NULL,
    description text,
    experience_years integer,
    CONSTRAINT teachers_experience_years_check CHECK ((experience_years >= 0))
);


--
-- Name: teachers_teacher_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.teachers ALTER COLUMN teacher_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.teachers_teacher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    role_id bigint NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    last_name character varying(100),
    first_name character varying(100),
    patronymic character varying(100),
    telegram_id bigint,
    notification_consent boolean DEFAULT false NOT NULL,
    telegram_id_consent_at timestamp with time zone,
    telegram_id_consent_version text,
    notifications_enabled boolean DEFAULT false NOT NULL
);


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN user_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: groups groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_pkey PRIMARY KEY (group_id);


--
-- Name: groups groups_teacher_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_teacher_id_name_key UNIQUE (teacher_id, name);


--
-- Name: homework_assignments homework_assignments_group_id_assignment_description_due_da_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_assignments
    ADD CONSTRAINT homework_assignments_group_id_assignment_description_due_da_key UNIQUE (group_id, assignment_description, due_date);


--
-- Name: homework_assignments homework_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_assignments
    ADD CONSTRAINT homework_assignments_pkey PRIMARY KEY (homework_id);


--
-- Name: lessons lessons_group_id_lesson_date_lesson_time_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT lessons_group_id_lesson_date_lesson_time_key UNIQUE (group_id, lesson_date, lesson_time);


--
-- Name: lessons lessons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT lessons_pkey PRIMARY KEY (lesson_id);


--
-- Name: request_groups request_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_groups
    ADD CONSTRAINT request_groups_pkey PRIMARY KEY (request_id, group_id);


--
-- Name: requests requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (request_id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: student_groups student_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT student_groups_pkey PRIMARY KEY (student_id, group_id);


--
-- Name: students students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_pkey PRIMARY KEY (student_id);


--
-- Name: students students_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_user_id_key UNIQUE (user_id);


--
-- Name: teachers teachers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT teachers_pkey PRIMARY KEY (teacher_id);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (role_id, user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: users users_telegram_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_telegram_id_key UNIQUE (telegram_id);


--
-- Name: requests_student_idempotency_key_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX requests_student_idempotency_key_uidx ON public.requests USING btree (student_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);

CREATE UNIQUE INDEX requests_applicant_idempotency_key_uidx ON public.requests USING btree (applicant_user_id, idempotency_key) WHERE (applicant_user_id IS NOT NULL AND idempotency_key IS NOT NULL);


--
-- Name: teachers_user_id_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX teachers_user_id_key ON public.teachers USING btree (user_id);


--
-- Name: requests requests_status_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER requests_status_transition BEFORE INSERT OR UPDATE OF status ON public.requests FOR EACH ROW EXECUTE FUNCTION public.enforce_request_status_transition();


--
-- Name: user_roles user_roles_compatibility; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_roles_compatibility BEFORE INSERT OR UPDATE OF user_id, role_id ON public.user_roles FOR EACH ROW EXECUTE FUNCTION public.enforce_user_role_compatibility();


--
-- Name: groups groups_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teachers(teacher_id);


--
-- Name: homework_assignments homework_assignments_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_assignments
    ADD CONSTRAINT homework_assignments_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(group_id);


--
-- Name: lessons lessons_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT lessons_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(group_id);


--
-- Name: request_groups request_groups_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_groups
    ADD CONSTRAINT request_groups_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(group_id);


--
-- Name: request_groups request_groups_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_groups
    ADD CONSTRAINT request_groups_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.requests(request_id);


--
-- Name: requests requests_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(student_id);

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_applicant_user_id_fkey FOREIGN KEY (applicant_user_id) REFERENCES public.users(user_id);


--
-- Name: student_groups student_groups_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT student_groups_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(group_id);


--
-- Name: student_groups student_groups_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_groups
    ADD CONSTRAINT student_groups_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.students(student_id);


--
-- Name: students students_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.students
    ADD CONSTRAINT students_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: teachers teachers_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teachers
    ADD CONSTRAINT teachers_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- PostgreSQL database dump complete
--

\unrestrict uspF1DUf11AoHXiSz6fLKbYtcNOUUtQWohFWrEQovzTU3oQQCaTUhcu8gQ77rHB

