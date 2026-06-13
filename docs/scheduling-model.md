# Scheduling model — specification (v0.5.3)

Readable formulas only. Defines **when instances exist** and their **scheduled** times. Day assignment for workload is [pain-model.md](./pain-model.md) (**planning**).

**Status:** Decisions from TBD review locked for v1 implementation.

---

## 1. Scheduling vs planning

| Step | Question | Output |
|------|----------|--------|
| **Scheduling** | When must this occurrence exist (ideal time)? | **Scheduled** date `s` per instance |
| **Planning** | Which calendar day should I do it in this horizon? | **Planned** date `p` |

Scheduling runs **before** planning. Planning reads scheduled instances (open backlog + ephemeral horizon projection). **Season, weekdays, and interval grid** are applied here so the planner only needs horizon + snooze ([planning-algorithm.md](./planning-algorithm.md) §3).

---

## 2. Core objects

| Object | Meaning |
|--------|---------|
| **Task** | Template: rules, tags, duration, pain params |
| **Instance** | One occurrence: **open** or **completed** |
| **Open instance** | Not completed; has `scheduled_at` (ideal `s`), optional `snooze_until` |
| **Completion** | `completed_at`, `scheduled_at`, `planned_at` (optional) |

### 2.1 Persistence (v1)

- Do **not** store all future instances in the database.
- **Database holds:**
  - **`next_scheduled`** — next forward slot (when not in backlog).
  - **Catch-up backlog** (catch-up **yes** only): **`last_missed_scheduled_at`** + **`catch_up_count`** + **`last_reconciled_date`** (see section 3.3). Individual older missed dates are **not** stored.
  - **Open instance** row(s) for catch-up **no**, external due, or a single holder for snooze — **≤ 1** per task when `catch_up = false`.
  - **Completion** rows — historical mark-done events only (no persisted scheduled/planned instance rows for wall-clock past).
- **Wall-clock past** (calendar days **`< today`**): completions + aggregate fields above only — not “before `H_start`”.
- **Horizon** `[H_start, H_end]` and the **gap** **`[today, H_start)`** when **`H_start > today`**: instances are **projected in memory** only (sections 7–8).

### 2.2 Dates and timezone

- **Scheduling and planning use calendar dates** (start of day in the **JVM system default timezone**).
- **`preferred_time`** (optional, HH:mm on task): display and within-day ordering only; does not shift `scheduled_at` in v1.

---

## 3. Open instances — when they are created

### 3.1 On read (locked)

Whenever the system evaluates a task (plan request, task list refresh, mark done, etc.):

```
if task has next_scheduled (or current cycle due) and now > end of that scheduled day
   and instance not completed:
       ensure an open instance exists for that obligation
```

- **Catch-up yes:** increment **`catch_up_count`** for newly lapsed grid slots since **`last_reconciled_date`** (section 3.3). Mark done decrements; no full grid rescan from epoch.
- **Catch-up no:** **at most one open** per task (section 3.2).

No nightly batch required in v1.

### 3.2 Catch-up no — single open invariant

```
catch_up = false  =>  count(open instances for task) <= 1
```

- On read: if the scheduled day has passed and the obligation is open, **reuse** the existing open row or create **one**; do **not** add a new open per missed cycle.
- **Mark done:** complete that instance; recompute `next_scheduled`; **cancel any other open** rows for that task (safety — should be none).

### 3.3 Catch-up yes — last missed + count

**Catch-up is independent of interval type.** It applies to any recurrence (every N days, every N weeks, nth weekday of month, etc.). What varies is how **missed slots** are counted on the task’s schedule **grid**, not whether the task is “daily”.

Backlog is stored compactly:

| Field | Meaning |
|-------|---------|
| **`last_missed_scheduled_at`** | Scheduled date of the **most recent** missed obligation on the grid. |
| **`catch_up_count`** | How many completions still owed (integer ≥ 0). |
| **`last_reconciled_date`** | Watermark: last calendar day catch-up increment was applied through (section 3.3). |

