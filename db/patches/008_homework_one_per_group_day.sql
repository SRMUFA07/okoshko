-- Keep one homework item for each group and calendar date in Ufa.
-- Saving another assignment for the same day replaces that day's slot.
CREATE UNIQUE INDEX IF NOT EXISTS homework_group_due_day_uidx
    ON public.homework_assignments
       (group_id, ((due_date AT TIME ZONE 'Asia/Yekaterinburg')::date));
