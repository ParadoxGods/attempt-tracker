package com.attempttracker.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FishingFrameTraceTest
{
	@Test
	public void PreservesTransientChangesHitsAndItemPairCallbackOrder()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		trace.record(1000, "animation", 618, 2);
		trace.record(1001, "interaction", 0);
		trace.record(1001, "hit", 16, 0);
		trace.record(1001, "interaction", 2);
		trace.record(1002, "item_pair", 946, 6333);
		trace.record(1003, "animation", 1248, 0);
		assertEquals("0@1000:animation:618:2|1@1001:interaction:0|2@1001:hit:16:0|3@1001:interaction:2"
			+ "|4@1002:item_pair:946:6333|5@1003:animation:1248:0", trace.events());
		assertFalse(trace.truncated());
	}

	@Test
	public void ClearsEveryFrameIncludingOverflowFlagAndSequenceIndex()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		assertEquals("", trace.events()); assertFalse(trace.truncated());
		for (int i = 0; i < 65; i++) { trace.record(i, "hit", 16, 0); }
		assertTrue(trace.truncated());
		trace.clear(); assertEquals("", trace.events()); assertFalse(trace.truncated());
		trace.record(123, "outcome", 1); assertEquals("0@123:outcome:1", trace.events());
	}

	@Test
	public void EventLimitIs64AndFurtherCallbacksCannotGrowTheBuffer()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		for (int i = 0; i < 64; i++) { trace.record(i, "hit", 0); }
		assertFalse(trace.truncated());
		String retained = trace.events(); assertEquals(64, retained.split("\\|").length);
		for (int i = 0; i < 10000; i++) { trace.record(i, "hit", Integer.MAX_VALUE); }
		assertTrue(trace.truncated()); assertEquals(retained, trace.events());
	}

	@Test
	public void CharacterLimitStopsBefore4096AndRetainsOnlyCompleteTokens()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		int[] largeValues = new int[16]; java.util.Arrays.fill(largeValues, Integer.MIN_VALUE);
		for (int i = 0; i < 64; i++) { trace.record(Integer.MAX_VALUE, "timer-state-with-extra-guards", largeValues); }
		assertTrue(trace.truncated()); assertTrue(trace.events().length() <= 4096);
		String retained = trace.events();
		assertTrue(retained.endsWith(":" + Integer.MIN_VALUE));
		assertTrue(retained.split("\\|").length < 64);
		trace.record(10000, "hit", 0); assertEquals(retained, trace.events());
	}

	@Test
	public void CategoriesRejectArbitraryChatWhitespaceQuotesCsvAndUnicode()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		for (String category : new String[]{null, "", "You catch a shark!", "Player Name", "chat\ntext", "quoted\"text",
			"csv,text", "x:y", "x|y", "emoji🐟", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO"})
		{
			try { trace.record(0, category, 1); fail("Accepted unsafe category " + category); }
			catch (IllegalArgumentException expected) { assertEquals("", trace.events()); }
		}
		trace.record(0, "start-FISHING_2", -1, 2);
		assertEquals("0@0:start-FISHING_2:-1:2", trace.events());
	}

	@Test
	public void ExcessiveOrMissingNumericPayloadIsExplicitlyTruncated()
	{
		FishingFrameTrace trace = new FishingFrameTrace();
		trace.record(0, "item_pair", new int[17]); assertTrue(trace.truncated()); assertEquals("", trace.events());
		trace.clear(); trace.record(0, "item_pair", (int[]) null); assertTrue(trace.truncated()); assertEquals("", trace.events());
		trace.clear(); trace.record(0, "start"); assertEquals("0@0:start", trace.events()); assertFalse(trace.truncated());
	}
}