**Older missed dates are not stored.** Only “how many”, “last”, and the reconcile watermark matter.

**Invariant:** when **`catch_up_count == 0`**, the task is **fully caught up** as of the last mark done that cleared the backlog.

**Wall-clock past vs horizon gap**

| Region | Boundary | Persisted? | Catch-up increment? |
|--------|----------|------------|---------------------|
| **Wall-clock past** | grid slot **`S < today`** | Completions + aggregate backlog fields only | **Yes** — on reconcile (below) |
| **Horizon gap** | **`today ≤ S < H_start`** when **`H_start > today`** | **No** — virtual + ephemeral assumptions (section 8) | **No** — those days have not lapsed in wall-clock time |
| **Horizon body** | **`H_start ≤ S ≤ H_end`** | Virtual only (section 7) | No |

**On read** (`catch_up = true`) — **incremental** reconcile:

```
watermark = last_reconciled_date
for each grid slot S where watermark < S < today:
    catch_up_count += 1
    last_missed_scheduled_at = S   // keep the latest S
last_reconciled_date = today
```

- Only slots that **newly entered wall-clock past** since the last reconcile are counted. No full grid rescan from epoch; **completions are not matched per slot** on read.
- After a long idle period, one reconcile walks **`(last_reconciled_date, today)`** in a single pass.
- **On task create:** set **`last_reconciled_date = today`** (do not backfill misses before the task existed).

**Mark done:**

```
catch_up_count -= 1
append completion row
completion.scheduled_at = last_missed_scheduled_at   // reference for pain / UI
if catch_up_count == 0:
    clear last_missed_scheduled_at; recompute next_scheduled
// last_reconciled_date unchanged
```

- While **`catch_up_count > 1`**, **`last_missed_scheduled_at`** stays fixed (e.g. “3× last due Wed” → mark once → “2× last due Wed”).

**Cold start / repair:** optional bootstrap — assume caught up at last completion, then count grid slots in **`(day after last completion, yesterday)`** once; or manual backlog reset.

- The **grid** comes from the task’s interval rules (section 5.1 / 5.1.1 — not “calendar days” unless **`every_n_days`** with **`n = 1`**).
- Example: **every 3rd Tuesday of the month** — three missed months → `catch_up_count = 3`, `last_missed` = date of the **third** (most recent) missed Tuesday.
- Example: **every 1 day** (15 items) — one increment per newly lapsed daily slot.
- Example: **every 1.333 days** — epoch-anchored grid skips ~**1/4** of days long-term; missed **grid slots** increment catch-up, not every blank calendar day.

**Planning / horizon:** expand backlog into **`catch_up_count` virtual instances**, each with **`scheduled_at = last_missed_scheduled_at`** (same `s` for pain/planner). Do not invent dates for earlier misses.

**UI:** may show e.g. “3× (last due Wed)” instead of three separate historical dates.

---

## 4. Snooze (locked)

- **`scheduled_at`** (`s`): ideal date from rules — **unchanged** by snooze (used by planning pain / Regime A).
- **`snooze_until`**: optional date on open instance; planner **`F_i`** excludes days before this (see planning-algorithm §3).

Scheduling does not move `s` when user snoozes.

---

## 5. Rule dimensions

Composable per task (rules editor, no presets).

### 5.1 Interval (v1 implemented)

Stored as structured JSON (validated in Java), not a free-form DSL string.

| Type | Parameter **`n`** | Unit |
|------|-------------------|------|
| `every_n_days` | float **`n >= 1`** (§5.1.1) | calendar days |
| `every_n_weeks` | float **`n >= 1`** | weeks (× 7 days in grid) |
| `every_n_months` | float **`n >= 1`** | mean month (× 365.2425/12 days in grid) |
| `every_n_years` | float **`n >= 1`** | mean year (× 365.2425 days in grid); may follow days/weeks/months in implementation order |

Example JSON: `{ "type": "every_n_days", "n": 1.333 }`.

**nth weekday of month** (e.g. 3rd Tuesday): separate interval type; **integer** only (no fractional **n**). Supported for grid walk / catch-up; may follow **days/weeks/months/years** in implementation order.

