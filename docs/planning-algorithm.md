# Planning algorithm — specification (v0.1)

Procedural spec for **planning** (assigning **planned** dates). Pain formulas: [pain-model.md](./pain-model.md). Instance generation: [scheduling-model.md](./scheduling-model.md) (when present).

**Status:** Locked for v1 implementation.

---

## 1. Purpose

Given a horizon `[H_start, H_end]` and current task state, produce a **plan**: a **planned** calendar day for each schedulable instance, minimizing pain subject to feasible-day rules and same-day ordering.

**`planned_at` is ephemeral** until the user marks an instance done; only then persist on the completion record (see section 6).

---

## 2. Pipeline

```
plan(horizonStart, horizonEnd, settings, tasks):
    1. reconcileOnRead(tasks)              // scheduling: opens, catch_up_count, etc.
    2. instances = scheduleHorizon(...)    // opens + backlog virtuals + forward virtuals
    3. for each instance in instances:
           build F_i                       // feasible days (section 3)
    4. assignment = planAssign(instances, F_i, settings)   // section 5
    5. return PlanResult(assignment, pains, flags)         // section 7
```

Scheduling steps 1–2 are defined in scheduling-model; this document starts at **feasible days** and **assignment**.

---

## 3. Feasible days `F_i`

For each plannable instance **i**, **`F_i`** is the set of calendar days **d** in `[H_start, H_end]` such that:

| Rule | Condition |
|------|-----------|
| Horizon | `H_start <= d <= H_end` |
| Allowed weekdays | `d` matches task weekday rules (if any) |
| Seasonal window | `d` inside seasonal window (if any) |
| Snooze | `d >= snooze_until` (if set on this instance) |
| Archived | Forward virtuals excluded; **catch-up backlog** rows still plannable |

**`d0_i`** = earliest day in **`F_i`** (first feasible day). Used by Regime B in pain-model.

**ASAP / external due:** same as other instances once an open row exists; **`F_i`** from horizon start subject to snooze/weekdays.

Days **not** in **`F_i`** are never chosen; they are not “infinite pain” days.

---

## 4. Overdue for Regime B (planning)

An instance uses **Regime B** (0 timing pain on **`d0_i`**) when **both**:

1. **`scheduled_at < H_start`** (or `< today` when `H_start = today`), and  
2. **Grace has ended** as of **`H_start`**:  
   `index(H_start) - index(scheduled_at) > g_late`  
   (equivalently: `H_start` is outside the grace band `[scheduled_at - g_early, scheduled_at + g_late]`).

Otherwise use **Regime A** only (no forced zero at **`d0`**).

See [pain-model.md](./pain-model.md) sections 3 and 6.

---

## 5. Same-day ordering

### 5.1 Rules

- Tasks may define **precedence edges**: **“A before B”** (task A must appear **before** task B in the within-day list).
- Edges apply **only if** A and B are planned on the **same day**.
- No ordering constraint across different days.

### 5.2 Representation

Per task or global config (TBD in schema): directed pairs `(before_task_id, after_task_id)`.

### 5.3 Enforcement

After all instances have a planned day:

1. Group instances by planned day **d**.
2. For each **d**, topologically sort instances using edges where **both** endpoints are on **d**.
3. If a **cycle** exists among same-day tasks → **PlanResult** warning `ordering_cycle` (should not happen with valid user input).
4. **`within_day_order`** integer in the response (0, 1, 2, …) for UI; **`preferred_time`** breaks ties cosmetically.

**Planner assignment:** when evaluating a candidate day **d**, reject (or penalize as impossible) assignments that would make same-day ordering unsatisfiable. In v1: after greedy assign, run sort; if edge violated, swap/move in local search.

Multiple backlog virtuals of the **same** task on one day: any order unless edges involve other tasks.

---

## 6. `planned_at` persistence

| When | Behavior |
|------|----------|
| Plan generated | **`planned_at`** lives only in **PlanResult** (memory / API response). |
| User marks done | Persist **`planned_at`** from that plan on the **completion** row together with **`scheduled_at`**, **`completed_at`**. |
| Re-plan | New plan overwrites ephemeral assignments; no DB column for “current plan” in v1. |

---

## 7. Daily cap `H_hard` — best effort

**Assignment** does **not** treat **`H_hard`** as a hard stop.

- Planner assigns all instances to minimize pain (may exceed **`H_hard`** on some days).
- For each day **d** in the result:

```
L(d) = sum of duration_minutes for instances planned on d
over_hard_cap(d) = (L(d) > H_hard)
```

