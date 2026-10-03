# Attempt Tracker

<img src="../icon.png" alt="Attempt Tracker fish and stopwatch icon" width="48" height="48">

Version **1.3.0 development** is a fishing session tracker for RuneLite. The sidebar shows **Detected lures**, **Catch success**, **Catch fail**, **Catch rate**, **Logged in** time, **Fishing** time, and elapsed fishing ticks. History and diagnostics are folded away. The plugin observes gameplay; it does not automate it.

## Using the plugin

1. Launch the development client and enable **Attempt Tracker**. Open its fish-and-stopwatch sidebar button.
2. Choose 1, 3, or 5 lures in-game. The sidebar shows **Detected lures: 3 per catch**, for example, and updates as the game choice or carried supply changes; no plugin setting is needed. This is your selected quantity, not the number of lures left. When no lures or tackle box are carried, it shows **None / No lures available**, and estimates use the no-lure schedule. An unverified tackle box shows **Unknown**; an unloaded inventory shows `--`. A note identifies a needed harpooning restart.
3. Interact with a fishing spot. The fishing clock starts when you begin fishing there, after any walking. Ordinary shark attempt estimates separately require the game message `You start harpooning fish.` to anchor their schedule. Casing, repeated spaces, a missing final punctuation mark, `!`, and `...` are also accepted. Tick-manipulated timing instead starts from a catch with accepted fishing evidence, then reconstructs qualified timer operations; its anchoring catch is excluded from the rate sample.
4. Fishing time and ticks pause on logout, an unrelated action or animation, movement, a blocked inventory, or a moved/despawned fishing spot. Qualified manipulation actions can bridge active fishing. Clicking Inventory or Skills tabs, examining objects, or dropping fish does not interrupt the timer.
5. Interact with a fishing spot again to resume fishing time; a fresh harpooning start resumes shark attempt measurement. Existing totals remain. **Reset session** is the only action that clears the current totals and timers; it archives the previous session first.

The current session survives logout, world changes, client restarts, and plugin disable/re-enable. Logged-in time includes time spent banking or standing idle; fishing time includes active fishing at a selected spot, even when its attempt schedule is unknown or excluded by the shark filter. Neither clock includes time offline. Timers use a monotonic clock, so changing the system time does not change their elapsed time. A reset pauses fishing until a new fishing interaction or start message.

**Session history** lets you view earlier manual sessions. The **Detected lures** row always shows the live choice, even when viewing an older session. Logging out replaces its amount with `--`. **Details** contains the status/diagnostic text and **Export CSV**. Visible configuration options control the overlay, inclusion of all fish, tick-manipulated timing, diagnostics, and local tick tracing. Changing overlay or diagnostic settings does not pause fishing.

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

A missed tick, an impossible catch phase, ambiguous catches, or Fishing XP without a recognized catch invalidates the latest anchored run's inferred counts. Its observed catches remain. Earlier completed runs remain. **Catch rate** uses only catches with tracked attempts. Catches outside a tracked run remain in **Catch success**, and a small note shows how many catches the rate uses. The rate shows `--` when no supported attempt sample exists. Deliberate stops keep completed trials and discard the unfinished cycle. A queued inventory-filling catch is preserved once.

The plugin remembers a clicked/interacted fishing NPC even when the live interaction clears, checking position, plane, world view, and proximity before using it. NPC 16335 is explicitly mapped to sharks because the tested RuneLite SDK omits it. An empty lure inventory with no tackle box uses the no-lure rule. A tackle box with unknown contents is successes-only. Live consumption verified raw game values 1, 3, and 5 as the selected lure quantities. The plugin reads the game choice before the first catch and ignores old manual lure settings. Changing the game choice interrupts the old attempt schedule while preserving session totals and fishing time; a fresh harpooning start resumes estimates with the newly detected choice. Unrecognized values remain successes-only. The game setting is exposed as [SHARK_LURE_USE_QUANTITY](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java).

Tick manipulation and inherited skilling timers can change the first phase or cadence. Supported ordinary harpooning retains its established schedule. Qualified timer operations establish a separate inferred attempt sample. Activity windows and click rhythm do not enter the displayed rate denominator. A displayed rate does not by itself confirm or refute a wiki catch probability. Keep level, boosts, gear, and lure mode consistent when studying a rate.

## Fishing timing models

### Shared-timer reconstruction

The tracker follows qualified operations that change the shared skilling timer. It supports recognized shark/tuna/swordfish harpooning and the fly/bait and barbarian rod families. Harpooning may roll on the first accepted interaction; the rod family excludes that first-interaction roll. Independent fishing timers, including karambwan, minnow and eel methods, are not assigned this model.