### 5.1.1 Fractional **`n`** (every N days/weeks/months/years)

**`n`** may be a **floating-point** value **≥ 1** (e.g. **1.333**, **1.5**, **7.25**). Integer **`n`** behaves as before (**`n = 1`** on **`every_n_days`** = every calendar day).

**Use case:** **`every_n_days`** with **`n = 1.333`** (≈ **4/3**) is “mostly daily” but the grid **skips** roughly **1/4** of calendar days in the long run. With **`n = 1.5`**, long-run skip rate is about **1/3** (**`1 − 1/n`** calendar days on a dense day grid when **`n > 1`**). For cadences shorter than one full unit (e.g. twice per week), use a smaller unit type (**`every_n_days`**) or a smaller **`n`** on that unit — not **`n < 1`**.

Scheduling still uses **calendar dates** (section 2.2). Fractional **`n`** defines an **epoch-anchored slot grid**, not a separate “skip random days” rule.

**Unit length (days)** for grid math:

```
unitDays(every_n_days)   = 1
unitDays(every_n_weeks)  = 7
unitDays(every_n_months) = 365.2425 / 12
unitDays(every_n_years)  = 365.2425

intervalDays = n * unitDays(type)
```

**Slot dates** from **`epoch_start`** (slot index **`k = 0, 1, 2, …`**):

```
E = dayIndex(epoch_start)    // 0-based local calendar index

slotDayIndex(k) = E + k * intervalDays
gridSlotDate(k) = calendarDate(floor(slotDayIndex(k) + 1e-9))
```

- **`gridSlotDate(0) = epoch_start`** (after weekday / seasonal snap applied when the epoch was set).
- **`nextScheduled`** (epoch anchor): smallest **`gridSlotDate(k) >= after_date`** after allowed-weekday, seasonal, and min-gap rules (section 6).
- **Catch-up walk** (section 3.3): iterate **`k`** while **`gridSlotDate(k) <= today`**; each distinct slot date is one obligation on the grid.
- **At most one instance per calendar day** from the interval grid: if **`gridSlotDate(k) == gridSlotDate(k-1)`**, treat as a single slot (dedupe consecutive **`k`**).

**Last-completion anchor:** after mark done, next candidate = **`completed_date + intervalDays`** (as calendar-day add), then weekday / seasonal / min-gap — same as integer intervals; the slot grid is not re-anchored to completion unless the user changes epoch / interval.

**Interval ±%** (mark done): multiply stored **`n`** by the factor (e.g. **1.333 → 1.466** at +10%). Result must stay **`>= 1`**; if the product **< 1**, clamp to **`1`**.

**Validation:** **`n >= 1`** required on create/edit and after ±%. Reject **`n < 1`**. UI may show “≈ every *n* …” and long-run skip rate **≈ `1 − 1/n`** of calendar days on **`every_n_days`** when **`n > 1`**.

### 5.2 Epoch

- **`epoch_start`**: first **scheduled** date of the current epoch (not first completion).
- **On task create:** default `epoch_start` = **next computed slot from today** (user may edit in UI).
- **On interval ±% at mark done:** new epoch; `epoch_start` = new `next_scheduled` after adjustment.

### 5.3 Anchor

| Mode | Next slot after completion |
|------|----------------------------|
| **Epoch** | Next grid slot from `epoch_start` + interval (completion day irrelevant) |
| **Last completion** | From `completed_at` + interval (then constraints) |

### 5.4 Allowed weekdays

If raw candidate falls on a disallowed day: snap to **nearest allowed** calendar day (may move backward or forward).

### 5.5 Seasonal window

Only schedule inside `[window_start, window_end]` each year; shift candidate into window or skip per rule logic.

### 5.6 Min days between scheduled instances

When generating **new** forward slots (grid / `next_scheduled`):

- If gap from previous **scheduled** date < minimum → **push candidate forward** until gap OK.

**Catch-up exception:** **min gap** applies to forward **`next_scheduled`** generation, not to splitting the backlog (count + last missed are exempt from per-day gap rules).

