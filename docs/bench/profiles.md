# RewardsBench v1: 50 profiles

This document lists all 50 benchmark profiles used in RewardsBench v1. Spend amounts are in **USD annual** (monthly profiles are listed with their monthly spend; the harness annualizes by ×12). Categories: GROCERIES, DINING, GAS, TRAVEL, ONLINE, OTHER. Only non-zero categories are shown.

**Taxonomy:** 6 baselines, 10 category-dominant, 20 cap-boundary, 4 goal-discriminator, 10 monthly. Total 40 annual + 10 monthly.

**Profile design rationale:**
- **Baselines:** Cover spend range from minimal ($5k) to high-net-worth ($88k annual) to ensure solvers scale across realistic budgets.
- **Category-dominant:** Stress category-specific earn rates (e.g., grocery-heavy profiles probe 6% grocery cards; travel-heavy probes travel multipliers).
- **Cap-boundary:** Probe cap-aware logic at below/at/above/far for multiple catalog caps (e.g., $6k, $12k groceries; $2k online).
- **Goal-discriminator:** Target spend mixes that favor different goals (aa-sweetspot, flex-sweetspot, cashback-sweetspot, fee-sensitive).
- **Monthly:** Validate annualization (×12); ensure monthly-input users get correct cap proration.

---

## A) Baselines (6 annual)

| Profile ID        | GROCERIES | DINING | GAS   | TRAVEL | ONLINE | OTHER  |
|------------------|-----------|--------|-------|--------|--------|--------|
| light            | 3000      | 1500   | 1200  | —      | —      | 2500   |
| moderate         | 6000      | 3000   | 2400  | 2000   | 1200   | 5000   |
| heavy            | 15000     | 6000   | 3600  | 8000   | 3000   | 10000  |
| balanced         | 5000      | 2500   | 2000  | 2500   | 1500   | 4000   |
| minimal          | —         | —      | —     | —      | —      | 5000   |
| high-net-worth   | 24000     | 12000  | 6000  | 20000  | 6000   | 20000  |

---

## B) Category-dominant (10 annual)

| Profile ID                 | GROCERIES | DINING | GAS  | TRAVEL | ONLINE | OTHER  |
|---------------------------|-----------|--------|------|--------|--------|--------|
| grocery-heavy             | 12000     | 2000   | —    | —      | —      | 3000   |
| dining-heavy              | 4000      | 10000  | —    | —      | —      | 2000   |
| travel-heavy              | —         | 4000   | —    | 15000  | —      | 5000   |
| gas-heavy                 | 3000      | —      | 6000 | —      | —      | 3000   |
| online-heavy              | 3000      | —      | —    | —      | 8000   | 4000   |
| grocery-heavy-other-low   | 12000     | 2000   | —    | —      | —      | 1000   |
| grocery-heavy-other-med   | 12000     | 2000   | —    | —      | —      | 5000   |
| grocery-heavy-other-high  | 12000     | 2000   | —    | —      | —      | 12000  |
| dining-heavy-other-low   | 4000      | 10000  | —    | —      | —      | 1000   |
| travel-heavy-other-low    | —         | 4000   | —    | 15000  | —      | 1000   |

---

## C) Cap-boundary (20 annual)

Spend at/near catalog caps to stress cap-aware logic. OTHER = 1000 in all for non-degenerate selection.

| Profile ID                  | Primary category | Spend (annual) | Note    |
|----------------------------|------------------|----------------|---------|
| groceries-cap6000-below    | GROCERIES        | 5900           | below 6k |
| groceries-cap6000-at       | GROCERIES        | 6000           | at cap   |
| groceries-cap6000-above    | GROCERIES        | 6100           | above    |
| groceries-cap6000-far      | GROCERIES        | 12000          | 2× cap   |
| groceries-cap12000-below   | GROCERIES        | 11900          | below 12k |
| groceries-cap12000-at     | GROCERIES        | 12000          | at cap   |
| groceries-cap12000-above  | GROCERIES        | 12100          | above    |
| groceries-cap12000-far    | GROCERIES        | 24000          | 2× cap   |
| groceries-cap25000-below   | GROCERIES        | 24900          | below 25k |
| groceries-cap25000-at     | GROCERIES        | 25000          | at cap   |
| groceries-cap25000-above  | GROCERIES        | 25100          | above    |
| groceries-cap25000-far    | GROCERIES        | 50000          | 2× cap   |
| groceries-cap1500-below   | GROCERIES        | 1400           | below 1.5k |
| groceries-cap1500-at      | GROCERIES        | 1500           | at cap   |
| groceries-cap1500-above   | GROCERIES        | 1600           | above    |
| groceries-cap1500-far     | GROCERIES        | 3000           | 2× cap   |
| online-cap2000-below      | ONLINE           | 1900           | below 2k |
| online-cap2000-at         | ONLINE           | 2000           | at cap   |
| online-cap2000-above      | ONLINE           | 2100           | above    |
| online-cap2000-far        | ONLINE           | 4000           | 2× cap   |

---

## D) Goal-discriminator (4 annual)

| Profile ID          | GROCERIES | DINING | GAS  | TRAVEL | ONLINE | OTHER |
|---------------------|-----------|--------|------|--------|--------|-------|
| aa-sweetspot        | 3000      | 6000   | —    | 12000  | —      | 2000  |
| flex-sweetspot      | —         | 4000   | —    | 6000   | 8000   | 3000  |
| cashback-sweetspot  | 5000      | 3000   | 2000 | 2000   | 1500   | 4000  |
| fee-sensitive       | 6000      | 3000   | —    | —      | —      | 5000  |

---

## E) Monthly (10)

Amounts below are **monthly** USD; harness annualizes (×12) for optimization.

| Profile ID               | GROCERIES | DINING | GAS  | TRAVEL | ONLINE | OTHER |
|--------------------------|-----------|--------|------|--------|--------|-------|
| monthly-light            | 400       | 200    | —    | —      | —      | 300   |
| monthly-moderate         | 600       | 350    | 250  | 200    | 100    | 500   |
| monthly-grocery-cap-below| 408       | —      | —    | —      | —      | 100   |
| monthly-grocery-cap-at   | 500       | —      | —    | —      | —      | 100   |
| monthly-grocery-cap-above| 508       | —      | —    | —      | —      | 100   |
| monthly-grocery-cap-far | 1000      | —      | —    | —      | —      | 100   |
| monthly-travel           | —         | —      | —    | 1000   | —      | 200   |
| monthly-dining            | —         | 500    | —    | —      | —      | 200   |
| monthly-online           | —         | —      | —    | —      | 400    | 150   |
| monthly-commuter          | —         | 150    | 350  | —      | —      | 200   |

---

**Canonical profile IDs** (sorted) are in `docs/bench/profile_ids_v1.json`. 
