# RangeWater Google Play Data Safety Draft

This file is a release questionnaire aid, not a substitute for answering the current Play Console form.

## Current 1.0 behavior

- Accounts: none.
- Advertising: none.
- Third-party analytics: none.
- Device location permission: not requested.
- Ranch, herd, and movement records: stored locally on the operator's device.
- Backup archives: exported only after an operator action to a destination selected through Android's system picker or share sheet.
- Map imagery: visible tile coordinates and normal connection metadata are transmitted to the RangeWater Cloudflare imagery gateway and upstream federal imagery services so the requested map can be delivered.
- Cloud credentials: RangeWater neither requests nor stores a Google Drive or other document-provider password.
- Deletion: app-local data can be removed in the app, through Android storage settings, or by uninstalling; separately exported archives remain under the operator's control.

## Console review points

1. Confirm whether Play's current definitions classify requested map-tile coordinates as approximate location collection even though RangeWater does not request Android location permission.
2. Confirm whether the user-directed export/share flow is excluded from collection under the current Data safety definitions.
3. Declare network encryption only after verifying every production endpoint uses HTTPS.
4. Publish the final privacy policy at a stable public HTTPS URL and replace the draft contact placeholder.
5. Re-audit this form before adding live GPS, weather history, accounts, synchronization, analytics, or crash reporting.
