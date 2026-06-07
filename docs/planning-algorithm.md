# Planning algorithm — specification (v0.3.3)

Procedural spec for **planning** (assigning **planned** dates). Pain formulas: [pain-model.md](./pain-model.md). Instance generation: [scheduling-model.md](./scheduling-model.md).

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
    2. H_user = [horizonStart, horizonEnd]
       H_plan = extendHorizon(H_user)           // section 2.1 (default 2× span)
    3. instances = scheduleHorizon(tasks, H_user.start, H_plan.end)
    4. for each instance in instances:
           build F_i on H_plan                  // section 3
    5. assignment = planAssign(instances, F_i, settings)
    6. return truncatePlan(assignment, H_user) // section 2.1 — user-visible result
```

Scheduling steps 1–3 are defined in scheduling-model; this document starts at **feasible days** and **assignment**.

### 2.1 Extended planning horizon (lookahead)

**Problem:** A hard cutoff at **`H_user_end`** creates boundary effects: the optimizer may **cluster** work on the last user day, trigger **overflow** unnecessarily, or make **local-search** moves that look optimal only because there is “nowhere left to go.” Unplanned reference pain at **`p_beyond`** is correct but should not **distort** placement **inside** the user window.

**Strategy:** Plan on a **longer internal horizon** than the user requested, then **truncate** to the user span for display and API output.

| Symbol | Meaning |
|--------|---------|
| **`H_user`** | `[U_start, U_end]` — what the user asked for (week / month). |
| **`H_plan`** | Extended horizon used for **`F_i`**, assignment, and **`P_total`** optimization. |
| **`p_beyond`** | Always **`U_end + 1 day`** — **not** the internal extended end. |

**Default extension (v1):** **2× span** — same start, end pushed forward by the user horizon length:

```
len = calendar_days(U_start, U_end)    // inclusive count
U_plan_end = U_end + len               // e.g. 7-day user window → plan 14 days from U_start
H_plan = [U_start, U_plan_end]
```

**`extend_factor`** (global setting, default **2**) may generalize this later; v1 uses **2×** as above.

**Truncation (`truncatePlan`):**

| Internal assignment **`p`** | User-visible result |
|----------------------------|---------------------|
| **`U_start <= p <= U_end`** | **`planned_at = p`** (normal assigned row) |
| **`p > U_end`** or unassigned internally | **`planned_at = null`**, **`placement: "unassigned"`**; **`timingPain`** at **`p_beyond`** (§8.2) |

- **`P_total`** is minimized over the **full **`H_plan`** assignment** (including days after **`U_end`**).
- **`P_total_reported`**, daily loads **`L(d)`**, **`over_hard_cap`**, and API **`horizonEnd`** reflect **`H_user` only**.
- **Scheduling **`overdue`** / carry-in** still use **`U_start`** as **`H_start`** (user horizon start), not the extended tail.

**What this fixes:** last-day cramming, spurious overflow when work would naturally sit just after the user window, and greedy/local-search edge behavior at the cutoff.

**What it does not fix:** true overload inside **`H_user`**, snooze blocking the whole user window, or mandatory unplanned rows when internal placement lands only in the extended tail (those become user-visible unplanned with **`p_beyond`** pain — intentional).

**Implementation note:** Run **`scheduleHorizon`** through **`H_plan.end`** so forward virtuals exist in the extension; **`F_i`** uses **`H_plan.end`**, not **`U_end`**.

---

## 3. Feasible days `F_i` (minimal)

**Season, allowed weekdays, min gap, interval grid, and archive** are enforced by the **scheduler** when producing **`scheduled_at`**. The planner does **not** re-apply them.

For each instance **i**, build **`F_i`** over the **planning horizon** **`H_plan`** (§2.1), not the user cutoff alone:

```
F_i = { d | U_start <= d <= U_plan_end
          AND (snooze_until is null OR d >= snooze_until) }
