-- A group's join URL is shared by its students and does not vary by lesson.
ALTER TABLE public.groups
    ADD COLUMN IF NOT EXISTS lesson_link text;
