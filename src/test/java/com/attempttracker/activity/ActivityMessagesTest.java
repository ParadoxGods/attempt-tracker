package com.attempttracker.activity;

import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivityMessagesTest
{
	@Test
	public void officialFishingFixturesIdentifySingleSuccessfulActions()
	{
		// https://github.com/runelite/runelite/blob/master/runelite-client/src/test/java/net/runelite/client/plugins/fishing/FishingPluginTest.java
		assertOutcome("You catch a Lobster.", "Fishing: Lobster", "FISHING", true, true);
		assertOutcome("You catch an Anglerfish.", "Fishing: Anglerfish", "FISHING", true, true);
		assertOutcome("You catch 15 Karambwanji.", "Fishing: Karambwanji", "FISHING", true, true);
	}

	@Test
	public void liveFishingExclamationMessagesIdentifySingleSuccessfulActions()
	{
		// Exact shark message reported during live game testing.
		assertOutcome("You catch a shark!", "Fishing: Shark", "FISHING", true, true);
		assertOutcome("<col=00ff00>YOU CATCH A SHARK!</col>", "Fishing: Shark", "FISHING", true, true);
		assertOutcome("You catch an Anglerfish!", "Fishing: Anglerfish", "FISHING", true, true);
		assertOutcome("You catch some Shrimps!", "Fishing: Shrimp", "FISHING", true, true);
		assertOutcome("You catch 15 Karambwanji!", "Fishing: Karambwanji", "FISHING", true, true);
	}

	@Test
	public void fishingExclamationMessagesRemainAnchoredAndWhitelisted()
	{
		String[] messages = {
			"Friend: You catch a shark!", "You catch a shark! Good luck!",
			"You catch a shark! You catch a shark!", "You catch a shark!!",
			"You catch 15 Karambwanji! You catch 15 Karambwanji!",
			"You catch 15 Karambwanji! Good luck!", "Friend: You catch 15 Karambwanji!",
			"You catch a cold!", "You catch a shark lure!", "You catch 2 sharks!",
			"You successfully cook a shark!", "You accidentally burn the shark!",
			"You pick the man's pocket!", "You manage to mine some iron!", "You get some logs!"
		};
		for (String message : messages)
		{
			assertFalse("Should ignore: " + message, ActivityMessages.parse(message).isPresent());
		}
	}

	@Test
	public void cookAndBurnMessagesShareTheSameCanonicalActivity()
	{
		// Success examples copied from RuneLite CookingPluginTest.java.
		assertPair("You successfully cook a shark.", "You accidentally burn the shark.", "Cooking: Shark");
		assertPair("You successfully cook an anglerfish.", "You accidentally burn the anglerfish.", "Cooking: Anglerfish");
		assertPair("You manage to cook a tuna.", "You accidentally burn the tuna.", "Cooking: Tuna");
		assertPair("You cook a bass.", "You accidentally burn the bass.", "Cooking: Bass");
		assertPair("You roast a lobster.", "You accidentally burn the lobster.", "Cooking: Lobster");
		assertPair("You cook the karambwan. It looks delicious.", "You accidentally burn the karambwan.", "Cooking: Karambwan");
		assertPair("You successfully bake a tasty garden pie.", "You accidentally burn the pie.", "Cooking: Pie");
		assertPair("You successfully fry a mushroom.", "You burn the mushroom in the fire.", "Cooking: Mushroom");
		// Observed raw-beef messages documented by the Chat Success Rates author:
		// https://oldschool.runescape.wiki/w/User:Skretzo/Chat_messages
		assertPair("You cook a piece of meat.", "You accidentally burn the meat.", "Cooking: Meat");
		assertPair("You cook some chicken.", "You accidentally burn the chicken.", "Cooking: Chicken");
	}

	@Test
	public void genericPieBurnsCannotBiasAFlavorSpecificSuccessSample()
	{
		assertOutcome("You successfully bake a tasty garden pie.", "Cooking: Pie", "COOKING", true, false);
		assertOutcome("You successfully bake an apple pie.", "Cooking: Pie", "COOKING", true, false);
		assertOutcome("You accidentally burn the garden pie.", "Cooking: Pie", "COOKING", false, false);
		assertOutcome("You accidentally burn the pie.", "Cooking: Pie", "COOKING", false, false);
		assertFalse(ActivityMessages.parse("You make a jug of wine.").isPresent());
		assertFalse(ActivityMessages.parse("The wine spoils.").isPresent());
	}

	@Test
	public void formattingAndCasingDoNotSplitSamples()
	{
		assertOutcome("  <col=00ff00>YOU CATCH A</col>  Shark.  ", "Fishing: Shark", "FISHING", true, true);
		assertOutcome("@mes_hl_gre@You successfully cook\u00a0some Shrimps.</col>", "Cooking: Shrimp", "COOKING", true, false);
		assertOutcome("You fail to pick the Master Farmer\u2019s pocket.", "Pickpocketing", "THIEVING", false, false);
	}

	@Test
	public void pickpocketingOutcomesStayTogetherDespiteTargetOrExtraLoot()
	{
		// Exact templates from Skretzo/runelite-plugins Pickpocketing.java;
		// pajlads/runelite-pickpocket-helper MessagePattern confirms terminal period.
		assertOutcome("You pick the man's pocket.", "Pickpocketing", "THIEVING", true, false);
		assertOutcome("You fail to pick the man's pocket.", "Pickpocketing", "THIEVING", false, false);
		assertOutcome("You pick the TzHaar-Hur's pocket.", "Pickpocketing", "THIEVING", true, false);
		assertFalse(ActivityMessages.parse("Your rogue clothing allows you to steal twice as much loot!").isPresent());
		assertFalse(ActivityMessages.parse("Your dodgy necklace protects you. It has 7 charges left.").isPresent());
		assertFalse(ActivityMessages.parse("You've been stunned!").isPresent());
	}

	@Test
	public void knownMiningOutputsUseCanonicalOreLabels()
	{
		// Templates and ore list from Skretzo/runelite-plugins MiningRock.java and ChatSuccessRatesAction.java.
		assertOutcome("You manage to mine some iron.", "Mining: Iron ore", "MINING", true, true);
		assertOutcome("You manage to mine some iron ore.", "Mining: Iron ore", "MINING", true, true);
		assertOutcome("You manage to mine some coal.", "Mining: Coal", "MINING", true, true);
		assertOutcome("You manage to mine some adamantite.", "Mining: Adamantite ore", "MINING", true, true);
		assertFalse(ActivityMessages.parse("You swing your pick at the rock.").isPresent());
	}

	@Test
	public void officialWoodcuttingFixturesIdentifyOutputs()
	{
		// https://github.com/runelite/runelite/blob/master/runelite-client/src/test/java/net/runelite/client/plugins/woodcutting/WoodcuttingPluginTest.java
		assertOutcome("You get some logs.", "Woodcutting: Logs", "WOODCUTTING", true, true);
		assertOutcome("You get some oak logs.", "Woodcutting: Oak logs", "WOODCUTTING", true, true);
		assertOutcome("You get an arctic log.", "Woodcutting: Arctic logs", "WOODCUTTING", true, true);
		assertOutcome("You get some mushrooms.", "Woodcutting: Mushrooms", "WOODCUTTING", true, true);
	}

	@Test
	public void unrelatedAndAmbiguousMessagesAreNotActionResults()
	{
		String[] messages = {
			null, "", " ", "Friend: You catch a shark.", "You catch a shark. Good luck!",
			"You catch a cold.", "You catch a shark lure.", "You catch 2 sharks.",
			"You catch an extra fish due to your Rada's blessing.",
			"Your spirit flakes allow you to catch an extra fish.",
			"Your cormorant returns with its catch.", "You don't have enough inventory space to do that.",
			"You attempt to pick the man's pocket.", "You need to empty your coin pouches before you can continue pickpocketing.",
			"You burn some marrentill in the incense burner.",
			"You half-cook the karambwan.", "You dry a piece of meat and extract the sinew.",
			"You cook the undead meat.", "You successfully cook a mystery item.",
			"You get some coins.", "A bird's nest falls out of the tree."
		};
		for (String message : messages)
		{
			assertFalse("Should ignore: " + message, ActivityMessages.parse(message).isPresent());
		}
	}

	private static void assertPair(String success, String failure, String activity)
	{
		assertOutcome(success, activity, "COOKING", true, false);
		assertOutcome(failure, activity, "COOKING", false, false);
	}

	private static void assertOutcome(String message, String activity, String skill, boolean success, boolean timed)
	{
		Optional<ActivityOutcome> parsed = ActivityMessages.parse(message);
		assertTrue("Expected action: " + message, parsed.isPresent());
		ActivityOutcome actual = parsed.get();
		assertEquals(activity, actual.getActivity());
		assertEquals(skill, actual.getSkillName());
		assertEquals(success, actual.isSuccess());
		assertEquals(timed, actual.isTimed());
	}
}
