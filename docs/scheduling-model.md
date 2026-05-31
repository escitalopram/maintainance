# Scheduling model — coarse specification (v0.1)

Readable formulas only. Defines **when instances exist** and their **scheduled** times. Day assignment for workload is [pain-model.md](./pain-model.md) (**planning**).

**Status:** Coarse first version — refine before implementation.

---

## 1. Scheduling vs planning

| Step | Question | Output |
|------|----------|--------|
| **Scheduling** | When must this occurrence exist (ideal time)? | **Scheduled** date/time `s` per **instance** |
| **Planning** | Which calendar day should I do it in this horizon? | **Planned** date/time `p` |

Scheduling runs **before** planning. Planning reads scheduled instances (including overdue backlog).

---

## 2. Core objects

| Object | Meaning |
|--------|---------|
| **Task** | Template: rules, tags, duration, pain/flexibility params |
| **Instance** | One occurrence that is **open** (must still be done) or **completed** |
| **Open instance** | Not completed; has `scheduled_at` |
| **Completion** | Record: `completed_at`, `scheduled_at`, `planned_at` (optional) |

**Persistence (v1):**

- Do **not** store all future instances in the DB.
- Store per task:
  - **Next scheduled** (one datetime when rules allow a single “upcoming” slot), and/or
  - **Open instances** (queue) when catch-up or ASAP rules require more than one.
- **Horizon schedule** `[H_start, H_end]` is **computed on demand** for plan views.

---

## 3. Scheduling inputs

For each active (non-archived) task:

| Input | Source |
|-------|--------|
| Rule set | Task definition |
| Completions | History |
| Open instances | DB |
| Snooze | Per open instance (if any) |
| `now` | Clock |
| Horizon | `H_start`, `H_end` (for batch generation) |
| Due function | External `true` / `false` (optional) |

**Archived task** (`end_date` in the past):

- Do **not** create **new** instances.
- **Catch-up exception:** existing **open** instances remain until completed.

---

## 4. Scheduling outputs

For a horizon request:

```
schedule(task, H_start, H_end) -> list of instances { scheduled_at, task_id, instance_id? }
```

Includes:

1. All **open** instances with `scheduled_at < H_end` (overdue backlog).
2. **New** instances whose `scheduled_at` falls in `[H_start, H_end]` (per rules).
3. Instances after `H_start` in **(now, H_start)** handled per horizon assumptions (section 8).

Each instance has exactly one **scheduled_at** (date or date-time; v1 may use **start of day** in local timezone).

---

## 5. Rule dimensions (scheduling only)

Composable per task (rules editor, no presets).

### 5.1 Recurrence

**Interval** (coarse types for v1 — exact grammar TBD):

| Type | Meaning | Example |
|------|---------|---------|
| Every N days | Add N calendar days | Fingernails every 14 days |
| Every N weeks | Add N weeks on allowed weekdays | Call father every 1 week on Sat/Sun |
| Every N months | Calendar month step | Filter every 3 months |
| Nth weekday of month | e.g. 2nd Tuesday | Optional v1 |

**Epoch:** after interval change or task creation, store **epoch_start** = first **scheduled** datetime of the new epoch (not first completion).

### 5.2 Anchor (what “next” is relative to)

| Mode | Next scheduled after completion |
|------|----------------------------------|
| **Epoch** | Advance from **epoch** + interval grid (completion date does **not** shift the series) |
| **Last completion** | `scheduled_at = completed_at + interval` (adjusted for allowed weekdays / window) |

**Mark done** on epoch-anchored task: record completion; **next** instance still computed from epoch + interval (e.g. call father done Monday → next slot still weekend).

### 5.3 Constraints on scheduled date

| Rule | Effect |
|------|--------|
| **Allowed weekdays** | Map candidate date to nearest allowed day, or skip until allowed |
| **Seasonal window** | Only schedule inside [window_start, window_end] each year |
| **End date** | No new instances after; archive task |
| **Min days between instances** | Reject/shift candidate if gap from previous **scheduled** < minimum |
| **Snooze** | No instance with `scheduled_at` before snooze end |

### 5.4 Catch-up

| Mode | Missed instance |
|------|-----------------|
| **Catch-up yes** | Stays **open**; new instances still generated; multiple open allowed |
| **Catch-up no** | **Mark done** clears missed obligation; only current cycle matters |

**Habit (daily + catch-up):** each missed day → one open instance; next day may have several open.

### 5.5 External due

```
due(): boolean
```

- If `false`: no new instance from external rule (existing open instances unchanged).
- If `true`: ensure an open instance exists with `scheduled_at` = **ASAP** (earliest valid instant ≥ now, respecting snooze/weekdays).

Implementation v1: subprocess / script; contract only.

