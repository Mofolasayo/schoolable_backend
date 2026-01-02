# 🚀 Auto-Aura Performance System

## Overview

The Aura Performance System is a **fully automated** employee evaluation framework that calculates performance scores using only system data. It minimizes subjective input and maximizes objectivity through data-driven metrics.

---

## 🎯 Core Design Principles

1. **Automation First**: If data exists in the system, use it. Don't ask for manual input.
2. **Department-Specific**: Each team has different KPIs based on their role.
3. **Transparency**: Employees can see exactly how their score is calculated.
4. **Fairness**: Same metrics = same calculation = no bias.

---

## 📊 Automation Breakdown by Department

### Engineering/Development (~80% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Task Completion Rate | Tasks | `Completed tasks / Total assigned × 100` |
| On-Time Delivery | Tasks | `Tasks completed by due date / Total completed × 100` |
| Task Quality | Tasks | `Tasks not reopened / Total completed × 100` |
| Documentation | Tasks | `Tasks with descriptions > 20 chars / Total × 100` |
| Attendance | Attendance | `Days present / Expected work days × 100` |
| Punctuality | Attendance | `Check-ins before 9 AM / Total check-ins × 100` |
| Policy Compliance | Compliance | `Compliant submissions / Total × 100` |
| Training | Training | `Certifications this quarter ≥ 1 = 100%` |
| Initiative* | Weekly Report | Team Lead rates 1-5, converted to percentage |
| Attitude* | Weekly Report | Team Lead rates 1-5, converted to percentage |

*Only ~20% requires Team Lead input*

### Operations/Admin (~75% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Task Completion | Tasks | Same as above |
| Process Adherence | Compliance | SOP compliance rate |
| Attendance | Attendance | Higher target (98%) for ops |
| Punctuality | Attendance | Higher target (95%) for ops |
| Collaboration | Tasks | Cross-team task participation |
| Training | Training | Completion status |
| Reliability* | Weekly Report | TL rating |
| Adaptability* | Weekly Report | TL rating |

### Sales/Business Dev (~70% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Target Achievement | Sales Data | Revenue / Target × 100 (if integrated) |
| Deals Closed | Tasks/CRM | Completed sales tasks |
| Client Meetings | Calendar | Meetings attended (if integrated) |
| Response Time | Comms | Average reply time (if chat integrated) |
| Professionalism* | Weekly Report | TL rating |
| Initiative* | Weekly Report | TL rating |

### HR/People Ops (~75% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Task Completion | Tasks | HR task completion |
| Process Adherence | Compliance | Policy/SOP compliance |
| Documentation | Tasks | Tasks with proper documentation |
| Attendance | Attendance | High attendance expected (98%) |
| Employee Support | Tasks | HR tasks handled |
| Training | Training | HR certifications |
| Confidentiality* | Weekly Report | TL rating |
| Culture Champion* | Weekly Report | TL rating |

### Finance/Accounting (~70% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Report Accuracy | Tasks | Tasks without reopening |
| Deadline Adherence | Tasks | On-time task completion (100% target) |
| Audit Compliance | Compliance | Compliance submissions |
| Attendance | Attendance | Very high target (98%) |
| Punctuality | Attendance | Very high target (95%) |
| Confidentiality* | Weekly Report | TL rating |
| Integrity* | Weekly Report | TL rating |

### Marketing (~70% Automated)

| Metric | Data Source | How It's Calculated |
|--------|-------------|---------------------|
| Campaign Delivery | Tasks | Marketing tasks completed |
| Content Output | Tasks | Content tasks completed |
| On-Time Delivery | Tasks | Deadline adherence |
| Collaboration | Tasks | Cross-team work |
| Brand Alignment* | Weekly Report | TL rating |
| Creativity* | Weekly Report | TL rating |

---

## 🔢 How Scores Are Calculated

### Step 1: Raw Data Collection
```
Employee: John Doe (Engineering)

Tasks this quarter:
- Total Assigned: 25
- Completed: 22
- On-time: 20

Attendance this quarter:
- Work days: 65
- Present: 62
- On-time check-ins: 58

Compliance:
- Total items: 10
- Compliant: 10

Training:
- Certificates this quarter: 1
```

### Step 2: Convert to Percentages
```
Task Completion Rate: 22/25 × 100 = 88%
On-Time Delivery: 20/22 × 100 = 91%
Attendance Rate: 62/65 × 100 = 95%
Punctuality: 58/62 × 100 = 94%
Compliance: 10/10 × 100 = 100%
Training: 1 cert = 100%
```

