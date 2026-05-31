# Pain model — mathematical specification (draft)

This document defines how **pain** is computed and how the **planner** uses it. It implements [requirements.md](./requirements.md) §8.

**Principles**

- Pain is a **scalar cost** to **minimize** (subject to hard constraints).
- There is **no pain cap**: any plan that satisfies hard constraints is valid.
- **Global pain threshold** `P*` is a **UI / target reference only**, not a feasibility bound.
- Planning is **day-granular** in v1 (`planned_at` date; time-of-day is display-only).

---

## 1. Notation

| Symbol | Meaning |
|--------|---------|
| `D` | Set of calendar days in the planning horizon `[H_start, H_end]` |
| `I` | Set of instances to plan (from scheduling), indexed by `i` |
| `s_i` | **Scheduled date** of instance `i` (ideal day from rules) |
| `p_i` | **Planned date** (decision variable), `p_i ∈ D` |
| `δ_i` | Day offset: `δ_i = p_i - s_i` (integer days; **+ late**, **− early**) |
| `d_i` | Duration (minutes) of instance `i` |
| `w_i` | Importance weight, `w_i ≥ 1` (default `1`) |
| `L(d)` | Total planned minutes on day `d`: `L(d) = Σ_{i: p_i = d} d_i` |
| `T` | Soft daily minute threshold (settings) |
| `P_T` | Pain at `L = T` (settings: `pain_at_threshold`) |
| `k` | Pain per minute above `T` (settings: `pain_per_minute_over_threshold`) |
| `H_hard` | Hard daily minute cap (settings) |
| `P*` | Global pain threshold (reference only) |

**Overdue:** instance `i` is overdue (w.r.t. plan start) if `s_i < H_start` and not completed.

**Snooze / allowed weekdays / seasonal window:** feasible set `F_i ⊆ D` per instance; require `p_i ∈ F_i`.

---

## 2. Total pain

```
P_total(x) = P_timing(x) + P_overdue(x) + P_daily(x)
```

where `x` is the assignment vector `(p_i)_{i∈I}`.

The planner **minimizes** `P_total` over feasible assignments. It **always** returns a feasible plan if one exists; it does **not** reject plans with `P_total > P*`.

---

## 3. Instance timing pain (flexibility)

### 3.1 Acceptability

Define **acceptability** `V_i(δ) ∈ (0, 1]`, with `V_i(0) = 1` (best). Lower acceptability ⇒ higher pain.

**Asymmetric Gaussian (recommended default):**

```
V_i(δ) = exp( - δ² / (2 σ_i(δ)²) )

σ_i(δ) = σ_i_early   if δ < 0
         σ_i_late    if δ ≥ 0
```

Parameters per task (or instance): `σ_i_early`, `σ_i_late` (days, > 0).

- Large `σ_i_early` → doing **early** is cheap (fingernails).
- Small `σ_i_late` → doing **late** is expensive.

**Pain from offset:**

```
π_timing(i, δ) = w_i · φ(V_i(δ))

φ(V) = 1 - V          (bounded, easy UI: 0 at ideal, → 1 far away)
```

Alternative (steeper tails): `φ(V) = -ln(V)` (unbounded above; use if you want stronger penalties).

**Properties**

- `π_timing(i, 0) = 0`
- Symmetric iff `σ_early = σ_late`
- Strictly increasing in `|δ|` in the “bad” direction when σ’s are finite

### 3.2 Infeasible days

If rules forbid placing `i` on day `p` (weekday, season, snooze), `p ∉ F_i` is a **hard** constraint, not infinite pain.

Optional **soft** forbidden band (future): add penalty for `p` outside allowed weekdays instead of hard forbid — **not in v1**.

---

## 4. Overdue urgency

Requirement: overdue instances should be planned **as early as possible** in the horizon.

Add a term that penalizes **delay after horizon start** only for overdue instances:

```
O_i = { i ∈ I : s_i < H_start }

π_overdue(i, p_i) = w_i · η · max(0, idx(p_i) - idx(H_start))
```

- `idx(·)` = integer index of calendar day in `D` (0 = `H_start`).
- `η > 0` global (settings), e.g. `η = 1` pain unit per day of delay.

This is **linear** in delay; for stronger “ASAP”, use quadratic:

```
π_overdue(i, p_i) = w_i · η · max(0, idx(p_i) - idx(H_start))²
```

**Interaction with timing pain:** overdue instance scheduled on `H_start` may still have `δ_i < 0` (if `s_i` was long ago), so `π_timing` can be large. That is intentional: old overdue + far from ideal slot both hurt.

---

## 5. Daily load pain

### 5.1 Piecewise-linear soft budget

For each day `d ∈ D`, with load `L = L(d)`:

```
ρ(L) = 0                                    if L ≤ 0
       (P_T / T) · L                        if 0 < L ≤ T
       P_T + k · (L - T)                    if L > T
```

- `T` = soft threshold minutes (settings).
- `P_T` = pain at knee (settings).
- `k` = marginal pain per minute above `T` (settings).

```
P_daily(x) = Σ_{d ∈ D} ρ(L(d))
```

**Note:** `ρ` is **concave-linear-convex**: slow growth until `T`, then faster. No cap.

### 5.2 Hard cap

**Constraint (not pain):**

```
L(d) ≤ H_hard   for all d ∈ D
```

If the problem is infeasible (sum of durations too large for horizon × `H_hard`), planner reports **infeasible** — not “too much pain”.

### 5.3 Relation of `T` to “soft max minutes”

