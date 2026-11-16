INSERT INTO public.roles (name)
VALUES ('user'), ('student'), ('admin'), ('teacher')
ON CONFLICT (name) DO NOTHING;