An observed catch with accepted fishing evidence establishes timer zero. The anchoring catch stays in **Catch success**, but is excluded from the qualified rate sample. The model then advances only through contiguous observations at a stationary player and unchanged nearby spot. It counts a catch or silent failure when supported fishing evidence reaches a reconstructed roll deadline. Equipping a fast weapon, clicking a spot, looping an animation, seeing a hit, or receiving Fishing XP alone cannot establish a roll.

Supported timer operations can be combined without requiring one fixed click rhythm:

- **Attack-assisted fishing:** a qualified incoming combat hit and retarget, auto-retaliate enabled, and known effective weapon speed from two through twenty ticks can reset an expired, cleared timer to half that speed, rounded down. A two/three-tick weapon therefore sets one and permits a two-tick harpoon sequence. Fresh accepted harpooning at the resulting deadline reaches a modeled roll and clears its logical fishing interaction, even when the client still displays the fishing target; an end-of-tick null actor target is not required. A continuing ordinary harpoon interaction does not establish that clearing condition.
- **Three-tick production:** selected swamp tar plus a supported clean herb, or a knife plus teak/mahogany logs, must be followed by a fresh matching production animation. Accepted production sets an expired timer to two, allowing subsequent fishing to reach a modeled three-tick deadline. An item-use click or unrelated fletching animation cannot reset the model.
- **Food:** a supported Eat selection must have a fresh eating animation and matching observed inventory consumption. Verified ordinary food adds three to the current timer; verified karambwan adds two. These are additive delays, not unconditional resets.
- **Outgoing attacks:** a fresh supported attack animation, a combat target and a known effective weapon speed set the full attack delay. An Attack click alone does not establish this operation.

The same timer state handles qualified irregular mixtures of these operations. Unknown recipes, attack animations or food, competing actions, consumption ambiguity, missing ticks, movement, spot changes, unrecognized outcome XP and resource refusals discontinue the current reconstruction. A contradictory catch phase or multiple catch messages withdraw the current segment's inferred counts while retaining its observed catches. Deliberate stops preserve completed trials. Logout and restart preserve totals but require a new anchor.

The sidebar focuses the qualified timer sample separately from ordinary and older uncertain observations. **40 catches over 50 qualified attempts gives 80.0%**. The note `Timing sample: 40 of 100 catches` identifies the catches used by the percentage, while **Catch success** still includes all 100 observed catches. The tooltip and overlay label this timing inference. Legacy saved two-tick samples remain readable and are preserved separately.

This is a mechanics model based on public client observations. RuneLite does not expose the hidden shared timer or a universal failed-roll event. Automated fixtures exercise the reconstructed operations, but no live generalized tick-manipulation validation is available for this build. Cut-eat methods with ambiguous cutting/consumption, unsupported independent timers, unrecognized resets and arbitrary unknown methods are not claimed as exact support.

### Activity tracking and unsupported timing

All recognized fish are included by default, with an optional shark-only filter. The global spot and animation catalogs, plus a named-spot/action fallback, track fishing activity independently of whether an attempt model applies. Aerial cormorant returns are recognized. Minigame reward collection and unknown outcomes are not assigned invented catches or attempts.

Local hits and item-use/eating transitions can briefly bridge known fishing activity, but cannot start fishing or sustain it indefinitely. Descriptive interaction rhythm remains diagnostic evidence. Stackable minnow/karambwanji catches and open barrels prevent occupied slots alone from proving fishing is blocked; explicit inventory-space stop messages still pause activity. Barrel capacity is not inferred.

Adaptive zero-or-one-roll activity windows remain in saved diagnostics and raw CSV columns for compatibility. They do not enter **Catch fail** or **Catch rate**. For example, 30 catches across 90 possible activity windows retain all 30 successes but cannot establish a rate denominator. With no supported sample, the sidebar and overlay show `--` and `Waiting for supported attempts`. Existing completed ordinary or qualified samples remain available. Ordinary one/five-lure schedule bounds are still displayed when applicable; the tracker never chooses a midpoint to manufacture a percentage.

Optional local traces record numeric timing state, action evidence and exclusion information without attacker names. The callback-event buffer is bounded to 64 events and 4 KiB per frame; excess evidence is explicitly marked truncated. It records client callback order, which does not establish server execution order. Categories and numeric values are recorded, not arbitrary chat or inventory dumps. Trace files still use the Filepath worker. Diagnostics distinguish fishing activity from qualified attempt reconstruction.

