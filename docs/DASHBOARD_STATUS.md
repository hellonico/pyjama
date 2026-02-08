# Dashboard Improvements - Status

## ✅ Fixed (Backend)

1. **Activity Logging** - All steps now logged to Recent Activity
2. **Global Metrics** - Agent completions update Total Executions, Success Rate, Avg Duration
3. **Step Tracking** - All 14 steps tracked with start/end times
4. **Agent Status** - Correctly shows "running" → "completed"

## 🎨 TODO (Frontend - UI)

### 1. Collapsible Workflow Steps
**Current:** Shows all 14 steps inline (too crowded)  
**Desired:** Show first 3-4 steps, click agent card to expand full view

**Implementation:**
- Add click handler to agent card
- Show first 3 steps + "... and 11 more"
- On click: Modal/expanded view with all steps

### 2. Better Visual Hierarchy
- Make completed steps less prominent (gray out)
- Highlight current running step
- Add step duration next to each step

## Test It

Run a new agent to see the fixes:
```bash
cd /Users/nico/cool/origami-nightweave/pyjama
./run-agent.sh
```

You should now see:
- ✅ All steps in Recent Activity (not just write-file)
- ✅ Global Metrics updating (Total Executions: 1, Success Rate: 100%)
- ✅ Full workflow tracking

The UI collapsing needs JavaScript changes in `dashboard.clj`.
