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

- Production release: RangeWater 1.2.0, versionCode 20 — Google Play approved and
  published at 100% rollout on 2026-09-28.
- Released candidate source: `b28ed7c20364f972c9d7282204a935b63cb79d96`.
- Prior production: RangeWater 1.1.0, versionCode 19, source
  `df061bc07d8b09c6f678d56b1d439aa3e4e02923`; superseded for current distribution.
- Production continuity branch: `codex/rw-tx-016-pmp`.
- Active implementation branch: `codex/rw-tx-017-mpo`.
- RW-TX-017-MPO Revision 2: RangeWater 1.3.0, versionCode 21, passed automated
  verification at source `5a8e6bf8d290f305b854ec742bef98e9dc6bd2eb` in GitHub Actions
  run 162 and was released to Google Play Internal testing under Michael's exact-artifact
  authorization.
- Physical testing on 2026-10-02 withheld acceptance after straight and custom divider
  saves rejected plausible field geometry. Corrective RangeWater 1.3.1, versionCode 22,
  is in implementation and has no Google Play release authority.
- Production promotion and publication remain unauthorized.
- Automated acceptance: passed.
- Physical field acceptance: passed on Michael's Samsung phone and Samsung Tab S7.
- Google Play internal release: delivered; controlled 1.1.0-to-1.2.0 in-place migration
  passed with the preserved ranch records.
- Production promotion: explicitly authorized by Michael, approved by Google, and
  published at 100% rollout.
- Storefront propagation and post-publication device confirmation remain optional
  separate evidence and are not prerequisites for the recorded publication state.
- RW-TX-016-PMP: closed — field accepted — Google Play production published.

## Forgekeeper boundary

RangeWater is the first CaveCode Forgekeeper pilot. Repository-side continuity
records belong here. The later Forgekeeper engine will be a separate project and
must treat this repository as authoritative for RangeWater.

The ratified state ladder is:

`Captured → Proposed → Ratified → Assigned → Implemented → Verified → Field Accepted → Superseded`

Automatic systems may capture candidate memories. Only Michael may ratify project truth.