```

**`U_start`** / **`U_plan_end`** = bounds of **`H_plan`**.

**User-visible `p_beyond` and reported daily load** still use **`H_user`** = `[U_start, U_end]` (§2.1).

| Rule | Owner |
|------|--------|
| Horizon | Planner |
| Snooze (defer placement) | Planner — **`scheduled_at`** unchanged (pain still uses **s**) |
| Allowed weekdays, season, end date, … | **Scheduler** only |

**`d0_i`** = earliest day in **`F_i`**. Used by Regime B ([pain-model.md](./pain-model.md)).

**Beyond user horizon (reference only, not in user-visible plan):**

```
p_beyond = calendar day immediately after U_end    // user horizon end, not U_plan_end
```

**Unplanned instances** (no **`planned_at`** on the plan) use **timing pain at `p_beyond`** — the same curve as if the work slipped to the first day after the horizon (§8.2). Not a valid assignment day.

---

## 4. Overdue for Regime B (planning)

An instance uses **Regime B** (0 timing pain on **`d0_i`**) when scheduling marks it **`overdue`** at **`H_start`** ([scheduling-model.md](./scheduling-model.md) §9.2) — equivalently **both**:

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

## 7. Daily cap `H_hard` — hard constraint (with overflow)

**`H_hard`** is a **per-day minute limit** on planned work. It is **not** a soft suggestion.

### 7.1 Normal assignment (strict)

When placing instance **i** on day **d**:

```
allow  <=>  L(d) + d_i <= H_hard
```

If no day in **`F_i`** satisfies this, the instance is **not** placed in strict mode (held for overflow pass).

**Effect:** the planner **must** use days other than **`scheduled_at` / grace / `d0`** when the ideal day is full — even if **timing pain** is higher there. That fixes clustering on ideal days when **`H_hard`** would be exceeded.

### 7.2 When may `L(d) > H_hard` happen?

**Only** when it is **impossible** to assign **every** instance to **some** day in its **`F_i`** such that **`L(d) <= H_hard`** for **all** days **d** in the horizon.

Typical impossibility cases:

| Case | Example |
|------|---------|
| **A. Total overload** | Sum of all instance durations **>** total assignable capacity in the horizon (see §7.3). |
| **B. Bin-packing margin** | Aggregate capacity is enough, but **no** placement fits: e.g. **7** days × **`H_hard = 3`**, **8** instances × **2** min — each day fits at most one 2-min task (1 min left); **7** placed, **8th** cannot fit anywhere without exceeding **`H_hard`** on some day. |
| **C. Single instance too large** | One instance has **`d_i > H_hard`** — strict mode can never place it without exceed on that day. |

In those cases the planner runs an **overflow pass** (§7.4). Days with **`L(d) > H_hard`** after overflow get **`over_hard_cap: true`**.

If strict mode succeeds for **all** instances, **`over_hard_cap`** is **false** on **every** day.

### 7.3 Horizon capacity (for diagnostics)

For each calendar day **d** in `[H_start, H_end]`:

```
cap(d) = H_hard   (same for all days in v1)
C_total = sum over days d in horizon of cap(d)
D_total = sum of d_i over all instances to plan
```

- **`D_total > C_total`** → case **A** (total overload).  
- **`D_total <= C_total`** but strict assignment fails → case **B** (packing).  
- **`max(d_i) > H_hard`** → case **C**.

### 7.4 Overflow pass

If strict assignment leaves **unassigned** instances:

1. Set warning **`plan_underflow_strict_cap`** (and subtype **A** / **B** / **C** if detected).  
2. Assign remaining instances minimizing **`P_total`**, **allowing** **`L(d) > H_hard`**.  
3. Flag each day with **`L(d) > H_hard`** as **`over_hard_cap`**.

Prefer placing overflow on days with **most remaining slack** first, then by pain — details in §9.

Instances still unassignable after overflow → **`unassigned_instances`** (§8).

### 7.5 Mandatory assignment

If **`F_i`** is **non-empty**, instance **i** **must** receive a **`planned_at`** in strict or overflow phase.

**Unassigned** ( **`planned_at` null** ) only when **`F_i`** is **empty** (e.g. entire horizon before **`snooze_until`**, or no instance in horizon scope from scheduler).

### 7.6 Pain

**`P_daily`** uses **`rho(L(d))`** for the **final** loads (including overflow). High **`L(d)`** on overflow days is both flagged and penalized by pain.

---

## 8. Pain and unassigned instances

### 8.1 Assigned instances

```
P_total = P_timing + P_daily
```

- **Timing:** Regime A/B + backlog **`M(c)`** — [pain-model.md](./pain-model.md).
- **Daily:** **`rho(L(d))`** for each day **d** in the horizon (final loads, including overflow).

Minimize **`P_total`** over assignments (subject to §7).

### 8.2 Unplanned instances (on the plan, not placed)

Instances without a **`planned_at`** appear on the plan with **`plannedAt: null`**, **`placement: "unassigned"`**. Typical case: **`F_i` empty** (e.g. snooze blocks the whole horizon).

**Rule:** use **timing pain for the day after `H_end`** — evaluate the usual timing curve at **`p_beyond`**, not a new formula:

```
timing_pain_unassigned(i) = timing_pain(i, p = p_beyond)
```

- Use **Regime A** only (**`p_beyond`** is never **`d0`** and not in the horizon).
- Apply backlog **`M(c)`** the same as for assigned instances.
- **No **`P_daily`** contribution** (no minutes on any horizon day).

**Plan totals:**

```
P_total_reported = P_timing + P_daily + sum over unassigned i of timing_pain_unassigned(i)
```

This makes leaving work **outside the horizon** cost the same as slipping it to the **first day after the horizon** — important tasks (high **`w`**, backlog **`M(c)`**) score high, without a new penalty type.

**Optimizer note:** When **`F_i`** is non-empty, assignment is **mandatory**; reference pain is for **reporting** and comparing plans. When comparing alternative horizons in the UI, unassigned reference pain shows cost of “not in this window.”

No pain cap; compare **`P_total_reported`** to **`P*`** for UI only.

---

## 9. Planner algorithm (v1)

### 9.1 Initialization order

Process instances in this priority (stable sort):

1. Backlog virtuals and overdue (Regime B eligible) first  
2. Higher **`w`** (importance)  
3. Earlier **`scheduled_at`**  
4. Lower task id (tie-break)

### 9.2 Greedy assignment (two phases)

#### Phase 1 — strict `H_hard`

For each instance **i** in order:

```
best = null
for each day d in F_i in chronological order:
    if L(d) + d_i > H_hard:
        skip d
    if assigning i -> d keeps same-day ordering potentially satisfiable:
        delta_P = marginal increase in P_total
        pick d with minimum delta_P (tie: earlier d)