---

## 6. Computing the next scheduled date (steady state)

High-level algorithm for one task (not in horizon batch):

```
function nextScheduled(task, after_instant):
    if archived: return none
    if external due:
        if due(): return ASAP(after_instant)
        else: return none
    if anchor == LAST_COMPLETION:
        base = last_completion_at or task.created_at
        candidate = base + interval
    if anchor == EPOCH:
        candidate = next slot on grid from epoch_start + interval
    candidate = apply weekdays, seasonal window, min-gap, snooze
    return candidate
```

**After mark done:**

- **Last completion:** recompute `next_scheduled` from `completed_at`.
- **Epoch:** recompute next slot from epoch grid (unchanged by off-day completion).
- **Catch-up no:** remove other open instances for that task (or mark superseded).
- **Catch-up yes:** remove only the completed instance; recompute if needed.

**Interval ±%** (at mark done only): multiply period by factor; set new **epoch_start** = new next scheduled; persist; do not materialize full future chain.

---

## 7. Horizon batch generation (coarse)

```
scheduleHorizon(tasks, H_start, H_end):
    result = []
    for each task:
        result += open instances with scheduled_at < H_end
        apply horizon assumption in (now, H_start)  // section 8
        walk forward from next_scheduled while scheduled_at in [H_start, H_end]:
            append instance
            advance using anchor + interval + constraints
            enforce min-gap between scheduled dates
        stop at task end_date
    return result
```

**Catch-up:** “walk forward” may still create **one** instance per interval; missed slots remain as separate **open** rows created when due date passes without completion (exact trigger: daily job vs on-read — **TBD**).

**Simpler v1 option:** open instances created only when `now` passes `scheduled_at` without completion; horizon view lists opens + projected next slots.

---

## 8. Horizon assumptions (future start)

When `H_start > now`:

For each task, consider instances in **(now, H_start)**:

| Condition | Assumption at H_start |
|-----------|------------------------|
| ≥ 1 **non-due** scheduled slot in gap | Treated as **completed on scheduled date** (not open) |
| Otherwise | Instances **due** in gap remain **open / overdue** |

When `H_start = now`: use actual open instances and completions only.

---

## 9. Snooze

Snooze applies to an **open instance** (or the current cycle):

- `scheduled_at` must be **≥ snooze_end** (or instance hidden until then).
- Affects **feasible days** for planning (`d0` in pain model).

Reschedule: recompute `scheduled_at` = max(original, snooze_end) or keep open with deferred eligibility — **TBD**; prefer **eligibility** without losing “ideal” `s` for pain.

---

## 10. Overdue (scheduling sense)

Instance is **overdue** when:

```
scheduled_at < now   (or < H_start when planning)
AND not completed
```

Grace (for **planning** pain) is separate; see [pain-model.md](./pain-model.md).

---

## 11. Example tasks (scheduling behaviour)

### DNS (external due)

- `due() == true` → open instance, `scheduled_at` = ASAP.
- `due() == false` → no new instance.
- Planner puts ASAP instances early (pain/importance).

### Habit (daily, catch-up)

- Every day: ideal `scheduled_at` = that day 00:00 (or preferred time).
- Miss day D → open instance for D remains; day D+1 adds another open instance.
- Mark done one instance → removes that open only.

### Fingernails (last completion + N days)

- `scheduled_at = last_completed + N days` (weekday adjust if configured).
- Catch-up **no**: one open; mark done → recompute next; drop missed.
- No second open for “missed” cycle.

### Call father (epoch + weekly, Sat/Sun only, catch-up no)

- `epoch_start` = first scheduled Saturday (example).
- Grid: every 7 days, snap to allowed weekday.
- Optional **min 7 days** between scheduled instances → at most one Sat/Sun slot per week.
- Done Monday: completion recorded; **next scheduled** still next Sat/Sun from epoch grid.

---

## 12. Open / TBD (refine in v0.2)

| Topic | Notes |
|-------|--------|
| Interval grammar | Parse/store expression; month/year nth-weekday |
| When open instances are created | On read vs nightly vs at `now > scheduled` |
| Date vs date-time | Preferred time-of-day stored but scheduling uses day |
| Timezone | Single user local zone |
| Snooze vs scheduled_at | Keep ideal `s` for pain vs move `s` |
| Multiple tasks same due function | One script per task |

---

## 13. Related documents

| Doc | Role |
|-----|------|
| [requirements.md](./requirements.md) | Product rules |
| [pain-model.md](./pain-model.md) | Planning / pain |
| [tech-stack.md](./tech-stack.md) | Implementation stack |

---

## 14. Changelog

| Version | Notes |
|---------|--------|
| 0.1 | Coarse scheduling spec |
