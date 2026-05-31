# Pain model — mathematical specification (v0.2)

Readable formulas only (no LaTeX). Implements [requirements.md](./requirements.md) §8.

**Principles**

- Pain is a **scalar cost** to **minimize** (subject to hard constraints).
- **No pain cap:** feasible plans are always valid regardless of total pain.
- **P\*** (global pain threshold) is for UI / targets only.
- Planning uses **calendar days** in v1.

---

## 1. Notation

| Symbol | Meaning |
|--------|---------|
| D | Days in planning horizon [H_start, H_end] |
| I | Instances to plan |
| s_i | Scheduled date (ideal day) |
| p_i | Planned date (chosen by planner) |
| delta_i | Day offset: **p_i minus s_i** (positive = late, negative = early) |
| d_i | Duration in minutes |
| w_i | Importance weight (default 1, at least 1) |
| L(day) | Sum of d_i for all instances planned on that day |
| F_i | Feasible days for instance i (weekdays, season, snooze, horizon) |
| d0_i | **First feasible day** for i (see section 4) |
| idx(day) | 0-based index of day in D (0 = H_start) |

**Overdue:** s_i is before H_start and instance is not completed.

---

## 2. Total pain

```
P_total = P_timing + P_overdue + P_daily
```

Minimize P_total subject to hard constraints. Do **not** reject plans when P_total > P*.

---

## 3. Timing pain (flexibility + grace window)

### 3.1 Grace window (zero extra pain)

Per task, configure:

- **g_early** — days **before** s_i with no timing pain
- **g_late** — days **after** s_i with no timing pain

If **delta_i** is between **-g_early** and **+g_late** (inclusive):

```
timing_pain(i) = 0
```

Example: g_early=2, g_late=1 → plan from 2 days early through 1 day late with no timing pain.

### 3.2 Pain outside the grace window

Only the part **outside** the grace band counts.

**Effective offset** (delta_eff):

```
if delta_i < -g_early:
    delta_eff = delta_i + g_early          # how many days too early

else if delta_i > g_late:
    delta_eff = delta_i - g_late           # how many days too late

else:
    delta_eff = 0                          # inside grace → no pain
```

**Acceptability** (asymmetric bell curve on delta_eff):

```
sigma = sigma_early    if delta_eff < 0
        sigma_late     if delta_eff >= 0

V = exp( -(delta_eff * delta_eff) / (2 * sigma * sigma) )
```

**Timing pain:**

```
timing_pain(i) = w_i * (1 - V)
```

- At scheduled day (delta=0): inside grace → **0 pain**
- Far from band: V near 0 → pain near w_i
- Large sigma_early → slow pain growth when too early
- Small sigma_late → fast pain growth when too late (fingernails)

Parameters per task: **g_early**, **g_late**, **sigma_early**, **sigma_late**, **w_i**.

### 3.3 Diagram (timing)

```
timing pain
    ^
    |     \ late side (often steeper)
    |      \
----+-------+-------+-----> planned day offset (delta)
   -g_early  0    +g_late
    |      /
    |     / early side
    0 pain inside [ -g_early , +g_late ]
```

---

## 4. Overdue pain (first feasible day = free)

### 4.1 First feasible day d0_i

For each overdue instance i:

```
d0_i = earliest day in F_i   (chronological first allowed day in horizon)
```

Often d0_i = H_start, but if snooze or “Sat/Sun only” blocks earlier days, d0_i is later.

### 4.2 Overdue penalty

Only for overdue instances. Let:

```
delay_i = max(0,  idx(p_i) - idx(d0_i))
```

**If delay_i = 0** (planned on first feasible day):

```
overdue_pain(i) = 0
```

**If delay_i > 0** (pushed later than necessary):

```
overdue_pain(i) = w_i * eta * (delay_i * delay_i)     # quadratic (recommended)
```

or, for constant marginal pain per day:

```
overdue_pain(i) = w_i * eta * delay_i                 # linear
```

**Quadratic** matches “each extra day hurts **more** than the previous” (marginal pain grows).

**eta** is a global setting (pain scale).

### 4.3 Does this match the intent?

| Your rule | Model |
|-----------|--------|
| First possible day → no extra overdue pain | delay=0 → overdue_pain=0 |
| Each day later → increasingly worse | delay² (or higher power) |
| Not tied blindly to H_start | Anchor is **d0_i**, not H_start |

**Note:** Timing pain (section 3) is separate. An overdue item on d0_i can still have timing_pain > 0 if d0_i is far from s_i and outside the grace window. That is intentional (very old due date vs ideal slot).

---

## 5. Daily load pain

### 5.1 Soft budget (piecewise linear)

For each day, L = L(day) in minutes:

```
if L <= 0:
    rho(L) = 0

else if L <= T:
    rho(L) = (P_T / T) * L

else:
    rho(L) = P_T + k * (L - T)
```

- **T** — soft threshold minutes
- **P_T** — pain at T (pain_at_threshold)
- **k** — extra pain per minute above T

```
P_daily = sum over all days d in D of rho(L(d))
```

### 5.2 Hard cap

```
L(day) <= H_hard    for every day   (constraint, not pain)
```

### 5.3 Diagram (daily)

```
daily pain rho(L)
    ^
    |                    /
    |                   /  slope k
    |                  /
    |                 /
    |               / slope P_T/T
    |             /
    +------------+------------> L (minutes)
    0            T
```

---

## 6. Hard constraints

1. p_i in F_i
2. L(day) <= H_hard
3. Same-day ordering (hard)
4. Each instance assigned exactly once

---

## 7. Planner (v1)

Greedy + local search; marginal cost when choosing a day:

```
delta_P = change in timing_pain + change in overdue_pain + change in rho(L)
```

Overdue instances: break ties toward **smaller idx(p)** (earlier day).

---

## 8. Parameters

**Global:** T, P_T, k, H_hard, P*, eta, overdue_power (1=linear, 2=quadratic)

**Per task:** w_i, d_i, g_early, g_late, sigma_early, sigma_late

**Example defaults:** g_early=0, g_late=0, sigma_early=7, sigma_late=3

---

## 9. Worked examples (plain numbers)

**Grace:** g_early=1, g_late=2, scheduled Monday.

| Planned | delta | delta_eff | timing pain |
|---------|-------|-----------|-------------|
| Mon | 0 | 0 | 0 |
| Tue | +1 | 0 | 0 |
| Wed | +2 | 0 | 0 |
| Thu | +3 | +1 | w*(1-V) small |
| Sun | -1 | 0 | 0 |
| Sat | -2 | -1 | w*(1-V) small |

**Overdue:** H_start Monday, d0_i Monday (feasible), eta=1, w=1, quadratic.

| Planned | delay | overdue pain |
|---------|-------|--------------|
| Mon | 0 | 0 |
| Tue | 1 | 1 |
| Wed | 2 | 4 |
| Thu | 3 | 9 |

---

## 10. Open choices

| # | Topic | Suggestion |
|---|--------|------------|
| 1 | Overdue power | **2** (quadratic) |
| 2 | Timing phi | **1 - V** (bounded) |
| 3 | Separate g_early / g_late | **Yes** (asymmetric grace) |

---

## 11. Changelog

| Version | Notes |
|---------|--------|
| 0.2 | No LaTeX; grace window; overdue anchored at d0_i with 0 pain there |
| 0.1 | Initial draft |