Primary observation references: [RuneLite fishing catch messages](https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/plugins/fishing/FishingPlugin.java), [spot catalog](https://github.com/runelite/runelite/blob/runelite-parent-1.13.1/runelite-client/src/main/java/net/runelite/client/game/FishingSpot.java), [HitsplatApplied](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/events/HitsplatApplied.html), and the original [shared skilling timer investigation](https://github.com/data-dependent/osrs-guides/blob/master/skilling.md#flinching).

## History and export

Manual fishing sessions are saved locally in `.runelite/plugin-data/attempt-tracker/fishing-sessions.json`, with up to 200 sessions. CSV exports contain catches, measured catches, supported minimum/maximum failures and rates, both clocks in milliseconds, and fishing ticks. `modeled_catches`, `modeled_fail_min`, `modeled_fail_max` and `modeled_timing` preserve the generalized timing sample. The older `two_tick_catches`, `two_tick_failures` and `two_tick_timing` columns preserve legacy samples. `adaptive_catches`, `adaptive_fail_upper` and `adaptive_timing` retain raw activity-window evidence, independently of the displayed denominator. `measured_catches` still means the ordinary timing sample; `rate_catches`, `catch_fail_min`, `catch_fail_max` and rate columns follow the active sidebar sample. Rates are proportions from 0 to 1. Unknown rates are blank.

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

- `build/distributions/attempt-tracker-1.3.0.zip`: executable development client, plugin JAR, Windows start script, metadata, README, icon, and license.
- `build/libs/attempt-tracker-1.3.0-all.jar`: executable client with the plugin registered.
- `build/libs/attempt-tracker-1.3.0.jar`: plugin-only JAR for development tooling.
- `build/preview/attempt-tracker.png`: sidebar and overlay preview using demonstration data.
- `build/reports/tests/test/index.html`: test report.

Extract the ZIP and run `start-attempt-tracker.bat`, or launch directly:

```powershell
java -ea -jar .\attempt-tracker-1.3.0-all.jar --developer-mode --disable-telemetry --profile attempt-tracker-dev
```

`-ea` is required for RuneLite's development plugin loader. The executable bundles client dependencies and the launcher, excluding JUnit/Mockito/test classes. The plugin-only JAR does not install itself in a normal client. The initial [Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/17633) was merged; further updates still require RuneLite's review and build process.

For a Jagex account, use RuneLite's official [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) development workflow. Keep `.runelite/credentials.properties` private; the plugin does not handle credentials.

## Validation

Version 1.3.0 passes **336 automated tests** and the standard Plugin Hub production build against SDK 1.13.1 with Java 11 output. Thirty timer-model tests cover two-, three-, four- and alternating two/three-tick cycles. Sixteen adapter tests replay actual event handlers, including silent failures, production and food confirmation, stale evidence and ambiguous action order. These are simulated observations, not live server verification. The sidebar and overlay were visually checked at the 225px sidebar width.

Automated fixtures cover two-tick flinching with lingering client targets, three-tick harpoon/rod production, additive food delays, outgoing attacks, irregular mixed operations and a simulated alternating 2.5-tick sequence. They distinguish fresh harpoon re-engagement from continuous fishing, and check rod first-interaction exclusion, incomplete evidence, contradictory outcomes, missing ticks, restored counts and bounded state. These checks establish implementation behavior under their supplied observations, not live server-roll accuracy.

Ordinary schedule tests compare variable-lure bounds with exhaustive schedules and long runs. Adapter, persistence and UI checks cover pause conditions, Inventory/Skills tab changes, manual reset, logout/login, both clocks, automatic lure changes, inventory loading, unknown tackle-box supply, CSV, corrupt-file backup and lifecycle races. Qualified timing samples retain their separate denominators despite earlier adaptive windows. Tests check catch coverage, unknown versus genuine zero-success samples, history selection, closed-sidebar caching and layout at 225px. Blocked-disk fixtures verify that session writes do not hold the foreground lifecycle lock.

Earlier live ordinary-shark checks established three-lure starts at +4 and recurring +5 ticks, one-lure catch gaps compatible with 5/6-tick intervals, and five-lure consumption and recurring 4/5-tick gaps. One completed three-lure run produced 17 catches and four inferred failures; one completed one-lure run produced 14 measured catches and five inferred failures. Those observations validate the tested ordinary setups, not this build's generalized manipulation model. No live two/three-tick or arbitrary mixed-method run has been independently validated for this build.

Implementation references: [RuneLite Hooks](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/callback/Hooks.java), [FishingPlugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/fishing/FishingPlugin.java), [FishingSpot](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/game/FishingSpot.java), and [Plugin Hub development](https://github.com/runelite/plugin-hub).

This independent project is not endorsed by RuneLite or Jagex.
