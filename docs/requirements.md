# Personal maintenance planner — Requirements v0.3.7

Status: **Draft for sign-off**. Planning: [planning-algorithm.md](./planning-algorithm.md). Tech stack: [tech-stack.md](./tech-stack.md).

## 1. Purpose

Single-user, localhost application to define maintenance tasks with composable scheduling rules, record completions, tune intervals at completion time, and produce **schedules** (when instances exist) and **plans** (which day to do them) for a chosen week or month.

**Out of scope (v1):** authentication, multi-user, sync, import/export, “failed” tasks, weather/external sensors, named presets (rules editor only), push notifications.

**Non-functional:** runs on one machine; localhost; stable (no random crashes); data in a local database.

---

## 2. Glossary

| Term | Meaning |
|------|--------|
| **Task** | Long-lived definition (rules, tags, duration, etc.). |
| **Epoch** | Interval after an interval change; anchored by the **first scheduled** date/time of that epoch. |
| **Instance** | One occurrence that must be done or was done. |
| **Scheduled at** | Rule-based ideal/existence time (scheduling step). |
| **Planned at** | Calendar assignment (planning step). |
| **Completion** | User marked done; stores timestamps only. |
| **Overdue** | Scheduled time in the past, not completed. |
| **Snooze** | Per-instance: do not schedule/plan before the given date/time. |

**Persistence:** Do not store long lists of future instances. Persist **next scheduled** (and any **open** instances required by catch-up rules). Horizon views are computed on demand.

---

## 3. Scheduling and planning

### 3.1 Scheduling (instance generation)

Computes **scheduled** instances in `[H_start, H_end]`, plus **open obligations before `H_start`** (overdue backlog and **in-grace carry-in** — see [scheduling-model.md](./scheduling-model.md) §7.1, §9).

- **Snooze** affects scheduling.
- **Overdue** (grace ended at **`H_start`**) instances are included; planning may use Regime B. **In-grace carry-in** (`scheduled_at < H_start` but grace covers **`H_start`**) is included with **`overdue = false`**.
- **Due function:** returns `true` (due) or `false` (not due); when due, schedule ASAP as rules allow.
- **Archived** (`end_date` past): no **new** instances; **catch-up exception:** outstanding missed instances remain until completed.

### 3.2 Planning (calendar assignment)

Assigns **planned** dates/times within the horizon using flexibility, importance, duration, same-day ordering, and daily time/pain rules.

- **Planning lookahead:** optimize on an **extended horizon** (default **2×** user span), then **truncate** to the requested window ([planning-algorithm.md](./planning-algorithm.md) §2.1).
- **Same-day ordering:** hard constraint (cosmetic).
- **Preferred time of day:** display hint in v1 unless specified later.
- **Unplanned instances** (shown on the plan but not placed in the horizon): reported timing pain uses the **day immediately after `H_end`** — see [pain-model.md](./pain-model.md) §3.2.
- **Plan output:** each instance row includes **`timingPain`** (assigned and unplanned) — [planning-algorithm.md](./planning-algorithm.md) §10.1.

### 3.3 Horizon assumptions

Let **now** = current time, **H_start** = horizon start.

| Case | Behavior |
|------|----------|
| **H_start = now** | No assumed completions. Use actual state. |
| **H_start > now** | Per task, in **(now, H_start)**: if **≥ 1** scheduled instance exists that is **not yet due**, assume it is completed **on its scheduled date** at **H_start**. Otherwise, instances that are **due** by **H_start** remain **due**. |

---

## 4. Rule dimensions (composable editor)

### 4.1 Recurrence and anchors

- **Interval expression** (e.g. every *n* days/weeks/months/years with fractional *n* **≥ 1**; nth weekday of month; etc.). See [scheduling-model.md](./scheduling-model.md) §5.1.1.
- **Epoch:** first **scheduled** date/time; recalculate epoch when interval changes.
- **Anchor mode:**
  - **Epoch / first scheduled:** series follows calendar from epoch (completion on another day does not shift the series).
  - **Last completion:** next instance derived from when the user marked done (e.g. fingernails).
- **Allowed weekdays** (e.g. Saturday and Sunday only). No special “weekend” type.
- **Seasonal window** (only schedule between given dates).
- **End date:** after this, task is archived; no new instances (catch-up exception below).
- **Minimum days between scheduled instances** (optional): enforces a minimum gap between two **scheduled** instances of the same task (resolves “Sat + Sun + every 7 days” vs one slot per week).
- **Catch-up:** if yes, missed instances remain until done (any interval — daily, weekly, nth weekday of month, etc.); if no, marking done clears obligation for missed instances (no doubling). Independent of interval type.

