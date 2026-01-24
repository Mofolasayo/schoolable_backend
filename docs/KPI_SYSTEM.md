# AI-Powered KPI System Documentation

## Overview

The WorkSight AI-Powered KPI System enables Team Leads to set custom KPIs for their teams, track weekly progress, and receive AI-generated insights powered by Google's Gemini AI.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SYSTEM ARCHITECTURE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                │
│  │  Team Lead   │────▶│  KPI CRUD    │────▶│   Database   │                │
│  │  Dashboard   │     │  Endpoints   │     │  (Postgres)  │                │
│  └──────────────┘     └──────────────┘     └──────────────┘                │
│         │                                          │                        │
│         ▼                                          ▼                        │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                │
│  │   Weekly     │────▶│   Progress   │────▶│  Gemini AI   │                │
│  │   Reports    │     │   Tracking   │     │   Service    │                │
│  └──────────────┘     └──────────────┘     └──────────────┘                │
│                                                    │                        │
│                              ┌─────────────────────┘                        │
│                              ▼                                              │
│                       ┌──────────────┐                                     │
│                       │  AI Insights │                                     │
│                       │  & Scores    │                                     │
│                       └──────────────┘                                     │
│                              │                                              │
│            ┌─────────────────┼─────────────────┐                           │
│            ▼                 ▼                 ▼                           │
│     ┌──────────┐      ┌──────────┐      ┌──────────┐                       │
│     │  Team    │      │  Team    │      │  Super   │                       │
│     │  Lead    │      │  Member  │      │  Admin   │                       │
│     └──────────┘      └──────────┘      └──────────┘                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## API Endpoints

### KPI Management (Team Leads)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/kpi/my-kpis` | GET | Get all KPIs for current team lead |
| `/api/kpi` | POST | Create a new KPI |
| `/api/kpi/{kpiId}` | PUT | Update a KPI |
| `/api/kpi/{kpiId}` | DELETE | Deactivate a KPI |

### Progress Tracking

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/kpi/progress` | POST | Submit weekly KPI progress |
| `/api/kpi/progress` | GET | Get progress for a specific week |

### AI Insights

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/kpi/insights/generate` | POST | Manually trigger AI analysis |
| `/api/kpi/insights/latest` | GET | Get latest insight for team |
| `/api/kpi/insights/team` | GET | Get insight for team members (by dept) |
| `/api/kpi/insights/history` | GET | Get all previous insights |

### Team Scores

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/kpi/score/calculate` | POST | Calculate quarterly team score |
| `/api/kpi/score/my-team` | GET | Get current team's score |
| `/api/kpi/score/all-teams` | GET | Get all team scores (admin) |

### Team Member Access

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/kpi/team-kpis` | GET | View department's KPIs |
| `/api/kpi/insights/team` | GET | View team insights |

## Data Models

### TeamKpi
```json
{
  "id": "uuid",
  "teamLeadId": "uuid",
  "department": "Engineering",
  "name": "Complete Code Reviews",
  "description": "Review all PRs within 24 hours",
  "targetValue": 50,
  "targetUnit": "reviews",
  "weight": 25,
  "quarter": "Q1",
  "year": 2026,
  "isActive": true
}
```

### WeeklyKpiProgress
```json
{
  "id": "uuid",
  "kpiId": "uuid",
  "reportedBy": "uuid",
  "weekNumber": 14,
  "year": 2026,
  "achievedValue": 45,
  "progressPercentage": 90.0,
  "notes": "Slightly behind due to holidays"
}
```

### AiInsight
```json
{
  "id": "uuid",
  "teamLeadId": "uuid",
  "department": "Engineering",
  "insightType": "WEEKLY",
  "weekNumber": 14,
  "quarter": "Q1",
  "year": 2026,
  "kpiScore": 82.5,
  "summary": "Your team achieved 82% of KPIs this week...",
  "insights": {
    "topPerforming": ["Code Reviews (95%)", "Bug Fixes (88%)"],
    "needsAttention": ["Documentation (65%)"]
  },
  "recommendations": {
    "items": [
      "Allocate dedicated time for documentation",
      "Consider pair documentation sessions"
    ]
  },
  "riskAlerts": {
    "items": ["Documentation KPI at risk for Q1"]
  }
}
```

