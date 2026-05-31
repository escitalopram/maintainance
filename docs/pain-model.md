# Pain model — mathematical specification (v0.3)

Readable formulas only (no LaTeX). Implements [requirements.md](./requirements.md) §8.

**Principles**

- Pain is a **scalar cost** to **minimize** (subject to hard constraints).
- **No pain cap:** feasible plans are always valid regardless of total pain.
- **P\*** (global pain threshold) is for UI / targets only.
- Planning uses **calendar days** in v1.
- **Single timing term:** no separate overdue pain component.

---

## 1. Notation

| Symbol | Meaning |
|--------|---------|
| D | Days in planning horizon [H_start, H_end] |
| I | Instances to plan |
| s_i | Scheduled date (ideal day from scheduling rules) |
| p_i | Planned date (chosen by planner) |
| delta_i | Day offset: **index(p_i) - index(s_i)** (+ = late, − = early) |
| d_i | Duration in minutes |
| w_i | Importance weight (default 1, at least 1) |
| L(day) | Sum of d_i for all instances planned on that day |
| F_i | Feasible days for instance i (horizon, weekdays, season, snooze) |
| d0_i | **First feasible day:** earliest day in F_i |
| idx(day) | 0-based index in D (0 = H_start) |

**Overdue (planning):** instance is open, and the grace window around s_i has **fully ended** before planning starts (typically before H_start). Exact rule can align with “scheduled before horizon and outside grace at H_start”.

---

## 2. Total pain

```
P_total = P_timing + P_daily
```

Minimize P_total subject to hard constraints. Do **not** reject plans when P_total > P*.

---

## 3. Timing pain — two layers

Timing pain uses **one** underlying curve (**Regime A**) and, for overdue instances only, a **d0 exception** (**Regime B**).

```
if instance i is overdue:
    if p_i == d0_i:
        timing_pain(i) = 0
    else:
        timing_pain(i) = RegimeA(i, p_i)

else:
    timing_pain(i) = RegimeA(i, p_i)
```

**Regime B** is not a different curve — it is only: **“0 on first feasible day; otherwise use Regime A.”**

**d0_i** is the **first possible** planning day for that instance, not necessarily the first day of the horizon (snooze, allowed weekdays, etc. may push d0_i later).

---

## 4. Regime A — grace window + bell curve

### 4.1 Grace (flat zero)

Per task:

- **g_early** — days before s_i with no timing pain
- **g_late** — days after s_i with no timing pain

```
delta = index(p) - index(s)

if  -g_early <= delta <= g_late:
    RegimeA = 0
    (done)
```

### 4.2 Outside grace — effective offset

```
if delta < -g_early:
    delta_eff = delta + g_early

else if delta > g_late:
    delta_eff = delta - g_late
```

(Inside grace, Regime A is already 0.)

### 4.3 Bell curve → acceptability V

**Acceptability** V in (0, 1]: 1 = best, near 0 = bad.

```
sigma = sigma_early    if delta_eff < 0
        sigma_late     if delta_eff >= 0

V = exp( -(delta_eff * delta_eff) / (2 * sigma * sigma) )
```

This is a **Gaussian bell** in delta_eff:

- **delta_eff = 0** (just outside grace edge) → **V = 1**
- **|delta_eff|** increases → **V** falls toward 0
- **sigma_early** large → slow pain growth when too **early**
- **sigma_late** small → fast pain growth when too **late** (e.g. fingernails)

### 4.4 Pain from acceptability

```
RegimeA = w * (1 - V)
```

| V | timing pain | |
|---|-------------|---|
| 1 | 0 | ideal / at grace edge |
| 0.5 | 0.5 * w | moderate |
| ~0 | ~w | far outside band |

### 4.5 Diagram (Regime A)

```
timing pain (Regime A)
    ^
    |              \  late (steep if sigma_late small)
 w  |               \
    |                \
  0 +----+===========+-----> delta (days from s)
      -ge    grace    +gl
```

---

## 5. Regime B — overdue and the bell

### 5.1 Why Regime B exists