### Step 3: Apply Weights (Engineering Profile)

**Technical Pillar (30% of total)**
```
Task Completion (25%): 88 × 0.25 = 22.0
On-Time Delivery (25%): 91 × 0.25 = 22.75
Task Quality (20%): 90 × 0.20 = 18.0
Documentation (15%): 80 × 0.15 = 12.0
Workload Handling (15%): 100 × 0.15 = 15.0
─────────────────────────────────
Pillar Score: 89.75%
Contribution to Aura: 89.75 × 0.30 = 26.93 points
```

**Behavioral Pillar (25% of total)**
```
Attendance (30%): 95 × 0.30 = 28.5
Punctuality (25%): 94 × 0.25 = 23.5
Consistency (20%): 90 × 0.20 = 18.0
Initiative [TL] (25%): 80 × 0.25 = 20.0
─────────────────────────────────
Pillar Score: 90%
Contribution to Aura: 90 × 0.25 = 22.5 points
```

**Culture Fit Pillar (20% of total)**
```
Policy Compliance (35%): 100 × 0.35 = 35.0
Training Compliance (30%): 100 × 0.30 = 30.0
Zero Violations (20%): 100 × 0.20 = 20.0
Attitude [TL] (15%): 80 × 0.15 = 12.0
─────────────────────────────────
Pillar Score: 97%
Contribution to Aura: 97 × 0.20 = 19.4 points
```

**Growth Pillar (25% of total)**
```
Training Hours (25%): 100 × 0.25 = 25.0
Certifications (25%): 100 × 0.25 = 25.0
Improvement Trend (25%): 75 × 0.25 = 18.75
Skill Application [TL] (25%): 80 × 0.25 = 20.0
─────────────────────────────────
Pillar Score: 88.75%
Contribution to Aura: 88.75 × 0.25 = 22.19 points
```

### Step 4: Calculate Final Aura Score
```
Technical Contribution:  26.93
Behavioral Contribution: 22.50
Culture Contribution:    19.40
Growth Contribution:     22.19
────────────────────────────────
AURA SCORE: 91.02 → Grade: A
```

---

## 🔄 Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          DATA SOURCES (System Tracking)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   TASKS     │  │ ATTENDANCE  │  │ COMPLIANCE  │  │  TRAINING   │         │
│  │             │  │             │  │             │  │             │         │
│  │ • Status    │  │ • Check-in  │  │ • SOPs      │  │ • Certs     │         │
│  │ • Due Date  │  │ • Check-out │  │ • Policies  │  │ • Courses   │         │
│  │ • Assignee  │  │ • Date      │  │ • Status    │  │ • Quarter   │         │
│  │ • Created   │  │ • User      │  │ • User      │  │ • Year      │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                │                 │
└─────────┼────────────────┼────────────────┼────────────────┼─────────────────┘
          │                │                │                │
          └────────────────┴────────────────┴────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AUTO AURA CALCULATION SERVICE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│    1. Get Employee → 2. Get Department → 3. Load KPI Profile                │
│                                                                              │
│    ┌─────────────────────────────────────────────────────────────────┐      │
│    │                    DepartmentKpiConfig                          │      │
│    │                                                                 │      │
│    │   Engineering Profile:                                          │      │
│    │   • Technical (30%): task_completion, on_time, quality, docs    │      │
│    │   • Behavioral (25%): attendance, punctuality, initiative       │      │
│    │   • Culture (20%): compliance, training, attitude               │      │
│    │   • Growth (25%): certs, improvement, skill_application         │      │
│    │                                                                 │      │
│    │   Sales Profile:                                                │      │
│    │   • Technical (35%): targets, deals, pipeline, meetings         │      │
│    │   • Behavioral (25%): attendance, response_time, professional   │      │
│    │   • Culture (20%): team_support, compliance, attitude           │      │
│    │   • Growth (20%): product_knowledge, skills, trend              │      │
│    │                                                                 │      │
│    │   (... 4 more department profiles)                              │      │
│    └─────────────────────────────────────────────────────────────────┘      │
│                                                                              │
│    4. For each metric:                                                       │
│       ┌────────────────────────────────────────────────────────────┐        │
│       │  if source == "auto":                                       │        │
│       │      → Query database (tasks/attendance/compliance/training)│        │
│       │      → Calculate percentage vs target                       │        │
│       │  if source == "team_lead":                                  │        │
│       │      → Get latest WeeklyReport rating                       │        │
│       │      → Convert 1-5 scale to percentage                      │        │
│       └────────────────────────────────────────────────────────────┘        │
│                                                                              │
│    5. Apply weights → Calculate pillar scores                                │
│    6. Sum contributions → Final Aura Score                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            OUTPUT                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   {                                                                          │
│     "employeeId": "uuid",                                                    │
│     "employeeName": "John Doe",                                              │
│     "department": "Engineering",                                             │
│     "auraScore": 91.0,                                                       │
│     "grade": "A",                                                            │
│     "qgpa": 3.64,                                                            │
│     "automationRate": 80,  // 80% of metrics are auto-calculated            │
│     "pillars": {                                                             │
│       "technical": {                                                         │
│         "score": 89.75,                                                      │
│         "weight": 30,                                                        │
│         "contribution": 26.93,                                               │
│         "subMetrics": [                                                      │
│           { "key": "task_completion", "score": 88, "source": "auto" },      │
│           { "key": "on_time_delivery", "score": 91, "source": "auto" },     │
│           ...                                                                │
│         ]                                                                    │
│       },                                                                     │
│       ...                                                                    │
│     }                                                                        │
│   }                                                                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## ⏰ Scheduled Calculation