### 5.7 End date / archive

No new instances after `end_date`. Catch-up: remaining opens stay until done.

### 5.8 Catch-up flag

Orthogonal to interval expression (days / weeks / months / nth weekday / …).

| `catch_up` | Backlog | Mark done |
|------------|---------|-----------|
| **true** | `last_missed_scheduled_at` + `catch_up_count` | `count -= 1` |
| **false** | ≤ 1 open | Clears obligation; cancel other opens (safety) |

### 5.9 External due function

Per task:

- **`script_path`** (or command)
- **`script_args`** (optional stored arguments)

**Invocation:** subprocess; read **stdout**.

| Condition | Result | UX |
|-----------|--------|-----|
| Line `true` / `false` (case-insensitive, trimmed) | That value | — |
| No valid line on stdout | **false** | **Warn user** (misconfigured script) |
| Exit code != 0 | **false** (or abort read) | **Warn user** (script error) |
| Timeout | **false** | Warn user |

- **`false`:** no new instance from external rule; existing opens unchanged.
- **`true`:** ensure one open instance with `scheduled_at` = **ASAP** = start of **today** if allowed, else next feasible day (weekdays / snooze).

---

## 6. `nextScheduled` (steady state)

```
function nextScheduled(task, after_date):
    if archived: return none
    if external:
        if due() == true: return ASAP(after_date)
        else: return none
    if anchor == LAST_COMPLETION:
        base = last_completion_date or task.created_date
        candidate = base + interval
    if anchor == EPOCH:
        candidate = next grid slot >= after_date from epoch_start
    candidate = nearest_allowed_weekday(candidate)
    candidate = apply seasonal window
    candidate = apply min_gap from last scheduled (forward push only)
    return candidate
```

**After mark done:** recompute `next_scheduled`; apply catch-up rules (section 3).

**Interval ±%:** only at mark done; update interval; new epoch; `epoch_start` = new next scheduled; no chain of persisted futures.

---

## 7. Horizon projection (ephemeral — locked)

On `scheduleHorizon(tasks, H_start, H_end)`:

```
result = []

for each task:
    reconcileOpensOnRead(task)                    // section 3

    if catch_up and catch_up_count > 0:
        repeat catch_up_count times:
            result += VirtualInstance(last_missed_scheduled_at)
            mark overdue per section 9.2 (reference = H_start)

    else if open instance (catch_up no / external):
        result += persisted open
        mark overdue per section 9.2 (reference = H_start)

    // Carry-in (section 7.1): open obligations with scheduled_at < H_start whose grace
    // still covers H_start are included above; overdue = false (section 9.2)

    apply horizon assumptions for (now, H_start)  // section 8

    virtual = next_scheduled
    while virtual in [H_start, H_end] and before end_date:
        result += VirtualInstance(virtual)        // NOT persisted; overdue = false
        virtual = advance(virtual, rules)

return result
```

- **Virtual instances** exist only for the response (planning input).
- **Never INSERT** horizon walk results into the database.
- **Catch-up yes:** **`catch_up_count`** virtuals at **`last_missed_scheduled_at`**, plus forward **virtual** slots from `next_scheduled`.
- **Catch-up no:** at most one **open**; virtuals for forward preview only.

Each instance in the result carries scheduling metadata for planning (at minimum **`scheduled_at`**, optional **`overdue`** flag per section 9).

### 7.1 Carry-in — in grace before `H_start`

The planner may leave work **unplanned** in the horizon; unplanned timing pain uses **`p_beyond`** ([pain-model.md](./pain-model.md) §3.2). If **`p_beyond`** still lies inside the instance’s grace band, that reference pain can be **low or zero** — acceptable.

For that to behave consistently, **`scheduleHorizon`** must **include** open obligations whose ideal date is **before `H_start`** when **grace still extends past `H_start`**:

```
carry_in(i) <=>
    instance i is open (not completed)
    and scheduled_at(i) < H_start
    and in_grace_at(H_start, scheduled_at(i), g_early, g_late)   // section 9.1
```