If an instance is **very overdue**, **s** is far before **d0**. Regime A alone on **p = d0** would give a large **delta** outside grace → high **(1 − V)**. Requirement: **planning on the first feasible day must incur 0 timing pain**, even when **s** is long ago.

Regime B does **not** replace the bell; it **only** forces **0** at **d0**. On any other day, the **same bell around s** applies.

### 5.2 Relation to the bell (summary)

| Planned day | Overdue? | Timing pain |
|-------------|----------|-------------|
| **p = d0** | yes | **0** (forced; bell ignored) |
| **p ≠ d0** | yes | **Regime A** (grace + bell vs **s**) |
| any **p** | no | **Regime A** only |

### 5.3 A vs B on the same day (example)

Same **w**, grace, sigmas. **s_B = s_A + 3**. Both overdue, same **d0** (e.g. Monday).

| Plan | Task A | Task B |
|------|--------|--------|
| Monday **d0** | **0** (Regime B) | **0** (Regime B) |
| Tuesday | Regime A; larger delta from **s_A** | Regime A; smaller delta from **s_B** → **less pain** |

Moving the **earlier-scheduled** task to a later plan day hurts more because the bell measures distance from **s**.

### 5.4 Diagram (overdue + Regime B)

```
timing pain
    ^
    |     ..... Regime A (bell), for p > d0
 w  |  ...
    | .
  0 *-------------------------> p
    d0  d0+1
    ^
    forced 0 (may jump to Regime A value on d0+1)
```

---

## 6. Daily load pain

Unchanged from v0.2.

For each day, L = total planned minutes that day:

```
if L <= 0:
    rho(L) = 0
else if L <= T:
    rho(L) = (P_T / T) * L
else:
    rho(L) = P_T + k * (L - T)
```

```
P_daily = sum over days d in D of rho(L(d))
```

**Hard constraint:** `L(day) <= H_hard` for every day.

| Setting | Role |
|---------|------|
| T | Soft minute threshold |
| P_T | Pain at T |
| k | Pain per minute above T |
| H_hard | Hard daily cap (minutes) |

---

## 7. Hard constraints

1. `p_i in F_i`
2. `L(day) <= H_hard`
3. Same-day ordering (hard)
4. Each instance assigned exactly once

Not constrained by P* or P_total.

---

## 8. Planner (v1)

Marginal cost when assigning instance i to day d:

```
delta_P = change in timing_pain(i, d) + change in rho(L(d))
```

Use Regime B rule when computing timing_pain for overdue instances.

Tie-break: prefer earlier feasible days for overdue when delta_P is equal.

Greedy initialization + local move/swap improvement.

---

## 9. Parameters

**Global:** T, P_T, k, H_hard, P*

**Per task:** w_i, d_i, g_early, g_late, sigma_early, sigma_late

**Example defaults:** g_early=0, g_late=0, sigma_early=7, sigma_late=3, w=1

---

## 10. Worked examples

### 10.1 Regime A only (not overdue)

Scheduled Monday; g_early=1, g_late=2; sigma_late=3; w=1.

| Planned | delta | In grace? | timing pain |
|---------|-------|-----------|-------------|
| Mon | 0 | yes | 0 |
| Wed | +2 | yes | 0 |
| Thu | +3 | no, delta_eff=1 | ~0.05 |
| Sat | -2 | yes | 0 |

### 10.2 Overdue + Regime B

s was 10 days before d0; grace ended; w=1; g_late=0; sigma_late=3.

| Planned | Rule | timing pain (approx) |
|---------|------|----------------------|
| d0 | Regime B | **0** |
| d0+1 | Regime A, delta_eff ≈ 11 | high (~w) |
| d0+2 | Regime A, delta_eff ≈ 12 | higher |

Task B with s_B three days later: same on d0 (**0**); on d0+1, delta_eff about **8** instead of **11** → **lower** pain than A.

---

## 11. Changelog

| Version | Notes |
|---------|--------|
| 0.3 | Single P_timing; overdue = 0 at d0 else Regime A; removed P_overdue and staleness×slip |
| 0.2 | Grace window; separate P_overdue at d0 |
| 0.1 | Initial draft |
