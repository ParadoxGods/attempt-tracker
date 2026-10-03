# Attempt Tracker

<img src="../icon.png" alt="Attempt Tracker fish and stopwatch icon" width="48" height="48">

Version **1.1.9** is a simple fishing session tracker for RuneLite. The sidebar shows **Detected lures**, **Catch success**, **Catch fail**, **Catch rate**, **Logged in** time, **Fishing** time, and elapsed fishing ticks. History and diagnostics are folded away. The plugin observes gameplay; it does not automate it.

## Using the plugin

1. Launch the development client and enable **Attempt Tracker**. Open its fish-and-stopwatch sidebar button.
2. Choose 1, 3, or 5 lures in-game. The sidebar shows **Detected lures: 3 per catch**, for example, and updates as the game choice or carried supply changes; no plugin setting is needed. This is your selected quantity, not the number of lures left. When no lures or tackle box are carried, it shows **None / No lures available**, and estimates use the no-lure schedule. An unverified tackle box shows **Unknown**; an unloaded inventory shows `--`. A note identifies a needed harpooning restart. The empty Fishing configuration section has been removed.
3. Interact with a fishing spot. The fishing clock starts when you begin fishing there, after any walking. Shark attempt estimates separately require the game message `You start harpooning fish.` to anchor their schedule. Casing, repeated spaces, a missing final punctuation mark, `!`, and `...` are also accepted. A catch or animation alone cannot anchor a shark attempt schedule.
4. Fishing time and ticks pause on logout, a different action or animation, movement, a full inventory, or a moved/despawned fishing spot. Clicking Inventory or Skills tabs, examining objects, or dropping fish does not interrupt the timer.
5. Interact with a fishing spot again to resume fishing time; a fresh harpooning start resumes shark attempt measurement. Existing totals remain. **Reset session** is the only action that clears the current totals and timers; it archives the previous session first.

The current session survives logout, world changes, client restarts, and plugin disable/re-enable. Logged-in time includes time spent banking or standing idle; fishing time includes active fishing at a selected spot, even when its attempt schedule is unknown or excluded by the shark filter. Neither clock includes time offline. Timers use a monotonic clock, so changing the system time does not change their elapsed time. A reset pauses fishing until a new fishing interaction or start message.

**Session history** lets you view earlier manual sessions. The **Detected lures** row always shows the live choice, even when viewing an older session. Logging out replaces its amount with `--`. **Details** contains the status/diagnostic text and **Export CSV**. The only visible configuration options are the overlay, diagnostics, and local tick tracing. Changing display or diagnostic settings does not pause fishing.

## What the counts mean

A recognized `You catch a shark!` is one catch success. Lures are consumed on successful catches, so they do not reveal silent failed attempts. Failures are inferred from supported roll schedules while the action is active.

| Lures per catch | First roll after start | Subsequent roll intervals | Display |
| --- | --- | --- | --- |
| None | 5 ticks | 6 ticks | One estimated failure total and rate |
| 1 | 4 or 5 ticks | 5 or 6 ticks | Possible failure totals and rate range |
| 3 | 4 ticks | 5 ticks | One estimated failure total and rate |
| 5 | 3 or 4 ticks | 4 or 5 ticks | Possible failure totals and rate range |