| Property | Value |
|----------|--------|
| **Included in horizon output** | yes (via open row or backlog virtuals — section 7 loop) |
| **`overdue`** | **false** (section 9.2) |
| **Regime B (planning)** | not eligible ([planning-algorithm.md](./planning-algorithm.md) §4) |

**Example:** task scheduled **Mon**, **`g_late = 7`**, horizon **`H_start` = Wed** … **`H_end` = Sun**. Grace runs through **Mon+7**; **Wed ∈ grace** → instance appears in the schedule output, **`overdue = false`**. If the planner leaves it unplaced, **`p_beyond` = Mon** (day after **Sun**) may still be inside grace → low reference pain.

When grace has **ended** at **`H_start`** (`scheduled_at < H_start` but outside the band), the same obligation is still included, but **`overdue = true`** (backlog / Regime B eligible).

---

## 8. Horizon assumptions (future start)

When **`H_start > today`** (plan starts on a **future calendar day**):

**Gap** = grid slots **`S`** with **`today ≤ S < H_start`**. These days are still **wall-clock present or future** — they are **not** wall-clock past and **do not** affect **`catch_up_count`** or **`last_reconciled_date`**.

For each slot **`S`** in the gap:

| Condition | For this plan run |
|-----------|-------------------|
| **`S`** is **not yet due** at **`today`** (calendar day **`S`** has not ended — typically **`S ≥ today`**) | Emit as **virtual** if needed; treat as **assumed completed** on **`S`** (no persisted completion, no backlog change) |
| **`S`** is **due** at **`today`** (`S < today` cannot occur in the gap; use when evaluating carry-in at **`H_start`**) | Remains **open / overdue** in horizon output |

**Ephemeral only:** assumptions apply to **`scheduleHorizon` / planning input** for that request. They **do not** write completion rows, **do not** move **`last_reconciled_date`**, and **do not** persistently change **`catch_up_count`**. When **`S`** later becomes wall-clock past, reconcile (section 3.3) applies normally.

When **`H_start = today`**: use actual DB state only; no gap assumptions.

---

## 9. Overdue and grace (scheduling)

Open creation (section 3) runs when the **scheduled calendar day has passed** — independent of grace. **Overdue** is a separate label used by the UI and planning (Regime B); it is **grace-aware**.

### 9.1 Grace band

Per task, same parameters as [pain-model.md](./pain-model.md):

```
grace_band(s) = [s - g_early, s + g_late]   // inclusive calendar days

in_grace_at(reference_day, s, g_early, g_late) <=>
    reference_day is in grace_band(s)
```

### 9.2 Overdue flag

**At horizon planning** (reference day = **`H_start`**; when **`H_start = today`**, same as “as of today”):

```
overdue(instance, H_start) <=>
    not completed
    and scheduled_at < H_start
    and NOT in_grace_at(H_start, scheduled_at, g_early, g_late)
```

Equivalently: **`scheduled_at < H_start`** and grace has **fully ended** before **`H_start`**:

```
index(H_start) - index(scheduled_at) > g_late
```

(when **`scheduled_at < H_start`**, the early-grace side does not apply.)

**Forward** instances with **`scheduled_at >= H_start`** (horizon virtuals): **`overdue = false`**.

**General UI** (task list, “overdue” badge when no horizon is selected): use **`reference_day = today`** with the same formula.

Planning Regime B uses the same rule at **`H_start`** ([planning-algorithm.md](./planning-algorithm.md) §4).

### 9.3 Catch-up count vs overdue

**On-read** catch-up increment (section 3.3) counts grid slots with **`S < today`** newly since **`last_reconciled_date`** — it does **not** wait for grace to end. A slot may appear in **`catch_up_count`** while **`overdue(H_start) = false`** when grace still covers **`H_start`**. The **`overdue`** flag on horizon output is what planning and the UI use. **Horizon gap** slots (**`≥ today`**) are excluded from catch-up increment.

---

## 10. Example tasks

### DNS (external due)

