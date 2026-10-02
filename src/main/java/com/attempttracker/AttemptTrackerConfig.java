package com.attempttracker;

import net.runelite.api.Skill;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(AttemptTrackerConfig.GROUP)
public interface AttemptTrackerConfig extends Config
{
	String GROUP = "attempttracker";

	@ConfigSection(name = "Display", description = "Simple display", position = 0)
	String display = "display";

	@ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show the current fishing session on screen", section = display, position = 0)
	default boolean showOverlay() { return true; }

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "expectedRate", name = "Rate hypothesis (%)", description = "Compare the sample interval with this hypothesis. 0 disables comparison. This does not change counts or assert an expected game rate.", section = display, hidden = true, position = 1)
	default int expectedRate() { return 95; }

	@ConfigItem(keyName = "setupNotes", name = "Setup notes", description = "Optional notes for invisible boosts, diary effects, or other conditions; stored with each profile", section = display, hidden = true, position = 2)
	default String setupNotes() { return ""; }

	String observed = "observed";

	@ConfigItem(keyName = "cooking", name = "Cooking", description = "Track supported food cook/burn messages", section = observed, hidden = true, position = 0)
	default boolean cooking() { return true; }

	@ConfigItem(keyName = "pickpocketing", name = "Pickpocketing", description = "Track explicit pickpocket success/failure messages, grouped by NPC", section = observed, hidden = true, position = 1)
	default boolean pickpocketing() { return true; }

	String gathering = "gathering";
	@ConfigItem(keyName = "autoDetectLures", name = "Detect shark lures automatically", description = "Read the in-game lure selection instead of a manual declaration", section = gathering, hidden = true, position = 2)
	default boolean autoDetectLures() { return true; }

	@ConfigItem(keyName = "fixedTiming", name = "Use fixed-cycle estimates", description = "Count complete attempt cycles from a recognized start message. A cycle with no catch is an estimated failure. Validate the start-to-first-roll delay and cycle for your method; variable timing cannot use a fixed cycle.", section = gathering, hidden = true, position = 0)
	default boolean fixedTiming() { return false; }

	@ConfigItem(keyName = "fishing", name = "Fishing", description = "Track supported fishing catches", section = gathering, hidden = true, position = 1)
	default boolean fishing() { return true; }

	@ConfigItem(keyName = "sharksOnly", name = "Sharks only", description = "Restrict fishing to harpooning sharks. Disable to pool all fish caught with the same spot and method.", section = gathering, hidden = true, position = 2)
	default boolean sharksOnly() { return true; }

	@ConfigItem(keyName = "sharkLures", name = "Shark lures per catch", description = "Legacy manual override, used only when automatic lure detection is disabled", section = gathering, hidden = true, position = 3)
	default SharkLures sharkLures() { return SharkLures.THREE; }

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "sharkFirstRollDelay", name = "Shark first roll delay (ticks)", description = "0 uses the preset: three lures first roll 4 ticks after the start message, then every 5 ticks. No lures starts after 5 ticks, then repeats every 6. Set an override only after checking a tick trace.", section = gathering, hidden = true, position = 4)
	default int sharkFirstRollDelay() { return 0; }

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "fishingCycle", name = "Other fishing cycle (ticks)", description = "Fixed cycle for other fishing methods; requires Use fixed-cycle estimates. Shark harpooning uses the selected lure rule in both Sharks only and all-fishing modes.", section = gathering, hidden = true, position = 5)
	default int fishingCycle() { return 5; }

	@ConfigItem(keyName = "mining", name = "Mining", description = "Track normal ore success messages; confirm the cycle for your pickaxe and rock before estimating", section = gathering, hidden = true, position = 6)
	default boolean mining() { return true; }

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "miningCycle", name = "Mining cycle (ticks)", description = "User-validated fixed roll cycle; 4 is a starting configuration, not a universal mining rule", section = gathering, hidden = true, position = 7)
	default int miningCycle() { return 4; }

	@ConfigItem(keyName = "woodcutting", name = "Woodcutting", description = "Track normal log success messages; variable tool effects require success-only counting", section = gathering, hidden = true, position = 8)
	default boolean woodcutting() { return true; }

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "woodcuttingCycle", name = "Woodcutting cycle (ticks)", description = "User-validated fixed roll cycle for the selected method", section = gathering, hidden = true, position = 9)
	default int woodcuttingCycle() { return 4; }

	String custom = "custom";

	@ConfigItem(keyName = "customEnabled", name = "Enable custom activity", description = "Matching custom outcomes take priority over built-in message parsing", section = custom, hidden = true, position = 0)
	default boolean customEnabled() { return false; }

	@ConfigItem(keyName = "customName", name = "Activity name", description = "Name used in the overlay, history and export", section = custom, hidden = true, position = 1)
	default String customName() { return "Custom activity"; }

	@ConfigItem(keyName = "customSkill", name = "Skill", description = "Skill used to record the level and detect missing timed success messages", section = custom, hidden = true, position = 2)
	default Skill customSkill() { return Skill.FISHING; }

	@ConfigItem(keyName = "customMode", name = "Counting method", description = "Observed requires messages for both outcomes. Timed requires a fixed cycle and active animation IDs.", section = custom, hidden = true, position = 3)
	default CustomMode customMode() { return CustomMode.OBSERVED; }

	@ConfigItem(keyName = "customSuccess", name = "Success message prefixes", description = "One plain-text prefix per line, case insensitive. Game messages only; no regular expressions.", section = custom, hidden = true, position = 4)
	default String customSuccess() { return ""; }

	@ConfigItem(keyName = "customStart", name = "Start message prefixes", description = "Required for timed mode. One plain-text prefix per line that marks the start of an uninterrupted action; the first attempt follows the configured initial delay.", section = custom, hidden = true, position = 9)
	default String customStart() { return ""; }

	@ConfigItem(keyName = "customFailure", name = "Failure message prefixes", description = "One plain-text prefix per line for observed mode. Both outcomes must be configured.", section = custom, hidden = true, position = 5)
	default String customFailure() { return ""; }

	@ConfigItem(keyName = "customAnimations", name = "Active animation IDs", description = "Comma-separated IDs for timed mode. The sidebar can display your current ID with Diagnostics enabled.", section = custom, hidden = true, position = 6)
	default String customAnimations() { return ""; }

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "customCycle", name = "Cycle (ticks)", description = "User-validated fixed attempt cycle for timed custom mode", section = custom, hidden = true, position = 7)
	default int customCycle() { return 5; }

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "customFirstRollDelay", name = "First roll delay (ticks)", description = "Delay from the start message to the first roll. 0 uses Cycle. Later attempts use Cycle regardless of the initial delay. Validate both with a tick trace.", section = custom, hidden = true, position = 8)
	default int customFirstRollDelay() { return 0; }

	@ConfigSection(name = "Diagnostics", description = "Local trace for validating activity timing", position = 4, closedByDefault = true)
	String diagnostics = "diagnostics";

	@ConfigItem(keyName = "diagnostics", name = "Show diagnostics", description = "Display the current tick, animation, frame and target in the sidebar", section = diagnostics, position = 0)
	default boolean diagnostics() { return false; }

	@ConfigItem(keyName = "saveTrace", name = "Save tick trace", description = "Save local CSV tick/animation/XP/lure observations to .runelite/attempt-tracker/traces; no network uploads", section = diagnostics, position = 1)
	default boolean saveTrace() { return false; }

	enum SharkLures
	{
		NONE("No lures", 6, 0), ONE("1 lure", 0, 1), THREE("3 lures", 5, 3), FIVE("5 lures", 0, 5);
		private final String label;
		private final int cycle;
		private final int quantity;
		SharkLures(String label, int cycle, int quantity) { this.label = label; this.cycle = cycle; this.quantity = quantity; }
		public int getCycle() { return cycle; }
		public int getQuantity() { return quantity; }
		public static SharkLures fromGameValue(int value)
		{
			switch (value) { case 1: return ONE; case 3: return THREE; case 5: return FIVE; default: return null; }
		}
		@Override public String toString() { return label; }
	}

	enum CustomMode
	{
		OBSERVED("Observed chat outcomes"), TIMED("Fixed-cycle estimate");
		private final String label;
		CustomMode(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
}
