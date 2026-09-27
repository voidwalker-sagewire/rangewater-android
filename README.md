# RangeWater

RangeWater is an offline-capable Android field tool for water placement,
pasture mapping, herd movement, and seasonal grazing records.

## Project truth

Read these files in order before proposing or implementing work:

1. `continuity/RANGEWATER-CONTINUITY-CONTRACT.cavecode`
   - Ratified Forgekeeper pilot governance contract.
   - Ratified by Michael on 2026-09-26.
2. `continuity/RANGEWATER-CURRENT-STATE.cavecode`
   - Active, mobile-readable pointer to the latest accepted project state.
3. `RANGEWATER.cavecode.txt`
   - Preserved master history, accepted decisions, evidence, and recovery context.
   - Its older 1.1.0 current-state sections are superseded by
     `continuity/supersessions/RW-FK-SUP-001-LEGACY-MASTER-CURRENT-STATE.cavecode`.
4. `workorders/`
   - Ratified transmission contracts and their acceptance records.

The repository and its recorded evidence—not chat memory—are the durable project
record. Michael is the Project Owner, Lead Architect, and Final Decision Authority.

## Current preserved state

- Production release: RangeWater 1.1.0, versionCode 19 — Google Play approved and
  published on 2026-09-27.
- Released candidate source: `df061bc07d8b09c6f678d56b1d439aa3e4e02923`.
- Prior production: RangeWater 1.0.1, versionCode 18, source
  `6769df5f6f37fa369dc9e7d76dacb3c57a60c8a2`; superseded for current distribution.
- Active continuity branch: `codex/rw-tx-015-sgr`.
- Automated acceptance: passed.
- Physical field acceptance: passed on Michael's Samsung phone and Samsung Tab S7.
- Google Play internal release: delivered; controlled 1.0.1-to-1.1.0 in-place
  migration passed on Samsung Tab S7.
- Production promotion: explicitly authorized by Michael, approved by Google, and
  published at 100% rollout.
- Storefront propagation and post-publication device confirmation remain optional
  separate evidence and are not prerequisites for the recorded publication state.
- RW-TX-015-SGR: closed — field accepted — Google Play production published.

## Forgekeeper boundary

RangeWater is the first CaveCode Forgekeeper pilot. Repository-side continuity
records belong here. The later Forgekeeper engine will be a separate project and
must treat this repository as authoritative for RangeWater.

The ratified state ladder is:

`Captured → Proposed → Ratified → Assigned → Implemented → Verified → Field Accepted → Superseded`

Automatic systems may capture candidate memories. Only Michael may ratify project truth.
