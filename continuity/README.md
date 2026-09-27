# RangeWater Continuity

This directory is the repository-side home of the CaveCode Forgekeeper pilot.
It holds governance and continuity artifacts, not application runtime code.

## Ratified governance

- `RANGEWATER-CONTINUITY-CONTRACT.cavecode`
  - Status: RATIFIED — 2026-09-26 by Michael
  - Transition record: `ratifications/RW-CC-001-REV1-RATIFICATION.cavecode`

## Recorded evidence

- `transitions/RW-FK-TR-001-RW-TX-015-FIELD-ACCEPTED.cavecode`
  - Status: FIELD ACCEPTED
  - Records RangeWater 1.1.0 physical acceptance on both target devices.
  - Corrected accepted candidate: `df061bc`.
- `captures/RW-FK-CAP-001-PLAY-INTERNAL-ACCEPTANCE.cavecode`
  - Status: CAPTURED
  - Preserves the reported Google Play internal-release acceptance.
  - Migration-test result remains a human evidence gate.
- `conflicts/RW-FK-CONFLICT-001-PILOT-BRANCH-DRIFT.cavecode`
  - Status: OPEN
  - Records divergence between the isolated pilot branch and active product work.

## Proposed governance

- `RANGEWATER-CURRENT-STATE.cavecode`
  - Format status: PROPOSED
  - Short current-state pointer index for mobile and AI handoff.
- `proposals/RW-FK-002-CURRENT-STATE-AND-BRANCH-SYNC.cavecode`
  - Status: PROPOSED
  - Requires Michael's ratification before the index format, branch rule, or migration
    becomes authoritative.

## Boundaries

- `RANGEWATER.cavecode.txt` remains the current master project map.
- `workorders/` remains the home of scoped transmission contracts and test cards.
- Existing ratified, verified, and field-accepted work keeps its established status.
- A continuity artifact may point to evidence; it must not rewrite history.
- An automatic system may capture candidate memory.
- Only Michael may ratify project truth.
- The future Forgekeeper engine belongs in a separate repository.
- RangeWater 1.1.0 production promotion remains unauthorized.
