UPDATE tasks
SET completed_at = COALESCE(updated_at, created_at)
WHERE completed_at IS NULL
  AND status IS NOT NULL
  AND UPPER(status) IN ('DONE', 'COMPLETED');