### TeamQuarterlyScore
```json
{
  "id": "uuid",
  "teamLeadId": "uuid",
  "teamName": "Engineering Team",
  "department": "Engineering",
  "quarter": "Q1",
  "year": 2026,
  "kpiAchievementScore": 82.5,
  "overallTeamScore": 82.5,
  "grade": "B",
  "aiSummary": "Strong quarter with consistent performance..."
}
```

## AI Scoring Rules

```
KPI Achievement Scoring:
├── 100%+ achieved  → 100 points (Exceeded)
├── 90-99% achieved → 90 points (Excellent)
├── 80-89% achieved → 80 points (Good)
├── 70-79% achieved → 70 points (Satisfactory)
├── 60-69% achieved → 60 points (Needs Improvement)
└── Below 60%       → 50 points (At Risk)

Final Score = Σ(KPI Score × KPI Weight / 100)
```

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| Weekly Insight Generation | Sunday 11 PM | Auto-generates insights for all teams |

## Usage Examples

### 1. Creating a KPI (Team Lead)

```javascript
const response = await fetch('/api/kpi', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    name: 'Complete Sales Calls',
    description: 'Make outbound sales calls to prospects',
    targetValue: 100,
    targetUnit: 'calls',
    weight: 30,
    quarter: 'Q1',
    year: 2026
  })
});
```

### 2. Submitting Weekly Progress

```javascript
const response = await fetch('/api/kpi/progress', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    weekNumber: 14,
    year: 2026,
    progress: [
      {
        kpiId: 'kpi-uuid-1',
        achievedValue: 25,
        notes: 'Good week for calls'
      },
      {
        kpiId: 'kpi-uuid-2',
        achievedValue: 15,
        notes: null
      }
    ]
  })
});
```

### 3. Getting AI Insights

```javascript
const response = await fetch('/api/kpi/insights/latest', {
  headers: {
    'Authorization': 'Bearer <token>'
  }
});

// Response:
{
  "weekNumber": 14,
  "kpiScore": 85.5,
  "summary": "Your team had an excellent week...",
  "insights": {
    "topPerforming": ["Sales Calls (110%)", "Follow-ups (95%)"],
    "needsAttention": ["Proposals (70%)"]
  },
  "recommendations": {
    "items": [
      "Consider creating proposal templates to speed up creation",
      "Schedule dedicated proposal writing time"
    ]
  },
  "riskAlerts": {
    "items": []
  }
}
```

## Weight Validation

- Total weight of all KPIs for a team must equal 100%
- When creating a KPI, the system checks remaining weight
- API returns `remainingWeight` in the response

## Access Control

| Role | Create KPIs | Report Progress | View Insights | View Team Scores |
|------|-------------|-----------------|---------------|------------------|
| Team Lead | ✅ | ✅ | ✅ (own team) | ✅ (own team) |
| Team Member | ❌ | ❌ | ✅ (own dept) | ✅ (own dept) |
| Super Admin | ❌ | ❌ | ✅ (all teams) | ✅ (all teams) |

## Environment Variables

```bash
# Required for AI insights
GEMINI_API_KEY=your-gemini-api-key
```

## Troubleshooting

### AI Insights Not Generating
1. Check Gemini API key is valid
2. Ensure KPIs exist for the quarter
3. Check progress has been submitted
4. View logs for API errors

### Weight Validation Errors
- Ensure all KPI weights sum to 100%
- Deactivate unused KPIs to free up weight

### Missing Team Score
- Ensure quarterly score has been calculated
- Trigger calculation via `/api/kpi/score/calculate`
