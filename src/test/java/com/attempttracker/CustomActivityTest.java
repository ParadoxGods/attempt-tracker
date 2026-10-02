package com.attempttracker;

import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CustomActivityTest
{
	@Test
	public void timedInitialDelayIsValidatedAndSeparatesDefinitions()
	{
		AttemptTrackerConfig config = timedConfig();
		CustomActivity defaultDelay = new CustomActivity(config);
		assertEquals(defaultDelay.cycle, defaultDelay.firstDelay);
		when(config.customFirstRollDelay()).thenReturn(4);
		CustomActivity earlyFirstRoll = new CustomActivity(config);
		assertTrue(earlyFirstRoll.usable());
		assertEquals(4, earlyFirstRoll.firstDelay);
		assertNotEquals(defaultDelay.signature(), earlyFirstRoll.signature());
		for (int invalid : new int[]{-1, 101, Integer.MAX_VALUE})
		{
			when(config.customFirstRollDelay()).thenReturn(invalid);
			CustomActivity rejected = new CustomActivity(config);
			assertFalse(rejected.usable());
			assertTrue(rejected.problem.contains("first-roll delay"));
		}
	}

	@Test
	public void formattingNormalizationAgreesForConfiguredPrefixesAndGameMessages()
	{
		AttemptTrackerConfig config = observedConfig();
		when(config.customSkill()).thenReturn(Skill.THIEVING);
		when(config.customSuccess()).thenReturn("<col=ffffff>You pick the Master Farmer's</col>");
		when(config.customFailure()).thenReturn("@mes_hl_red@You fail to pick the Master Farmer\u2019s");
		CustomActivity activity = new CustomActivity(config);
		assertTrue(activity.usable());
		assertEquals(Boolean.TRUE, activity.outcome("@mes_hl_gre@YOU PICK\u00a0THE Master Farmer\u2019s pocket.</col>"));
		assertEquals(Boolean.FALSE, activity.outcome("<col=ff0000>You fail to pick the Master Farmer's pocket.</col>"));
		assertEquals("you fail to pick the master farmer's pocket.",
			CustomActivity.normalize(" @MES_HL_RED@You fail to pick\u00a0the Master Farmer\u2019s pocket.</col> "));
	}

	@Test
	public void prefixesAreAnchoredAndLiteralSoQuotedMessagesCannotBecomeResults()
	{
		CustomActivity activity = new CustomActivity(observedConfig());
		assertEquals(Boolean.TRUE, activity.outcome("You catch a shark."));
		assertNull(activity.outcome("Friend: You catch a shark."));
		assertNull(activity.outcome("You receive a message: You catch a shark."));
		assertNull(activity.outcome(null));
		AttemptTrackerConfig literal = observedConfig();
		when(literal.customSuccess()).thenReturn("You catch .*");
		assertNull(new CustomActivity(literal).outcome("You catch a shark."));
	}

	@Test
	public void overlappingOutcomePrefixesRejectTheDefinitionAfterNormalization()
	{
		for (String failure : new String[]{"You catch", "You catch a shark", "@mes_hl_red@YOU\u00a0CATCH A"})
		{
			AttemptTrackerConfig config = observedConfig();
			when(config.customFailure()).thenReturn(failure);
			CustomActivity activity = new CustomActivity(config);
			assertFalse(activity.usable());
			assertTrue(activity.problem.contains("overlap"));
			assertNull(activity.outcome("You catch a shark."));
		}
	}

	@Test
	public void observedDefinitionsRequireBothOutcomesAndARealSkill()
	{
		AttemptTrackerConfig noFailure = observedConfig();
		when(noFailure.customFailure()).thenReturn("<col=ff0000></col>");
		assertFalse(new CustomActivity(noFailure).usable());
		assertTrue(new CustomActivity(noFailure).problem.contains("failure prefixes"));
		for (Skill skill : new Skill[]{Skill.OVERALL, null})
		{
			AttemptTrackerConfig config = observedConfig();
			when(config.customSkill()).thenReturn(skill);
			assertFalse(new CustomActivity(config).usable());
		}
		AttemptTrackerConfig disabled = observedConfig();
		when(disabled.customEnabled()).thenReturn(false);
		when(disabled.customSuccess()).thenReturn("");
		assertFalse(new CustomActivity(disabled).usable());
		assertEquals("", new CustomActivity(disabled).problem);
	}

	@Test
	public void timedDefinitionsRejectInvalidAnimationListsAndCycles()
	{
		for (String animations : new String[]{"", "622,nope", "-1", "2147483648", "622,", "622,,623"})
		{
			AttemptTrackerConfig config = timedConfig();
			when(config.customAnimations()).thenReturn(animations);
			assertFalse(animations, new CustomActivity(config).usable());
			assertTrue(new CustomActivity(config).problem.contains("animation"));
		}
		for (int cycle : new int[]{0, -1, 101, Integer.MAX_VALUE})
		{
			AttemptTrackerConfig config = timedConfig();
			when(config.customCycle()).thenReturn(cycle);
			assertFalse(new CustomActivity(config).usable());
			assertTrue(new CustomActivity(config).problem.contains("cycle"));
		}
		assertTrue(new CustomActivity(timedConfig()).usable());
	}

	@Test
	public void timedDefinitionsRequireLiteralAnchoredStartPrefixes()
	{
		AttemptTrackerConfig config = timedConfig();
		when(config.customStart()).thenReturn("<col=ffffff>You start harpooning</col>\n@mes_hl_gre@YOU START\u00a0HARPOONING\nYou cast out your line.");
		CustomActivity activity = new CustomActivity(config);
		assertTrue(activity.usable());
		assertEquals(2, activity.starts.size());
		assertTrue(activity.matchesStart("@mes_hl_gre@YOU START\u00a0HARPOONING fish.</col>"));
		assertTrue(activity.matchesStart("You cast out your line."));
		assertFalse(activity.matchesStart("Friend: You start harpooning fish."));
		assertFalse(activity.matchesStart("You catch a shark!"));
		assertFalse(activity.matchesStart(null));

		when(config.customStart()).thenReturn("<col=ffffff></col>");
		CustomActivity noStart = new CustomActivity(config);
		assertFalse(noStart.usable());
		assertTrue(noStart.problem.contains("start-message prefix"));

		when(config.customStart()).thenReturn("You start .*");
		assertFalse(new CustomActivity(config).matchesStart("You start harpooning fish."));
	}

	@Test
	public void observedDefinitionsDoNotNeedOrMatchStartMessages()
	{
		AttemptTrackerConfig config = observedConfig();
		when(config.customStart()).thenReturn("You start harpooning");
		CustomActivity activity = new CustomActivity(config);
		assertTrue(activity.usable());
		assertFalse(activity.matchesStart("You start harpooning fish."));
	}

	@Test
	public void timedStartPrefixesCannotOverlapEitherOutcome()
	{
		for (String start : new String[]{"You catch", "You catch a shark", "<col=ffffff>YOU</col>", "You fail to catch a shark"})
		{
			AttemptTrackerConfig config = timedConfig();
			when(config.customStart()).thenReturn(start);
			when(config.customFailure()).thenReturn("You fail to catch");
			CustomActivity activity = new CustomActivity(config);
			assertFalse(activity.usable());
			assertTrue(activity.problem.contains("start and outcome prefixes overlap"));
		}
	}

	@Test
	public void changingStartDefinitionChangesItsSetupSignature()
	{
		AttemptTrackerConfig config = timedConfig();
		String initial = new CustomActivity(config).signature();
		when(config.customStart()).thenReturn("You cast out your line.");
		assertNotEquals(initial, new CustomActivity(config).signature());
		when(config.customStart()).thenReturn("  <col=ffffff>YOU START HARPOONING</col>  ");
		assertEquals(initial, new CustomActivity(config).signature());
	}

	private static AttemptTrackerConfig observedConfig()
	{
		AttemptTrackerConfig config = mock(AttemptTrackerConfig.class);
		when(config.customEnabled()).thenReturn(true);
		when(config.customName()).thenReturn("Custom fishing");
		when(config.customSkill()).thenReturn(Skill.FISHING);
		when(config.customMode()).thenReturn(AttemptTrackerConfig.CustomMode.OBSERVED);
		when(config.customCycle()).thenReturn(5);
		when(config.customSuccess()).thenReturn("You catch a");
		when(config.customFailure()).thenReturn("You fail to catch");
		return config;
	}

	private static AttemptTrackerConfig timedConfig()
	{
		AttemptTrackerConfig config = observedConfig();
		when(config.customMode()).thenReturn(AttemptTrackerConfig.CustomMode.TIMED);
		when(config.customAnimations()).thenReturn("622, 623");
		when(config.customStart()).thenReturn("You start harpooning");
		when(config.customFailure()).thenReturn("");
		return config;
	}
}