- Script per task with optional args; stdout `true`/`false`.
- `true` → one open, ASAP `scheduled_at`.
- `false` → no new open.

### 15 items (every day, catch-up yes)

- Grid: **`every_n_days`**, **`n = 1`**.
- On read: increment for each daily grid slot in **`(last_reconciled_date, today)`**; `last_missed` = latest such slot.
- Mark done: **`count -= 1`**.

### Sparse daily (every 1.333 days, catch-up yes) — illustration

- Grid: **`every_n_days`**, **`n = 1.333`** (≈ **4/3**); slot dates from **`epoch_start`** via §5.1.1.
- Long run: ~**3** scheduled slots per **4** calendar days (~**25%** of days have no slot).
- Catch-up counts **missed grid slots**, not “off” days with no slot.

### 3rd Tuesday monthly (catch-up yes) — illustration

- Grid: one slot per month on the 3rd Tuesday.
- Miss Jan–Mar: `catch_up_count = 3`, `last_missed` = March’s Tuesday (not Jan/Feb dates stored).
- Plan: three virtual instances, each with `scheduled_at = last_missed` for pain; user still completes three times.

### Fingernails (last completion, catch-up no)

- **≤ 1 open**; `next_scheduled` from last completion + N days.
- Missed cycles do not spawn extra opens.
- Mark done: recompute next; cancel stray opens (safety).

### Call father (epoch, weekly, Sat/Sun, catch-up no)

- `epoch_start` default from create (editable).
- Grid + **nearest** allowed weekday + **min 7 days** between **scheduled** forward slots.
- Done Monday: next **scheduled** still next Sat/Sun from epoch.

---

## 11. Decision log (TBD review)

| # | Topic | Decision |
|---|--------|----------|
| 1 | Open creation | On read |
| 2 | Snooze | Keep `scheduled_at`; `snooze_until` |
| 3 | Date/time | Date + `preferred_time` label |
| 4 | Timezone | System default |
| 5 | Intervals | days/weeks/months/years; fractional **`n`** on **`every_n_*`** (§5.1.1) |
| 6 | Weekdays | Nearest allowed |
| 7 | Min gap | Push forward; catch-up opens exempt |
| 8 | due() | stdout; warn on error; missing line → false |
| 9 | Script | Path + args per task |
| 10 | Horizon | Ephemeral projection only |
| 11 | Catch-up backlog | `last_missed_scheduled_at` + `catch_up_count` + `last_reconciled_date` |
| 12 | Catch-up no | ≤ 1 open; safety cancel on mark done |
| 13 | Epoch on create | Default next slot from today (editable) |
| 14 | Grace / overdue | Overdue when grace ended at reference day; carry-in before **`H_start`** in grace **`overdue = false`** |
| 15 | Fractional **`n`** | Float **`n`** on **`every_n_*`**; epoch slot grid (§5.1.1) |

---

## 12. Related documents

| Doc | Role |
|-----|------|
| [requirements.md](./requirements.md) | Product rules |
| [pain-model.md](./pain-model.md) | Planning / pain |
| [tech-stack.md](./tech-stack.md) | Stack (if present) |

---

## 13. Changelog

| Version | Notes |
|---------|--------|
| 0.5.3 | Incremental catch-up via **`last_reconciled_date`**; wall-clock past vs horizon gap; ephemeral §8 assumptions |
| 0.5.2 | **`n >= 1`** required; **`n < 1`** rejected; ±% clamps to **`1`** |
| 0.5.1 | ~~**`n < 1`** valid~~ (superseded by 0.5.2) |
| 0.5 | Fractional **`n`** on **`every_n_days/weeks/months/years`**; epoch slot grid (§5.1.1) |
| 0.4 | Grace-aware **`overdue`**; carry-in before **`H_start`** in grace (not overdue); aligns with **`p_beyond`** slip |
| 0.3 | Catch-up: last missed + count; planner uses minimal **`F_i`** (horizon + snooze only) |
| 0.2 | TBD review locked; ephemeral horizon; snooze; on-read opens; due script contract |
| 0.1 | Coarse draft |