if best found:
    assign i to best.d
else:
    leave i unassigned for overflow pass
```

#### Phase 2 — overflow (only if unassigned remain)

Run §7.4: assign remaining instances allowing **`L(d) > H_hard`**, minimize **`P_total`**.

**Marginal pain** in both phases includes:

- Change in **timing_pain** (Regime A/B, **`M(c)`** for backlog virtuals)  
- Change in **`rho(L(d))`** for affected days  

### 9.3 Local search

Repeat until no improvement or **max_iterations** (e.g. 1000):

- **Move / swap** only among assignments that satisfy **`L(d) <= H_hard`** if **strict mode succeeded for all instances**.  
- If **overflow** was used, allow moves that keep **`P_total`** lower; may keep or reduce **`over_hard_cap`** days.

Re-run within-day topological sort after each change.

### 9.4 Output ordering

After optimization, compute **`within_day_order`** per day (section 5).

---

## 10. `PlanResult` (API shape)

```json
{
  "horizonStart": "2026-06-01",
  "horizonEnd": "2026-06-07",
  "pTotal": 48.0,
  "pTiming": 35.0,
  "pTimingUnassigned": 6.0,
  "pDaily": 12.5,
  "pStar": 100,
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
    },
    {
      "instanceKey": "...",
      "taskId": "...",
      "scheduledAt": "2026-05-20",
      "plannedAt": null,
      "placement": "unassigned",
      "timingPain": 6.0,
      "durationMinutes": 10
    }
  ],
  "warnings": ["plan_underflow_strict_cap"]
}
```

- **`pTotal`** = assigned timing + daily + unassigned reference timing (§8.2).  
- **`warnings`:** e.g. `ordering_cycle`, `plan_underflow_strict_cap`, `unassigned_instances`, …

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
| Overload day | Only after overflow pass; **`overHardCap`** true |
| Strict cap | Instances spread off **`d0`** when ideal day full |
| 7×3 min cap, 8×2 min tasks | 7 assigned strict; 8th triggers overflow / warning |
| A before B same day | B always **`within_day_order` > A** |
| Snooze blocks whole horizon | **`unassigned`**, **`timingPain`** = pain at **`p_beyond`** |
| In-grace carry-in | **`scheduled_at < H_start`**, grace covers **`H_start`** → **`overdue = false`**, Regime A on **`d0`** |
| Slip past horizon in grace | Unplanned; **`timingPain`** at **`p_beyond`** low if **`p_beyond`** still in grace |
| Extended horizon (2×) | Internal assign on day **`U_end + 1`** → user view **unplanned**; no last-day cram |
| Unassigned on plan | **`plannedAt: null`**, included in **`pTimingUnassigned`** |
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
| 0.3.3 | Extended planning horizon (default 2×), truncate to user window |
| 0.3.2 | **`overdue`** aligned with scheduling grace rule; carry-in cross-ref |
| 0.3.1 | Unplanned instances: timing pain on day after **`H_end`** |
| 0.3 | Minimal **`F_i`** (horizon + snooze); unassigned pain at **`p_beyond`**; mandatory assign |
| 0.2 | **`H_hard`** strict + overflow pass; bin-packing infeasibility |
| 0.1 | Initial: pipeline, F_i, ordering, ephemeral planned_at |