**`PlanResult.days[]`** includes **`over_hard_cap`** boolean per day for UI (warn / highlight).

**Pain** from **`P_daily`** still uses **`rho(L)`** for all **`L`** (including **`L > H_hard`**). No separate “hard reject” in v1.

*(This clarifies implementation vs pain-model “hard constraint” wording: assignment is best-effort; overload is flagged, not forbidden.)*

---

## 8. Pain (reference)

Total objective (minimize):

```
P_total = P_timing + P_daily
```

- **Timing:** Regime A/B + backlog **`M(c)`** — [pain-model.md](./pain-model.md).
- **Daily:** **`rho(L(d))`** per day.

No pain cap; compare **`P_total`** to **`P*`** for UI only.

---

## 9. Planner algorithm (v1)

### 9.1 Initialization order

Process instances in this priority (stable sort):

1. Backlog virtuals and overdue (Regime B eligible) first  
2. Higher **`w`** (importance)  
3. Earlier **`scheduled_at`**  
4. Lower task id (tie-break)

### 9.2 Greedy assignment

For each instance **i** in order:

```
best = null
for each day d in F_i in chronological order:
    if assigning i -> d keeps same-day ordering potentially satisfiable:
        delta_P = marginal increase in P_total
        if best is null or delta_P < best.delta_P:
            best = (d, delta_P)
        else if delta_P == best.delta_P and d earlier:
            best = (d, delta_P)
assign i to best.d (fallback: first day in F_i if all equal)
```

**Marginal pain** includes:

- Change in **timing_pain** (Regime A/B, **`M(c)`** for backlog virtuals)  
- Change in **`rho(L(d))`** for affected days  

### 9.3 Local search

Repeat until no improvement or **max_iterations** (e.g. 1000):

- **Move:** reassign one instance to another day in **`F_i`** if **`P_total`** decreases and same-day order can be satisfied.  
- **Swap:** exchange planned days of two instances if feasible and **`P_total`** decreases.

Re-run within-day topological sort after each change.

### 9.4 Output ordering

After optimization, compute **`within_day_order`** per day (section 5).

---

## 10. `PlanResult` (API shape)

```json
{
  "horizonStart": "2026-06-01",
  "horizonEnd": "2026-06-07",
  "pTotal": 42.5,
  "pStar": 100,
  "pTiming": 30.0,
  "pDaily": 12.5,
  "days": [
    { "date": "2026-06-01", "loadMinutes": 90, "overHardCap": false, "dailyPain": 4.2 }
  ],
  "items": [
    {
      "instanceKey": "uuid-or-virtual-id",
      "taskId": "...",
      "virtual": true,
      "backlog": true,
      "scheduledAt": "2026-05-28",
      "plannedAt": "2026-06-01",
      "withinDayOrder": 0,
      "timingPain": 0,
      "durationMinutes": 15
    }
  ],
  "warnings": []
}
```

- **`instanceKey`:** persisted open id, or synthetic id for virtuals (`taskId + scheduledAt + backlogIndex`).  
- **`warnings`:** e.g. `ordering_cycle`, `no_feasible_day` (instance unassigned — should be rare).

---

## 11. API

| Method | Path | Body |
|--------|------|------|
| POST | `/api/plan` | `{ "horizonStart", "horizonEnd" }` |

Response: **PlanResult** above.

Mark done / snooze: separate endpoints; client may re-POST `/api/plan`.

---

## 12. Acceptance tests (planning)

| Scenario | Check |
|----------|--------|
| Fingernails | Early plan day lower timing pain than late |
| Regime B | Timing pain 0 on **`d0`**, rises on **`d0+1`** |
| Two tasks, same slip | Earlier **`scheduled_at`** → higher pain |
| Backlog **c=3**, **backlog_p=0.6** | Higher timing pain than **c=1** |
| Overload day | **`overHardCap`** true; **`rho(L)`** high |
| A before B same day | B always **`within_day_order` > A** |
| Snooze | Nothing planned before **`snooze_until`** |
| Mark done | Only completion stores **`planned_at`** |

---

## 13. Related documents

| Doc | Role |
|-----|------|
| [requirements.md](./requirements.md) | Product |
| [pain-model.md](./pain-model.md) | Pain formulas |
| [scheduling-model.md](./scheduling-model.md) | Instances input |

---

## 14. Changelog

| Version | Notes |
|---------|--------|
| 0.1 | Initial: pipeline, F_i, ordering, best-effort H_hard flag, ephemeral planned_at |
