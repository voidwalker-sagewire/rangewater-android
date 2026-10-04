# RangeWater
## Grazing Buffers & Water Placement

RangeWater is a native Android planning tool for cattle producers and rotational grazing managers. Draw pastures, place water sources, map gates, and see how water placement affects the portion of a pasture within planned watering distances.

[Google Play](https://play.google.com/store/apps/details?id=com.sagewire.rangewater) · [Builds and checks](https://github.com/voidwalker-sagewire/rangewater-android/actions) · [Master Project Map / CaveCode](RANGEWATER.cavecode.txt)

## What you can do

- **Map pastures:** draw and edit boundaries over aerial imagery, estimate acreage, and maintain shared fence corners.
- **Plan water placement:** add and move water points, assign them to the pastures they serve, and compare covered acreage and percentages.
- **Choose a coverage view:** show the full physical radius or clip coverage to explicitly assigned pastures.
- **Map gates:** place gates on fence segments and preserve their relationship to the surrounding boundary.
- **Manage herd movements:** record herds and their locations, plan movement through gates, and complete or cancel movements with history.
- **Control the map:** switch aerial/labeled imagery, adjust overlay visibility, and focus on a herd's pasture context.
- **Back up ranch records:** export portable, checksummed `.rangewater` archives and restore them through Android's document picker.

These describe the baseline available in `main`. Later release work is tracked on the branches below.

### Understanding the water zones

| Zone | Distance from water | Purpose |
| --- | --- | --- |
| Preferred access | 0–800 feet (0–243.84 m) | Baseline watering-distance planning area |
| Planning transition | 800–1,000 feet (243.84–304.80 m) | Additional area to consider when assessing placement |
| Beyond planned coverage | Beyond 1,000 feet | Remaining pasture acreage outside these zones |

The zones are planning geometry, not a guarantee of where cattle will graze. Accessible Coverage uses explicit pasture assignments; it is not terrain-aware animal routing. Terrain and slope constraints are future work in the baseline documented here. Boundary and acreage values are map estimates, not surveyed boundaries.

## Source branches and release status

**Check the branch before building.** As inspected on October 4, 2026, the default branch does not contain the latest numbered development version.

| Branch | Declared version | Version code |
| --- | --- | --- |
| [main](https://github.com/voidwalker-sagewire/rangewater-android/tree/main) | 1.0.1 | 18 |
| [codex/rw-tx-015-sgr](https://github.com/voidwalker-sagewire/rangewater-android/tree/codex/rw-tx-015-sgr) | 1.1.0 | 19 |
| [codex/rw-tx-016-pmp](https://github.com/voidwalker-sagewire/rangewater-android/tree/codex/rw-tx-016-pmp) | 1.2.0 | 20 |
| [codex/rw-tx-017-mpo](https://github.com/voidwalker-sagewire/rangewater-android/tree/codex/rw-tx-017-mpo) | 1.3.1 | 22 |

These values were read from each branch's `app/build.gradle.kts`. A version declared in source does not establish Google Play publication, successful CI, or field acceptance. Consult the selected branch's project record and the exact build run for that commit. The CaveCode file on `main` records an older release baseline; do not treat it as a current account of later branches.

## Built in a mobile Appalachian forge

RangeWater is led by Michael, a cattle herd manager building software on his phone and tablet in the hours around ranch work. AI collaborators help translate field requirements into designs, code, and checks; Michael directs the product and evaluates it in use.

The development loop is practical: describe a field problem, define the intended behavior, implement it, run automated checks, test it on a device, and use the findings to guide the next revision. Phones and tablets are the working interface; repository hosting, cloud tools, and GitHub Actions provide supporting infrastructure and builds.

Project authority, decisions, implementation history, and verification evidence are recorded in the [Master Project Map / CaveCode](RANGEWATER.cavecode.txt).

## Data and imagery

Ranch records are stored locally with Room/SQLite. Moving records between devices uses an explicit backup and restore; this baseline does not provide live cloud synchronization. Make a separate archive before uninstalling or changing installation channels.

Local-first records do not mean all aerial imagery is available offline. Basemaps use USGS imagery and USDA NAIP detail through a Cloudflare Worker. Network access and upstream availability affect imagery loading. Bulk offline imagery packaging remains outside this baseline.

See the [imagery gateway guide](imagery-gateway/README.md) for its purpose and deployment procedure.

## Build and test

The following instructions match the inspected `main` branch. Check the build configuration on a different branch before using it.

### Requirements

- JDK 17.
- Android SDK with platform API 36 and suitable Android build tools.
- Android SDK location configured through `local.properties` (`sdk.dir=...`) or the build environment.
- Network access for initial dependency downloads.
- An Android device or emulator for instrumentation tests.

The committed wrapper selects Gradle 8.11.1. Root build configuration uses Android Gradle Plugin 8.10.1, Kotlin 2.0.21, and KSP 2.0.21-1.0.28. The app targets API 36 and supports Android 7.0 / API 24 or newer.

From the repository root:

```sh
# Run JVM unit tests.
./gradlew testDebugUnitTest

# Build an ordinary debug APK.
./gradlew assembleDebug

# With a device or emulator connected, run Android/Room tests.
./gradlew connectedDebugAndroidTest
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Ordinary local debug builds use the normal Android debug key. CI field builds use a separately configured stable field key. Google Play upload signing uses its own configuration; signing credentials are not part of the repository. A locally built debug APK should not be assumed to update an existing Play or field installation.

### GitHub Actions from a phone or tablet

1. Open [Actions](https://github.com/voidwalker-sagewire/rangewater-android/actions).
2. Select **Build RangeWater Field APK and Play Bundle**.
3. Open the run for the intended branch and commit, or use **Run workflow** where available.
4. Inspect the checks before downloading **RangeWater-Field-APK** or **RangeWater-Play-AAB**.
5. Match the artifact to its run, commit, and declared version before installing or submitting it.

The workflow runs gateway tests, JVM tests, Android instrumentation tests, APK/AAB builds, and certificate checks. Its signed build path requires repository signing secrets. A successful build is separate from physical-device acceptance and Play publication.

Gateway tests can also run separately with Node.js 22 and npm:

```sh
cd imagery-gateway
npm ci
npm test
```

## Repository guide

| Location | Contents |
| --- | --- |
| [RANGEWATER.cavecode.txt](RANGEWATER.cavecode.txt) | Master Project Map, decisions, history, and recorded verification |
| [app/src/main/java/com/sagewire/rangewater/data](app/src/main/java/com/sagewire/rangewater/data) | Room entities, data access, migrations, and backup/restore |
| [app/src/main/java/com/sagewire/rangewater/spatial](app/src/main/java/com/sagewire/rangewater/spatial) | Geometry, coverage, acreage, snapping, and routing calculations |
| [app/src/main/java/com/sagewire/rangewater/ui](app/src/main/java/com/sagewire/rangewater/ui) | Compose UI and MapLibre map integration |
| [app/src/test](app/src/test) | JVM tests |
| [app/src/androidTest](app/src/androidTest) | Android instrumentation and database tests |
| [.github/workflows/build.yml](.github/workflows/build.yml) | CI tests, builds, signing verification, and artifact uploads |
| [imagery-gateway](imagery-gateway) | Cloudflare Worker and gateway tests |
| [play](play) | Store listing assets and policy drafts |

## Reporting a field finding

Include the installed version, device, steps taken, expected behavior, observed behavior, and a screenshot when useful. For a build problem, include the branch, commit, workflow run, and relevant error output. Avoid posting private ranch records or signing credentials.

Product and architectural changes follow Michael's approval and the project record. Database changes require data-preserving migrations and verification; automated checks supplement physical-device testing.
