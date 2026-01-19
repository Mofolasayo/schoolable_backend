UPDATE team_kpis
SET progress_source = 'DAILY_REPORT_KPI_ALIGNMENT',
    auto_progress_enabled = TRUE
WHERE progress_source IS NULL
  AND is_active = TRUE;

UPDATE individual_kpis
SET progress_source = 'DAILY_REPORT_KPI_ALIGNMENT',
    auto_progress_enabled = TRUE
WHERE progress_source IS NULL
  AND is_active = TRUE;
