INSERT INTO public.roles (name)
VALUES ('student'), ('admin'), ('teacher')
ON CONFLICT (name) DO NOTHING;
