# Scheduling model — specification (v0.2)

Readable formulas only. Defines **when instances exist** and their **scheduled** times. Day assignment for workload is [pain-model.md](./pain-model.md) (**planning**).

**Status:** Decisions from TBD review locked for v1 implementation.

---

## 1. Scheduling vs planning

| Step | Question | Output |
|------|----------|--------|
| **Scheduling** | When must this occurrence exist (ideal time)? | **Scheduled** date `s` per instance |
| **Planning** | Which calendar day should I do it in this horizon? | **Planned** date `p` |

Scheduling runs **before** planning. Planning reads scheduled instances (open backlog + ephemeral horizon projection).

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
  - **Open instances** (0…many per task, per catch-up rules).
  - **`next_scheduled`** (optional date pointer for the next not-yet-due slot on non-catch-up tasks).
- **Horizon** `[H_start, H_end]`: additional instances are **projected in memory** only (section 7).

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

- **Catch-up yes:** each missed day → **separate open row** with `scheduled_at` = that day (habit).
- **Catch-up no:** **at most one open** per task (section 3.2).

No nightly batch required in v1.

### 3.2 Catch-up no — single open invariant

```
catch_up = false  =>  count(open instances for task) <= 1
```

- On read: if overdue, **reuse** the existing open row or create **one**; do **not** add a new open per missed cycle.
- **Mark done:** complete that instance; recompute `next_scheduled`; **cancel any other open** rows for that task (safety — should be none).

### 3.3 Catch-up yes

- Each missed scheduled day keeps (or gets) its own open instance.
- **Mark done:** remove **only** the completed instance; other opens remain.

---

## 4. Snooze (locked)

- **`scheduled_at`** (`s`): ideal date from rules — **unchanged** by snooze (used by planning pain / Regime A).
- **`snooze_until`**: optional date on open instance; planning cannot place before this day.
- **Feasible days** for planning: `F_i` excludes days before `snooze_until` (see pain-model `d0`).

Scheduling does not move `s` when user snoozes.

---

## 5. Rule dimensions

Composable per task (rules editor, no presets).

### 5.1 Interval (v1 implemented)

Stored as structured JSON (validated in Java), not a free-form DSL string.

| Type | Advance |
|------|---------|
| `every_n_days` | + N calendar days |
| `every_n_weeks` | + N weeks, then weekday rules |
| `every_n_months` | + N calendar months |

**v1 later sketch — `every_n_years` (not implemented first):**

| Type | Advance |
|------|---------|
| `every_n_years` | + N years; same allowed-weekday / seasonal / min-gap pipeline as months |

Defer **nth weekday of month** until after v1 core.

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

**Catch-up exception:** existing **open** instances **keep** their original `scheduled_at` even if two opens are closer than the minimum (e.g. two habit days in a row).

### 5.7 End date / archive

No new instances after `end_date`. Catch-up: remaining opens stay until done.

### 5.8 Catch-up flag

| `catch_up` | Opens | Mark done |
|------------|-------|-----------|
| **true** | Many (one per missed day) | Removes one open |
| **false** | ≤ 1 | Clears obligation; cancel other opens (safety) |

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

    for each open instance with scheduled_at < H_end:
        result += instance                        // persisted

    apply horizon assumptions for (now, H_start)  // section 8

    virtual = next_scheduled
    while virtual in [H_start, H_end] and before end_date:
        result += VirtualInstance(virtual)        // NOT persisted
        virtual = advance(virtual, rules)

return result
```

- **Virtual instances** exist only for the response (planning input).
- **Never INSERT** horizon walk results into the database.
- **Catch-up yes:** result includes all **open** rows (past days) plus **virtual** future slots in range.
- **Catch-up no:** at most one **open**; virtuals for forward preview only.

---

## 8. Horizon assumptions (future start)

When `H_start > now`:

For each task, slots in **(now, H_start)**:

| Condition | At H_start |
|-----------|------------|
| ≥ 1 scheduled slot in gap that is **not yet due** at its day | Assumed **completed** on scheduled date (no open) |
| Otherwise | Slots **due** in gap remain **open / overdue** |

When `H_start = now`: use actual DB state only.

---

## 9. Overdue (scheduling)

```
open instance is overdue  <=>  scheduled_at < today (local)
                              and not completed
```

Planning grace / Regime B: [pain-model.md](./pain-model.md).

---

## 10. Example tasks

### DNS (external due)

- Script per task with optional args; stdout `true`/`false`.
- `true` → one open, ASAP `scheduled_at`.
- `false` → no new open.

### Habit (daily, catch-up yes)

- Each day’s ideal `scheduled_at` = that date.
- On read after midnight: new open for each missed day (**multiple rows**).
- Mark done: delete one open; others remain.

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
| 5 | Intervals | days/weeks/months v1; years sketched |
| 6 | Weekdays | Nearest allowed |
| 7 | Min gap | Push forward; catch-up opens exempt |
| 8 | due() | stdout; warn on error; missing line → false |
| 9 | Script | Path + args per task |
| 10 | Horizon | Ephemeral projection only |
| 11 | Habit backlog | One open per missed day |
| 12 | Catch-up no | ≤ 1 open; safety cancel on mark done |
| 13 | Epoch on create | Default next slot from today (editable) |

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
| 0.2 | TBD review locked; ephemeral horizon; snooze; on-read opens; due script contract |
| 0.1 | Coarse draft |
