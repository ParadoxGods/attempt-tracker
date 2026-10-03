package com.attempttracker.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.attempttracker.core.TwoTickFishingTracker.Result.*;

public class TwoTickFishingTrackerTest
{
	@Test public void fiftyQualifiedRollsWithFortyCatchesProduceEightyPercent()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker();
		anchor(tracker, 0);
		for (int roll = 1; roll <= 50; roll++)
		{
			assertEquals(ARMED, flinch(tracker, roll * 2 - 1));
			assertEquals(COUNTED, fish(tracker, roll * 2, roll % 5 == 0 ? 0 : 1));
		}
		assertEquals(40, tracker.getCatches()); assertEquals(10, tracker.getFailures());
		assertEquals(50, tracker.getAttempts());
		assertEquals(0.8, (double) tracker.getCatches() / tracker.getAttempts(), 0);
	}
	@Test public void anchorSuccessIsExcludedSoActivationDoesNotInflateTheRate()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0);
		assertTrue(tracker.isActive()); assertFalse(tracker.isAwaitingRoll());
		assertEquals(0, tracker.getAttempts()); flinch(tracker, 1); fish(tracker, 2, 0);
		assertEquals(0, tracker.getCatches()); assertEquals(1, tracker.getFailures());
	}
	@Test public void catchesClicksAndUnqualifiedHitsDoNotInventRolls()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker();
		assertEquals(NONE, tracker.observe(0, true, false, false, true, false, 1));
		assertFalse(tracker.isActive());
		assertEquals(NONE, tracker.observe(1, true, true, false, false, false, 0));
		assertEquals(NONE, fish(tracker, 2, 0)); assertEquals(0, tracker.getAttempts());
		anchor(tracker, 3);
		assertEquals(NONE, tracker.observe(4, true, false, false, false, false, 0));
		assertEquals(NONE, fish(tracker, 5, 0)); assertEquals(0, tracker.getAttempts());
	}
	@Test public void missingReclickAndUnclearedInteractionAreExcludedRegardlessOfOutcome()
	{
		for (int caught = 0; caught <= 1; caught++)
		{
			for (boolean accepted : new boolean[]{false, true})
			{
				TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0); flinch(tracker, 1);
				assertEquals(NONE, tracker.observe(2, true, false, accepted, !accepted, false, caught));
				assertEquals(0, tracker.getAttempts()); assertFalse(tracker.isAwaitingRoll());
			}
		}
	}
	@Test public void acceptedFishingOnFlinchTickAndExtraFlinchOnDueTickExcludeWindow()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0);
		assertEquals(NONE, tracker.observe(1, true, true, true, false, false, 0));
		assertFalse(tracker.isActive());
		anchor(tracker, 2); flinch(tracker, 3);
		assertEquals(NONE, tracker.observe(4, true, true, true, true, false, 1));
		assertEquals(0, tracker.getAttempts());
	}
	@Test public void unqualifiedFramesAndConflictsNeverCountTheCurrentWindow()
	{
		for (int caught = 0; caught <= 1; caught++)
		{
			for (boolean conflict : new boolean[]{false, true})
			{
				TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0); flinch(tracker, 1); fish(tracker, 2, 1);
				flinch(tracker, 3);
				assertEquals(NONE, tracker.observe(4, conflict, false, true, true, conflict, caught));
				assertEquals(1, tracker.getCatches()); assertEquals(0, tracker.getFailures()); assertFalse(tracker.isActive());
			}
		}
	}
	@Test public void missedTicksAndRewindsKeepCompletedTrialsButDoNotFillTheGap()
	{
		for (int discontinuous : new int[]{4, 1000000, 0})
		{
			TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0); flinch(tracker, 1); fish(tracker, 2, 0);
			assertEquals(NONE, fish(tracker, discontinuous, 0));
			assertEquals(1, tracker.getAttempts()); assertFalse(tracker.isActive());
		}
	}
	@Test public void duplicateFramesDoNotDoubleCountOrTurnAnOutcomeIntoAnotherAnchor()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0);
		assertEquals(NONE, fish(tracker, 0, 1)); assertEquals(ARMED, flinch(tracker, 1));
		assertEquals(NONE, flinch(tracker, 1)); assertEquals(COUNTED, fish(tracker, 2, 1));
		assertEquals(NONE, fish(tracker, 2, 1)); assertEquals(NONE, fish(tracker, 2, 0));
		assertEquals(1, tracker.getAttempts()); assertEquals(1, tracker.getCatches());
	}
	@Test public void contradictoryCatchRollsBackOnlyCurrentSegmentAndCanReanchor()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0); flinch(tracker, 1); fish(tracker, 2, 0);
		tracker.end(); anchor(tracker, 3); flinch(tracker, 4); fish(tracker, 5, 1); flinch(tracker, 6); fish(tracker, 7, 0);
		assertEquals(3, tracker.getAttempts());
		assertEquals(INVALIDATED, fish(tracker, 8, 1));
		assertEquals(0, tracker.getCatches()); assertEquals(1, tracker.getFailures()); assertTrue(tracker.isActive());
		flinch(tracker, 9); fish(tracker, 10, 1);
		assertEquals(1, tracker.getCatches()); assertEquals(1, tracker.getFailures());
	}
	@Test public void ambiguousOutcomesInvalidateCurrentSegmentInsteadOfClaimingMultipleRolls()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); tracker.restore(5, 7);
		anchor(tracker, 0); flinch(tracker, 1); fish(tracker, 2, 0); flinch(tracker, 3);
		assertEquals(INVALIDATED, fish(tracker, 4, 2));
		assertEquals(5, tracker.getCatches()); assertEquals(7, tracker.getFailures()); assertFalse(tracker.isActive());
		assertEquals(NONE, fish(tracker, 5, 2)); assertEquals(12, tracker.getAttempts());
	}
	@Test public void restoreAndLogoutRequireFreshAnchorAndResetClearsTheCohort()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); tracker.restore(12, 8);
		assertFalse(tracker.isActive()); assertEquals(NONE, flinch(tracker, 0)); assertEquals(NONE, fish(tracker, 1, 0));
		anchor(tracker, 2); flinch(tracker, 3); fish(tracker, 4, 1); tracker.discontinuity();
		assertEquals(NONE, flinch(tracker, 5000)); assertEquals(NONE, fish(tracker, 5001, 0));
		assertEquals(13, tracker.getCatches()); assertEquals(8, tracker.getFailures());
		tracker.reset(); assertEquals(0, tracker.getAttempts()); assertFalse(tracker.isActive());
	}
	@Test public void longRunCountsFailuresWithoutUnboundedHistory()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, 0);
		for (int roll = 1; roll <= 3000; roll++) { flinch(tracker, roll * 2 - 1); assertEquals(COUNTED, fish(tracker, roll * 2, 0)); }
		assertEquals(3000, tracker.getFailures()); assertEquals(0, tracker.getCatches());
	}
	@Test public void tickDeadlineArithmeticDoesNotWrapAtIntegerMaximum()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); anchor(tracker, Integer.MAX_VALUE - 2);
		assertEquals(ARMED, flinch(tracker, Integer.MAX_VALUE - 1)); assertEquals(COUNTED, fish(tracker, Integer.MAX_VALUE, 0));
		assertEquals(1, tracker.getFailures()); assertEquals(NONE, flinch(tracker, 0)); assertFalse(tracker.isActive());
	}
	@Test public void fullCountersDoNotOverflowAndInvalidRestoreIsRejected()
	{
		TwoTickFishingTracker tracker = new TwoTickFishingTracker(); tracker.restore(Long.MAX_VALUE - 1, 1);
		anchor(tracker, 0); flinch(tracker, 1); assertEquals(NONE, fish(tracker, 2, 1));
		assertEquals(Long.MAX_VALUE, tracker.getAttempts());
		for (long[] invalid : new long[][]{{-1, 0}, {0, -1}, {Long.MAX_VALUE, 1}})
		{
			try { tracker.restore(invalid[0], invalid[1]); fail("Expected invalid restored counts"); }
			catch (IllegalArgumentException expected) { assertEquals(Long.MAX_VALUE, tracker.getAttempts()); }
		}
	}
	private static void anchor(TwoTickFishingTracker tracker, int tick) { assertEquals(NONE, fish(tracker, tick, 1)); }
	private static TwoTickFishingTracker.Result flinch(TwoTickFishingTracker tracker, int tick) { return tracker.observe(tick, true, true, false, false, false, 0); }
	private static TwoTickFishingTracker.Result fish(TwoTickFishingTracker tracker, int tick, int caught) { return tracker.observe(tick, true, false, true, true, false, caught); }
}
