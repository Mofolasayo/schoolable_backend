ALTER TABLE recurring_task_templates
    ADD COLUMN IF NOT EXISTS recurrence_days INTEGER[];
