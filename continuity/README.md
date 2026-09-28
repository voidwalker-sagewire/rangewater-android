# RangeWater Continuity

This directory is the repository-side home of the CaveCode Forgekeeper pilot.
It holds governance and continuity artifacts, not application runtime code.

## Ratified governance

- `RANGEWATER-CONTINUITY-CONTRACT.cavecode`
  - Status: RATIFIED — 2026-09-26 by Michael
  - Transition record: `ratifications/RW-CC-001-REV1-RATIFICATION.cavecode`
- `RANGEWATER-CURRENT-STATE.cavecode`
  - Format status: RATIFIED — ACTIVE
  - Short current-state pointer index for mobile and AI handoff.
- `proposals/RW-FK-002-CURRENT-STATE-AND-BRANCH-SYNC.cavecode`
  - Status: RATIFIED — IMPLEMENTED
  - Integrated into the active RangeWater branch through pull request #3.

## Current evidence chain

- `captures/RW-FK-CAP-002-RW-TX-016-LOCAL-IMPLEMENTATION.cavecode`
  - Status: CAPTURED — IMPLEMENTED LOCALLY — NOT VERIFIED
  - Records application commit `5bcd1ba` and the current CI/push access blockers without
    advancing field or production authority.

- `captures/RW-FK-CAP-003-PRECISION-LOUPE-ALIGNMENT.cavecode`
  - Status: FIELD RETEST PASSED
  - Preserves the physical alignment finding, corrected source `b28ed7c`, automated
    evidence, and Michael's successful reticle retest.

- `ratifications/RW-TX-016-PMP-REV1-RATIFICATION.cavecode`
  - Status: RATIFIED — IMPLEMENTATION AUTHORIZED
  - Records Michael's authority for RangeWater 1.2.0 Precision Mapping and Paddock Planning.
  - Production publication remains separately gated.

- `transitions/RW-FK-TR-001-RW-TX-015-FIELD-ACCEPTED.cavecode`
  - Status: FIELD ACCEPTED
  - Records RangeWater 1.1.0 physical acceptance on both target devices.
  - Corrected accepted candidate: `df061bc`.
- `transitions/RW-FK-TR-003-PLAY-INTERNAL-MIGRATION-ACCEPTED.cavecode`
  - Status: FIELD ACCEPTED
  - Records Play delivery and the successful 1.0.1-to-1.1.0 in-place migration.
- `transitions/RW-FK-TR-004-PRODUCTION-PROMOTION-AUTHORIZED.cavecode`
  - Status: RATIFIED
  - Records Michael's authority to promote the exact tested versionCode 19 bundle.
- `transitions/RW-FK-TR-005-RANGEWATER-1.1.0-PRODUCTION-PUBLISHED.cavecode`
  - Status: FIELD ACCEPTED — PRODUCTION PUBLISHED
  - Records Google approval and Michael's completed 100% production publication.
- `transitions/RW-FK-TR-006-CAP-001-SUPERSEDED.cavecode`
  - Status: SUPERSEDED
  - Closes the earlier candidate capture through its later accepted evidence.
- `transitions/RW-FK-TR-007-RW-TX-016-FIELD-ACCEPTED.cavecode`
  - Status: FIELD ACCEPTED
  - Records RangeWater 1.2.0 physical acceptance after the corrected precision-reticle
    retest, while keeping Play internal migration and production authorization separate.
- `transitions/RW-FK-TR-008-RW-TX-016-INTERNAL-RELEASE-AUTHORIZED.cavecode`
  - Status: RATIFIED — INTERNAL RELEASE AUTHORIZED
  - Authorizes only the exact run-145 versionCode 20 bundle for Google Play Internal
    testing and the controlled 1.1.0-to-1.2.0 in-place migration test.

## Preserved historical records

- `captures/RW-FK-CAP-001-PLAY-INTERNAL-ACCEPTANCE.cavecode`
  - Historical state: CAPTURED
  - Current interpretation: SUPERSEDED by RW-FK-TR-003 and closed by RW-FK-TR-006.
- `conflicts/RW-FK-CONFLICT-001-PILOT-BRANCH-DRIFT.cavecode`
  - Status: RESOLVED — historical pilot evidence
  - The isolated pilot branch is superseded for current-state use.
- `supersessions/RW-FK-SUP-001-LEGACY-MASTER-CURRENT-STATE.cavecode`
  - Status: SUPERSEDED
  - Preserves `RANGEWATER.cavecode.txt` while superseding its stale 1.1.0
    current-state language with the active index and later transition records.

## Boundaries

- `RANGEWATER-CURRENT-STATE.cavecode` is the first current-state pointer after the
  continuity contract.
- `RANGEWATER.cavecode.txt` remains preserved as the historical master project map;
  later transition and supersession records govern where its old current-state
  statements conflict.
- `workorders/` remains the home of scoped transmission contracts and test cards.
- Existing ratified, verified, and field-accepted work keeps its established status.
- A continuity artifact may point to evidence; it must not rewrite history.
- An automatic system may capture candidate memory.
- Only Michael may ratify project truth.
- The future Forgekeeper engine belongs in a separate repository.
- RangeWater 1.1.0 versionCode 19 is Google Play production published.
- RangeWater 1.2.0 development is authorized only on `codex/rw-tx-016-pmp` under
  `workorders/RW-TX-016-PMP.cavecode` Revision 1.
- Its physical sequence is prepared in `workorders/RW-TX-016-PMP-PHYSICAL-TESTS.cavecode`;
  every item remains pending until a verified signed candidate exists.
