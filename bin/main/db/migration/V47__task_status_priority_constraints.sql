ALTER TABLE IF EXISTS tasks DROP CONSTRAINT IF EXISTS tasks_status_check;
ALTER TABLE IF EXISTS tasks DROP CONSTRAINT IF EXISTS tasks_priority_check;

ALTER TABLE IF EXISTS tasks
    ADD CONSTRAINT tasks_status_check CHECK (
        status IN (
            'Pending',
            'In Progress',
            'Completed',
            'Overdue',
            'TODO',
            'IN_PROGRESS',
            'REVIEW',
            'DONE',
            'CANCELLED',
            'CANCELED'
        )
    );

ALTER TABLE IF EXISTS tasks
    ADD CONSTRAINT tasks_priority_check CHECK (
        priority IN (
            'Low',
            'Medium',
            'High',
            'Critical'
        )
    );