### 4.2 Flexibility and priority

- **Flexibility curve:** pain vs distance from scheduled day; asymmetric early vs late.
- **Importance:** weight when competing for days.

### 4.3 Capacity

- **Duration** (minutes per instance).

### 4.4 External and grouping

- **Due function:** `true` / `false`.
- **Same-day order:** hard ordering with other tasks that day.

### 4.5 Archive and catch-up

- Archive when **end date** is in the past.
- Catch-up tasks: no new instances after end date; existing missed instances stay on the plan until done.

---

## 5. Interval adjustment (±%)

- Offered **only** when marking an instance **done**.
- Only when interval type supports proportional change (**`every_n_*`** with numeric **`n`**, including fractional).
- Updates interval, starts new **epoch**, recomputes **next scheduled** immediately.
- Does not persist a chain of future instances beyond open-instance rules.

---

## 6. Completions

**Stored:** `completed_at`, `scheduled_at`, `planned_at` (when planning ran).

**Not stored:** deltas; compute in UI (e.g. completed − planned).

**Mark done (no catch-up):** satisfies current cycle; missed instances dropped.

**Mark done (catch-up):** decrement backlog count by one; others remain until count is zero.

**Next scheduled:** last-completion anchor → from completion time; epoch anchor → from epoch/interval, not from off-day completion.

---

## 7. Snooze

From **plan** view on the current instance. Affects **scheduling** and planning (nothing before snooze end).

---

## 8. Pain and daily limits

There is **no pain cap**: the planner does **not** reject plans because total pain exceeds a threshold. Pain guides placement and user feedback; feasibility is governed by **hard** rules (including time cap).

### 8.1 Daily minutes

| Setting | Role |
|---------|------|
| **Soft budget** (max minutes/day for planning) | Drives pain below/above knee. |
| **Hard cap** (e.g. 8 h workday or 24 h) | Cannot plan more minutes than this on a day. |

**User-configured pain from daily load:**

- **Threshold** `T` (minutes): pain grows **linearly** from 0 to **pain_at_threshold** at `T`.
- **Above** `T`: plan remains valid; pain increases by **pain_per_minute_over_threshold** × minutes above `T`.
- Plus instance-level flexibility/importance pain.

**Global pain threshold (user setting):** reference / target for planner optimization and UI—not a hard cap on plan validity.

### 8.2 Future (not v1)

Optional “minimum achievable pain” analysis for comparison.

---

## 9. UI (v1)

| View | Contents |
|------|----------|
| **Tasks** | CRUD; tags (name, description); archived; completion history; next scheduled (and open catch-up count). |
| **Plan** | Week or month; planned instances; **mark done** (±% when applicable); **snooze**. |
| **Settings** | Soft max minutes/day, hard daily cap, pain curve parameters (`T`, pain at threshold, pain/min over threshold), global pain threshold (non-cap). |

---

## 10. Example tasks (acceptance sketches)

| Task | Rules (summary) |
|------|-----------------|
| **DNS update** | Due function; when `true`, schedule ASAP; high importance; short duration. |
| **15 items** | Every day; catch-up yes; backlog = count + last missed (see scheduling-model). |
| **Fingernails** | Last completion + *n* days; no catch-up; asymmetric flexibility. |
| **Call father** | Epoch + every 7 days; allowed weekdays Sat/Sun; no catch-up; optional **min days between instances** if one slot per week is desired; epoch anchor. |

---

## 11. Changelog

| Version | Changes |
|---------|---------|
| v0.3.7 | Fractional **`n`**: require **`n >= 1`**. |
| v0.3.6 | ~~**`n < 1`** valid~~ (reverted in v0.3.7) |
| v0.3.5 | Fractional interval **`n`** on every N days/weeks/months/years. |
| v0.3.4 | Plan API: **`timingPain`** on every instance row. |
| v0.3.3 | Extended planning horizon (2×) with truncate to user window. |
| v0.3.2 | Scheduling: in-grace carry-in before horizon; grace-aware overdue. |
| v0.3.1 | Unplanned instances: reported timing pain on day after horizon end. |
| v0.3 | No pain cap; min days between scheduled instances rule; clarified pain threshold is non-cap. |
| v0.2 | Horizon logic; timestamps only; daily pain curve + hard time cap; no “weekend” type. |
| v0.1 | Initial two-step model. |

---

## 12. Sign-off

When approved as **frozen**, proceed to technical preferences and data model / API sketch.