Requirements mention soft budget for planning. Two equivalent views:

1. **Single knob:** `T` is the soft budget; `ρ` encodes discomfort above it.
2. **Optional:** also store `M_soft` and default `T = M_soft` in UI.

Use one pair `(T, P_T, k)` in v1 to avoid redundancy.

---

## 6. Hard constraints (feasibility)

A plan is **feasible** iff:

1. **Assignment:** `p_i ∈ F_i` for all `i`
2. **Daily cap:** `L(d) ≤ H_hard` for all `d`
3. **Same-day order:** for instances with `p_i = p_j = d`, sort order matches hard precedence graph (topological order exists)
4. **At most one planned day per open instance** (each instance assigned exactly once)

**Not** constrained by `P*` or `P_total`.

---

## 7. Optimization problem

```
minimize    P_total(p)
subject to  p_i ∈ F_i,
            L(d) ≤ H_hard,
            ordering constraints on each day
```

**NP-hard** in general (bin-packing + assignment). For v1 use a **constructive heuristic + local improvement** (Section 8), not an external solver.

---

## 8. Suggested planner algorithm (v1)

### Phase A — Feasible initialization

1. Sort instances by priority key (descending):
   - overdue first (`s_i < H_start`)
   - then `w_i · η` (importance)
   - then earlier `s_i`
2. For each instance `i`, assign `p_i` to the **first** day `d ∈ F_i` (in chronological order) such that:
   - `L(d) + d_i ≤ H_hard`
   - ordering slots on `d` still available
   - **Marginal pain** `ΔP` is finite

Use **marginal cost** when multiple days qualify:

```
ΔP(i → d) = π_timing(i, d - s_i) + π_overdue(i, d) + ρ(L(d) + d_i) - ρ(L(d))
```

Pick `d` with minimum `ΔP` (break ties toward earlier `d` for overdue).

### Phase B — Local search

Repeat until no improvement or iteration limit:

- **Move:** reassign one `i` to another feasible day with lower `P_total`
- **Swap:** exchange two instances’ days if feasible and `P_total` decreases

### Phase C — Reporting

Return:

- `P_total`, breakdown `(P_timing, P_overdue, P_daily)`
- Compare to `P*`: e.g. ratio `P_total / P*` or delta (UI only)
- Per-day `L(d)`, `ρ(L(d))` for transparency

---

## 9. Parameter catalog

### 9.1 Global (settings)

| Parameter | Role | Example |
|-----------|------|---------|
| `T` | Soft minute knee | 120 |
| `P_T` | Pain at knee | 10 |
| `k` | Pain / minute above `T` | 2 |
| `H_hard` | Hard daily cap (min) | 480 |
| `P*` | Reference threshold (UI) | 100 |
| `η` | Overdue delay penalty | 1 |
| `φ` | Transform (`1-V` or `-ln V`) | `1-V` |

### 9.2 Per task

| Parameter | Role |
|-----------|------|
| `w_i` | Importance multiplier |
| `d_i` | Duration (minutes) |
| `σ_i_early`, `σ_i_late` | Flexibility (days) |

**Defaults (starting point):** `σ_early = 7`, `σ_late = 3`, `w = 1`.

### 9.3 Preset shapes (UI helpers, not rule types)

| Profile | σ_early | σ_late |
|---------|---------|--------|
| Strict | 2 | 2 |
| Tolerant late | 5 | 2 |
| Fingernails-like | 10 | 2 |
| Flexible both | 7 | 7 |

---

## 10. Worked micro-example

**Settings:** `T=60`, `P_T=6`, `k=1`, `H_hard=120`, `η=2`, `P*=50`.

**One instance:** `s = Monday`, `w=2`, `d=30` min, `σ_early=7`, `σ_late=3`.

| Planned day | δ | `V ≈` | `φ=1-V` | `π_timing = 2φ` |
|-------------|---|-------|---------|-----------------|
| Mon | 0 | 1 | 0 | 0 |
| Wed | +2 | 0.51 | 0.49 | 0.98 |
| Fri (early) | −2 | 0.92 | 0.08 | 0.16 |

**Daily:** if only this task on a day, `L=30`, `ρ(30) = (6/60)*30 = 3`.

---

## 11. Open choices (decide before coding)

| # | Question | Recommendation |
|---|----------|----------------|
| 1 | `φ(V) = 1-V` vs `-ln V` | **`1-V`** for v1 (bounded, intuitive) |
| 2 | Overdue penalty linear vs quadratic | **Linear** first; quadratic if ASAP too weak |
| 3 | Day offset: calendar days vs business days | **Calendar days** in v1 |
| 4 | Multiple instances same task same day | Allowed if rules allow; durations **sum** in `L(d)` |
| 5 | External due (ASAP) | `s_i = H_start`, small `σ_late`, large `w_i`, high `η` |

---

## 12. Acceptance checks (from requirements)

| Scenario | Expected planner behavior |
|----------|---------------------------|
| Fingernails | Low `π_timing` for `δ < 0`; high for `δ > 0` |
| Overdue habit backlog | Low `idx(p_i)` (early in horizon) via `π_overdue` |
| Many tasks one day | `ρ(L)` rises; planner spreads if cheaper days exist |
| Above `T` minutes | `ρ` still finite; plan valid unless `L > H_hard` |
| `P_total >> P*` | Plan still returned; UI warns |

---

## 13. Version

| Version | Date | Notes |
|---------|------|-------|
| 0.1 | draft | Initial formal model |
