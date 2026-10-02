package com.attempttracker.activity;

import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivityStartsTest
{
	@Test
	public void supportedExactMessagesIdentifyTheStartedSkill()
	{
		assertStart("You start harpooning fish.", Skill.FISHING);
		assertStart("You start harpooning fish!", Skill.FISHING);
		assertStart("You  start harpooning fish", Skill.FISHING);
		assertStart("You start harpooning fish...", Skill.FISHING);
		assertStart("You cast out your line.", Skill.FISHING);
		assertStart("You cast out your line!", Skill.FISHING);
		assertStart("You swing your pick at the rock.", Skill.MINING);
		assertStart("You swing your pick at the rock!", Skill.MINING);
		assertStart("You swing your axe at the tree.", Skill.WOODCUTTING);
		assertStart("You swing your axe at the tree!", Skill.WOODCUTTING);
	}

	@Test
	public void formattingCasingAndWhitespaceDoNotChangeTheStart()
	{
		assertStart("  <col=00ff00>YOU START</col>\u00a0HARPOONING  fish.  ", Skill.FISHING);
		assertStart("@mes_hl_gre@You cast out your line!</col>", Skill.FISHING);
	}

	@Test
	public void harpoonStartsAreDistinguishedFromOtherFishingMethods()
	{
		assertTrue(ActivityStarts.isHarpooning("You start harpooning fish."));
		assertTrue(ActivityStarts.isHarpooning("<col=00ff00>You start harpooning fish!</col>"));
		assertFalse(ActivityStarts.isHarpooning("You cast out your line."));
		assertFalse(ActivityStarts.isHarpooning("You catch a shark!"));
		assertFalse(ActivityStarts.isHarpooning(null));
	}

	@Test
	public void unknownSpoofedAndDuplicatedMessagesCannotStartASchedule()
	{
		String[] messages = {
			null, "", " ", "You start fishing.",
			"Friend: You start harpooning fish.", "You start harpooning fish. Good luck!",
			"You start harpooning fish! You start harpooning fish!", "You start harpooning fish!!",
			"You catch a shark!", "You successfully cook a shark.",
			"You swing your axe at the rock.", "You swing your pick at the tree.",
			"You cast out your line. You catch a trout.", "You swing your pick at the rock! Extra text"
		};
		for (String message : messages)
		{
			assertFalse("Should ignore: " + message, ActivityStarts.parseStart(message).isPresent());
		}
	}

	private static void assertStart(String message, Skill skill)
	{
		assertEquals("Expected start: " + message, java.util.Optional.of(skill), ActivityStarts.parseStart(message));
	}
}