The system automatically runs calculations:

| Schedule | Action |
|----------|--------|
| **Every Sunday 2 AM** | Full recalculation for all employees |
| **On-demand** | Manual trigger via API: `POST /api/performance/recalculate` |
| **Real-time** | When fetching dashboard: `GET /api/performance/my-aura/enhanced` |

---

## 🔧 Team Lead's Role (Simplified)

Team Leads now only need to rate **soft skills** that can't be automated:

### Weekly Rating Form (Simplified)
```
Rate [Employee Name] for this week:

□ Initiative (1-5 stars)
  "How proactive was this employee?"

□ Attitude (1-5 stars)  
  "How positive was their work attitude?"

□ Professionalism (1-5 stars)
  "How professional was their conduct?"

[Submit]
```

This takes **< 1 minute per employee** instead of complex multi-field forms.

---

## 📈 Benefits of This Approach

| Before (Manual) | After (Automated) |
|-----------------|-------------------|
| TL rates 15+ metrics weekly | TL rates 2-3 soft skills only |
| Peer feedback with 9 ratings | Removed - reduces bias |
| Inconsistent scoring | Same formula = fair scoring |
| Time-consuming | < 5 minutes/week for TL |
| Subjective | 75-80% objective data |
| Gaming the system easy | Data-driven = harder to fake |

---

## 🏢 Adding Custom KPIs for New Departments

To add a new department profile:

```java
// In DepartmentKpiConfig.java

"customer_support", new DepartmentProfile(
    "Customer Support",
    Map.of(
        "technical", new PillarProfile(25, Map.of(
            "ticket_resolution", new MetricConfig(40, "Tickets Resolved", "auto", "tickets", "", 100.0),
            "first_response_time", new MetricConfig(30, "First Response Time", "auto", "tickets", "", 5.0),
            "customer_satisfaction", new MetricConfig(30, "CSAT Score", "auto", "surveys", "", 4.5)
        )),
        "behavioral", new PillarProfile(30, Map.of(
            "attendance", new MetricConfig(35, "Attendance", "auto", "attendance", "", 95.0),
            "punctuality", new MetricConfig(30, "Punctuality", "auto", "attendance", "", 90.0),
            "empathy", new MetricConfig(35, "Customer Empathy", "team_lead", "weekly_report", "", 4.0)
        )),
        // ... culture_fit, growth pillars
    )
)
```

---

## 📱 Mobile App Integration

The mobile app shows:
1. **Overall Aura Score** - Big number with grade
2. **Pillar Breakdown** - Tap to expand and see sub-metrics
3. **Source Indicators** - Shows if metric is auto or TL-rated
4. **Trend** - Compared to last quarter

---

## ✅ Summary

| Aspect | Status |
|--------|--------|
| Peer Feedback | ❌ Removed (too complex, biased) |
| Department KPIs | ✅ 6 pre-configured + Default |
| Auto-calculation | ✅ 70-80% of all metrics |
| TL Input | ✅ Simplified to 2-3 fields |
| Scheduling | ✅ Weekly auto-run |
| Real-time | ✅ On-demand calculation |
| Mobile | ✅ Expandable pillar breakdown |