The three-lure phase and one-lure mode were checked live. A five-lure live trace verifies raw value 5, five lures consumed per catch, an initial four-tick catch, and recurring four/five-tick gaps. Its alternative three-tick first roll and the no-lure initial phase still need separate live validation in this project. [Jagex's September 3 clarification](https://secure.runescape.com/m=news/summer-sweep-up-miscellaneous?oldschool=1) corrects the wrong tick numbers in its original announcement. [The original skilling mechanics guide](https://github.com/data-dependent/osrs-guides/blob/master/skilling.md#tick-manipulation-ii-the-skilling-tick) explains the difference between the initial timer delay and repeating intervals.

For three lures, a start at tick T schedules rolls at T+4, T+9, T+14, etc. A catch on a due tick is a success; no catch is an inferred failure. The first roll can fail, so the first *fish* is not necessarily caught at T+4.

One lure randomly changes the interval. RuneLite does not expose which delay was chosen after a failed roll. The plugin keeps every schedule consistent with the observed catches and displays the smallest and largest possible failure totals. For example, `Catch fail: 12-15` and `Catch rate: 85.0%-87.6%` are timing bounds, **not a statistical confidence interval**. The tracker never substitutes an average 5.5-tick schedule or pretends these failures are exact.

A missed tick, an impossible catch phase, ambiguous catches, or Fishing XP without a recognized catch invalidates the latest anchored run's inferred counts. Its observed catches remain. Earlier completed runs remain. **Catch rate** uses only catches with tracked attempts. Catches outside a tracked run remain in **Catch success**, and a small note shows how many catches the rate uses. The rate shows `--` only until a measured sample exists. Deliberate stops keep completed trials and discard the unfinished cycle. A queued inventory-filling catch is preserved once.

The plugin remembers a clicked/interacted fishing NPC even when the live interaction clears, checking position, plane, world view, and proximity before using it. NPC 16335 is explicitly mapped to sharks because the tested RuneLite SDK omits it. An empty lure inventory with no tackle box uses the no-lure rule. A tackle box with unknown contents is successes-only. Live consumption verified raw game values 1, 3, and 5 as the selected lure quantities. The plugin reads the game choice before the first catch and ignores old manual lure settings. Changing the game choice interrupts the old attempt schedule while preserving session totals and fishing time; a fresh harpooning start resumes estimates with the newly detected choice. Unrecognized values remain successes-only. The game setting is exposed as [SHARK_LURE_USE_QUANTITY](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java).

Tick manipulation and inherited skilling timers can change the first phase or cadence. This tracker measures ordinary uninterrupted harpooning; it cannot directly observe the server's random rolls. A displayed rate does not by itself confirm or refute a wiki catch probability. Keep level, boosts, gear, and lure mode consistent when studying a rate.

## History and export

Manual fishing sessions are saved locally in `.runelite/plugin-data/attempt-tracker/fishing-sessions.json`, with up to 200 sessions. CSV exports contain catches, measured catches, minimum/maximum failures and rates, both clocks in milliseconds, and fishing ticks. Rates are proportions from 0 to 1. Unknown rates are blank.

All production filesystem operations use RuneLite's [Filepath API](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/util/Filepath.html). History is read with a 2 MiB bound and replaced atomically within the plugin data directory, with a non-atomic move fallback where required by the filesystem. CSV export uses `Filepath.Chooser` and writes only the explicitly selected file; it does not create temporary files beside it or access its parent. The I/O worker shuts down gracefully and finishes queued saves without thread interruption.

RuneLite migrates the previous `.runelite/attempt-tracker` directory on first use of `getPluginDirectory()` when the new directory does not already exist. Both session files, backups, and traces move together.

Legacy per-setup history remains in `sessions.json`. On the first upgrade, the most recently updated timed fishing profile is imported into the current manual session. Old elapsed times were not recorded and cannot be recovered. Other legacy profiles remain in their original file. Corrupt JSON is backed up before replacement.

The original cooking, pickpocketing, mining, woodcutting, and custom detectors remain available internally and retain their legacy data/configuration. The simplified sidebar displays fishing sessions. Advanced legacy settings are hidden from the configuration screen to keep ordinary shark tracking simple; existing stored values, including first-delay overrides, remain honored.

## Build and run

Use JDK 17. Compilation targets Java 11. The wrapper uses Gradle 8.10 and resolves RuneLite `latest.release` (1.13.1 in the tested build). A compatible version can be pinned with `-PruneLiteVersion=1.13.1`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
.\gradlew.bat test build releaseZip preview --console=plain
.\gradlew.bat run
```

On macOS/Linux use `./gradlew` instead of `.\gradlew.bat`.

Outputs:

- `build/distributions/attempt-tracker-1.1.9.zip`: executable development client, plugin JAR, Windows start script, metadata, README, icon, and license.
- `build/libs/attempt-tracker-1.1.9-all.jar`: executable client with the plugin registered.
- `build/libs/attempt-tracker-1.1.9.jar`: plugin-only JAR for development tooling.
- `build/preview/attempt-tracker.png`: sidebar and overlay preview using demonstration data.
- `build/reports/tests/test/index.html`: test report.

Extract the ZIP and run `start-attempt-tracker.bat`, or launch directly:

```powershell
java -ea -jar .\attempt-tracker-1.1.9-all.jar --developer-mode --disable-telemetry --profile attempt-tracker-dev
```

`-ea` is required for RuneLite's development plugin loader. The executable bundles client dependencies and the launcher, excluding JUnit/Mockito/test classes. The plugin-only JAR does not install itself in a normal client. The initial [Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/17633) was merged; further updates still require RuneLite's review and build process.

For a Jagex account, use RuneLite's official [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) development workflow. Keep `.runelite/credentials.properties` private; the plugin does not handle credentials.

## Validation

**204 automated tests pass**. They cover the existing detectors, fixed-cycle estimates, variable schedule bounds compared with exhaustive schedules, pauses, tab changes, manual reset, logout/login, clock persistence, CSV, corrupt-file backup, lifecycle races, and sidebar selection/layout. Activity clock checks cover every catalogued spot and representative fishing methods. Automatic lure tests cover stale manual declarations, mode changes, unknown values, no supply, hidden tackle-box supply, and the live five-lure trace. New checks cover immediate sidebar updates, history selection, logout/missing supply, and waiting for inventory loading before estimating. A 6,000-tick variable-failure fixture checks bounded tracker state. The real sidebar and overlay have also been rendered and visually inspected at the normal 225-pixel panel width.

Historical **v1.0.4** live test: crystal harpoon, three lures, Fishing 77, start tick 24. Deadlines 28 through 128 produced 17 catches and four inferred failures over 21 attempts. Lure consumption was 51, and Fishing XP was 22 per catch. Another 11 idle ticks added no attempts; logout preserved totals. This verifies that particular three-lure cadence. The one-lure live validation is documented below; it is separate from this older three-lure test.

Implementation references: [RuneLite Hooks](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/callback/Hooks.java), [FishingPlugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/fishing/FishingPlugin.java), [FishingSpot](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/game/FishingSpot.java), and [Plugin Hub development](https://github.com/runelite/plugin-hub).

This independent project is not endorsed by RuneLite or Jagex.

Version 1.1.3 separates actual fishing time from shark roll estimates. NPC clicks and interaction events select spots using RuneLite's global catalog, with a named-spot/action fallback for missing IDs. Fishing animations confirm activity; walking and idle time are excluded. Normal rod casting-to-fishing, looping nets, and barehand fishing transitions continue the clock. Aerial fishing supports remote pools. Logout, reset, action changes, full inventory, player movement while fishing, and spot movement/despawn pause it. Unsupported schedules create no estimated failures. Catch counters still obey the existing shark filter. Automated checks cover every catalogued spot and representative methods; this is not a claim that every spot has been tested in-game. [RuneLite's spot catalog](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/game/FishingSpot.java) is the primary ID source.

Version 1.1.1 fixes a guard that kept one-lure tracking in successes-only mode when the plugin setting was changed before the game setting. Regression tests replay the actual one-lure trace: start 1036, catches 1040, 1045, 1051, 1062, 1074, one lure consumed per catch, yielding five successes and two inferred failures. The patched v1.1.1 live test then recorded 14 measured catches and five inferred failures in the first completed run, consumed 14 lures, and paused at the stop. Version 1.1.2 also shows the rate for the measured sample when other catches in the same session were untracked; it retains those catches and identifies the sample beneath the rate.

Version 1.1.9 fixes enabling or updating the plugin while already logged in. Startup reads of game state and lure settings run on RuneLite's client thread, with lifecycle guards that discard callbacks from a disabled instance. Regression checks exercise actual Swing startup, disable/re-enable, the bundled sidebar icon, and the visible settings descriptor. Tests use an isolated home directory.
