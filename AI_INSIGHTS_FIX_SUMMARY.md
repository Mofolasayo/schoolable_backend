# AI Insights & Weekly Report Submission - Fixed Issues

## Date: January 9, 2026

## Issues Fixed

### 1. ✅ **Database Constraint Violation on Report Submission**

**Problem:**
```
ERROR: null value in column "technical_score" of relation "weekly_performance_reports" violates not-null constraint
```

**Root Cause:**  
The simplified weekly report endpoint was setting the 3 soft skill ratings (Initiative, Attitude, Teamwork) but **NOT** the 4 core pillar scores (Technical, Behavioral, Culture Fit, Growth) which are marked as NOT NULL in the database.

**Solution:**  
Updated `WeeklyReportService.java` to automatically map the simplified ratings to the core pillars:
- **Initiative → Technical Competence**
- **Attitude → Behavioral Compliance**
- **Teamwork → Culture Fit**
- **Growth/Learning** = Average of the three ratings

This mapping happens for both new AND existing reports, preventing the constraint violation.

---

### 2. ✅ **Teams Overview Page Shows "No Team Data Yet"**

**Problem:**  
Despite having submitted weekly reports, the Teams Overview page (likely the Super Admin dashboard) showed no team data.

**Root Cause:**  
The Teams Overview page displays **Quarterly Scores**, not weekly reports. The system was waiting for someone to manually trigger quarterly score calculation for each team.

**Solution:**  
Added **automatic quarterly score calculation** after every weekly report batch submission:
- When a Team Lead submits their weekly ratings, the system now automatically calculates and saves the team's quarterly score
- This score is immediately visible on the Teams Overview dashboard
- The quarterly score is based on KPI progress and the submitted weekly ratings

---

### 3. ✅ **AI Insights More Detailed & Document-Aware**

**Enhancements Made:**

1. **Strategic Prompt Engineering**  
   - AI now acts like a "Senior Management Consultant"
   - Provides 3-4 sentence detailed summaries instead of generic bullet points
   - Directly links soft skill ratings to KPI outcomes

2. **Document Awareness**  
   - System now captures the uploaded document name (e.g., "Mofolasayo Osikoya.pdf")
   - AI is explicitly instructed to reference uploaded documents (CVs, Plans) in its recommendations
   - Example: "Leverage the skills identified in [Document Name] to bridge the gap in [Specific KPI]"

3. **Personalized Coaching**  
   - AI mentions specific employees by name
   - Identifies "silent risks" (e.g., stable but low engagement)
   - Provides behavioral analysis based on Team Lead ratings

4. **Robust Response Parsing**  
   - Improved JSON extraction to handle AI responses even if they include preamble text
   - Less likely to show "AI unavailable" errors

---

## How to Verify the Fixes

### Test Weekly Report Submission:
1. Go to **Weekly Reports** page
2. Fill in ratings for your team members (Initiative, Attitude, Teamwork on 1-5 scale)
3. Upload a document (optional, but recommended for AI context)
4. Click **Final Submit**
5. ✅ **Expected:** No database errors, success message appears

### Test Teams Overview:
1. Log in as **Super Admin**
2. Navigate to **Teams** → **Teams Overview**
3. ✅ **Expected:** Your team now appears with a quarterly score

### Test AI Insights:
1. Go to **AI Insights** page
2. Click **"Generate New Insight"** button
3. ✅ **Expected:** Detailed, personalized summary mentioning:
   - Specific employee names
   - Uploaded document reference
   - Actionable recommendations
   - No "Unable to generate" errors

---

## Do You Need to Deploy to Render?

**NO** - Your local development environment can generate AI insights as long as:
- Your backend is running (`./gradlew bootRun`)
- You have internet connectivity (for Gemini API calls)
- The Gemini API key is configured (already in `application.yml`)

**Deploy to Render only when:**
- You want other users (team leads, super admin) to access the live application
- You're ready for production use

---

## Files Modified

1. **Backend:**
   - `src/main/java/com/schoolable/backend/performance/WeeklyReportService.java`
   - `src/main/java/com/schoolable/backend/kpi/GeminiAiService.java`
   - `src/main/java/com/schoolable/backend/kpi/KpiAnalysisService.java`

2. **Data Model:**
   - No database migrations needed - only code logic changes

---

## Next Steps

1. ✅ **Restart your backend server** to apply these changes
2. Try submitting a new weekly report
3. Check the Teams Overview page
4. Generate AI insights and review the detail level

If you encounter any issues, check the backend console logs for detailed error messages.
