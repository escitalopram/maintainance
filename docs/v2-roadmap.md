# v2 roadmap

Branch **`v2`** continues from the v0.1 scaffold (backend + frontend on `cursor/backend-server-912e`). It holds **spec refinements** and **features deferred from v0.1**.

Status: **active development branch** (spec + implementation).

---

## v0.1 delivered (baseline)

- Requirements, scheduling, pain, and planning specs (through v0.3.7 / scheduling v0.5.2 / planning v0.3.4)
- Spring Boot backend: scheduling, planning, REST API, H2
- React frontend: tasks, plan (react-big-calendar), settings
- Fractional **`every_n_*`** intervals (**`n >= 1`**), catch-up count, grace-aware overdue, 2× planning horizon

---

## Postponed from v0.1 — implement in v2

### Scheduling

| Item | Spec reference | v0.1 gap |
|------|----------------|----------|
| **nth weekday of month** (e.g. 3rd Tuesday) | scheduling-model §5.1 | Not implemented |
| **`every_n_years`** | scheduling-model §5.1 | Not implemented |
| **Horizon assumptions** (`H_start > today`) | scheduling-model §8 | Gap assumed done silently; not shown in UI |
| **Catch-up increment** | scheduling-model §3.3 | Incremental via **`last_reconciled_date`**; mark done decrements |
| **Seasonal window** (full) | scheduling-model §5.5 | Partial / basic only |
| **Allowed weekdays** in UI | requirements §4.1 | Backend snap exists; no editor control |

### Planning

| Item | Spec reference | v0.1 gap |
|------|----------------|----------|
| **Same-day ordering edges** | planning-algorithm §5 | Not stored or enforced |
| **Local search** (up to 1000 iterations) | planning-algorithm §9.3 | Greedy + overflow only |
| **Ordering cycle** warning | planning-algorithm §5.3 | Not detected |
| **Within-day order** from topological sort | planning-algorithm §5 | Always `0` |
| **Ordering-aware assignment** during greedy | planning-algorithm §9.2 | Not checked when picking day |

### Product / UI

| Item | Spec reference | v0.1 gap |
|------|----------------|----------|
| **Tags** (name, description) | requirements §9 | Not in schema or UI |
| **Preferred time of day** label | scheduling-model §2.2 | Not shown |
| **Completion history** in task UI | requirements §9 | API exists; not in frontend |
| **Example-task presets** / acceptance fixtures | requirements §10 | Manual entry only |
| **Pain curve preview** in task editor | requirements §8 | Not visualized |

### Infrastructure

| Item | Notes |
|------|--------|
| **Integration tests** for planning acceptance table | planning-algorithm §12 |
| **OpenAPI-generated** TS client | Optional |
| **Prod static bundle** in CI | Gradle `copyFrontend` exists; not wired to CI |

---

## Spec refinements (v2)

Track doc edits here before or alongside code:

- [ ] Freeze v2 requirements sign-off (merge open doc PRs into `v2` baseline)
- [ ] Schema for **same-day ordering edges** (planning-algorithm §5.2 TBD)
- [x] Incremental catch-up + **`last_reconciled_date`** (scheduling-model §3.3)
- [ ] Clarify **LAST_COMPLETION** + fractional grid interaction after mark done
- [ ] Frontend: week view as **day-only calendar** (may replace react-big-calendar week mode or keep CSS-only hide)

---

## Suggested implementation order

1. ~~Horizon assumptions + incremental catch-up~~ (scheduling-model v0.5.3)
2. Same-day ordering (schema → planner → UI)
3. nth weekday + `every_n_years`
4. Local search pass
5. Tags, preferred time, task history UI
6. Acceptance / integration tests

---

## Changelog

| Version | Notes |
|---------|--------|
| 0.1 | Initial v2 roadmap from v0.1 branch |
