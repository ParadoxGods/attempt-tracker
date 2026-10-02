package com.attempttracker.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.junit.Assert.*;
import static com.attempttracker.core.AttemptTrackerEngine.TimedResult.*;

public class AttemptTrackerEngineTest
{
	private static final String ACTIVITY = "Shark fishing";
	private static final String SETUP = "Crystal harpoon; 3 lures";

	@Test
	public void explicitStartCreatesSessionWithoutEnteringDenominator()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(100, ACTIVITY, SETUP, 5);
		AttemptSession session = engine.getCurrentSession();
		assertEquals(TrackingMethod.TIMED, session.getMethod());
		assertEquals(5, session.getCycleTicks());
		assertEquals(0L, session.getAttempts());
		assertTrue(Double.isNaN(session.getSuccessRate()));
		assertTrue(Double.isNaN(session.getConfidenceLower()));
		assertTrue(Double.isNaN(session.getConfidenceUpper()));
		assertTrue(engine.isTimingActive());
		assertEquals(WAITING, engine.observeTimed(100, ACTIVITY, SETUP, 5, true, 0));
	}

	@Test
	public void completedCyclesCountFirstSuccessAndSilentFailures()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(100, ACTIVITY, SETUP, 5);
		ticks(engine, 100, 120, 5, 105, 115);
		assertCounts(engine.getCurrentSession(), 2, 2, 0);
		assertEquals(0.5, engine.getCurrentSession().getSuccessRate(), 0.0);
	}

	@Test
	public void separateFirstDelayCountsAtFourThenEveryFiveTicks()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5, 4);
		ticks(engine, 0, 3, 5);
		assertCounts(engine.getCurrentSession(), 0, 0, 0);
		assertEquals(COUNTED, engine.observeTimed(4, ACTIVITY, SETUP, 5, true, 1));
		ticks(engine, 5, 8, 5);
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
		assertEquals(COUNTED, engine.observeTimed(9, ACTIVITY, SETUP, 5, true, 0));
		ticks(engine, 10, 14, 5, 14);
		assertCounts(engine.getCurrentSession(), 2, 1, 0);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 2, 1, 0);
	}

	@Test
	public void separateFirstDelayCountsSilentFailuresWithoutAnySuccess()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5, 4);
		ticks(engine, 0, 14, 5);
		assertCounts(engine.getCurrentSession(), 0, 3, 0);
		ticks(engine, 15, 18, 5);
		assertCounts(engine.getCurrentSession(), 0, 3, 0);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 0, 3, 1);
	}

	@Test
	public void separateFirstDelayPreservesActualStartForPartialExclusions()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5, 4);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 0, 0, 0);
		engine.startTimed(10, ACTIVITY, SETUP, 5, 4);
		ticks(engine, 10, 11, 5);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
	}

	@Test
	public void catchAtDefaultDelayIsOffPhaseWhenFirstDelayIsFour()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5, 4);
		ticks(engine, 0, 4, 5);
		assertCounts(engine.getCurrentSession(), 0, 1, 0);
		assertEquals(INVALIDATED, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
	}

	@Test
	public void firstDelayMayBeLongerThanSubsequentCycles()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 2, 6);
		ticks(engine, 0, 5, 2);
		assertCounts(engine.getCurrentSession(), 0, 0, 0);
		ticks(engine, 6, 10, 2, 6, 10);
		assertCounts(engine.getCurrentSession(), 2, 1, 0);
	}

	@Test
	public void unsuccessfulAndTrailingCyclesCountWithoutClosingCatch()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 20, 5);
		assertCounts(engine.getCurrentSession(), 0, 4, 0);
		ticks(engine, 21, 30, 5, 25);
		assertCounts(engine.getCurrentSession(), 1, 5, 0);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 1, 5, 0);
		assertFalse(engine.isTimingActive());
	}

	@Test
	public void unfinishedCycleNeverCountsAsFailure()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 4, 5);
		assertCounts(engine.getCurrentSession(), 0, 0, 0);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		engine.interruptTiming();
		assertEquals(1, engine.getCurrentSession().getExcludedWindows());
	}

	@Test
	public void interruptionRequiresANewExplicitStart()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 3, 5);
		engine.interruptTiming();
		assertEquals(WAITING_FOR_START, engine.observeTimed(20, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		engine.startTimed(20, ACTIVITY, SETUP, 5);
		ticks(engine, 20, 25, 5, 25);
		assertCounts(engine.getCurrentSession(), 1, 0, 1);
	}

	@Test
	public void unanchoredObservationsDoNotInventSessionOrTiming()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		assertEquals(WAITING_FOR_START, engine.observeTimed(0, ACTIVITY, SETUP, 5, true, 1));
		assertEquals(WAITING_FOR_START, engine.observeTimed(1, null, null, 0, false, 1));
		assertFalse(engine.isTimingActive());
		assertNull(engine.getCurrentSession());
		assertTrue(engine.getSessions().isEmpty());
	}

	@Test
	public void ineligibleTickExcludesPartialCycleAndLeavesSuccessToCaller()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		engine.observeTimed(0, ACTIVITY, SETUP, 5, true, 0);
		assertEquals(INVALIDATED, engine.observeTimed(1, null, null, 0, false, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
		assertEquals(WAITING_FOR_START, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
	}

	@Test
	public void ineligibleInterruptionRetainsCompletedCyclesIncludingTrailingFailure()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 10, 5, 5);
		assertEquals(INVALIDATED, engine.observeTimed(11, null, null, 0, false, 0));
		assertCounts(engine.getCurrentSession(), 1, 1, 1);
		assertEquals(1, engine.getSessions().size());
	}

	@Test
	public void earlyFirstCatchInvalidatesWithoutInventingAnAttempt()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 2, 5);
		assertEquals(INVALIDATED, engine.observeTimed(3, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
		assertEquals(WAITING_FOR_START, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
	}

	@Test
	public void catchOnStartTickIsOffPhaseAndDoesNotBecomeAnAnchor()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(100, ACTIVITY, SETUP, 5);
		assertEquals(INVALIDATED, engine.observeTimed(100, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
	}

	@Test
	public void offDeadlineCatchStopsCountingWithoutAutomaticPhaseCorrection()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 5, 5, 5);
		assertEquals(INVALIDATED, engine.observeTimed(6, ACTIVITY, SETUP, 5, true, 1));
		ticks(engine, 7, 15, 5, 10, 15);
		assertCounts(timedSession(engine, SETUP, 5), 0, 0, 1);
		assertEquals(TrackingMethod.SUCCESS_ONLY, engine.getCurrentSession().getMethod());
		assertEquals(1L, engine.getCurrentSession().getSuccesses());
		assertEquals(0L, engine.getCurrentSession().getAttempts());
	}

	@Test
	public void skippedTickInvalidatesEvenWhenNextTickIsDeadline()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 3, 5);
		assertEquals(INVALIDATED, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
		ticks(engine, 6, 15, 5, 10, 15);
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
	}

	@Test
	public void backwardTickDoesNotOverflowOrRetainTimer()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(Integer.MAX_VALUE, ACTIVITY, SETUP, 5);
		engine.observeTimed(Integer.MAX_VALUE, ACTIVITY, SETUP, 5, true, 0);
		assertEquals(INVALIDATED,
			engine.observeTimed(Integer.MIN_VALUE, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
		engine.startTimed(Integer.MIN_VALUE, ACTIVITY, SETUP, 5);
		for (int offset = 0; offset <= 5; offset++)
		{
			engine.observeTimed(Integer.MIN_VALUE + offset, ACTIVITY, SETUP, 5, true,
				offset == 5 ? 1 : 0);
		}
		assertCounts(engine.getCurrentSession(), 1, 0, 1);
	}

	@Test
	public void duplicateGameTickDoesNotCountAnOutcomeTwice()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 4, 5);
		assertEquals(COUNTED, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
		assertEquals(WAITING, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
		ticks(engine, 6, 10, 5, 10);
		assertCounts(engine.getCurrentSession(), 2, 0, 0);
	}

	@Test
	public void multipleSuccessesInvalidateAndRequireNewStart()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 4, 5);
		assertEquals(INVALIDATED, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 2));
		ticks(engine, 6, 15, 5, 10, 15);
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertFalse(engine.isTimingActive());
	}

	@Test
	public void changingSetupOrCadenceRequiresStartAndKeepsSeparateProfiles()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 5, 5, 5);
		String firstId = engine.getCurrentSession().getId();
		assertEquals(INVALIDATED,
			engine.observeTimed(6, ACTIVITY, "Dragon harpoon", 5, true, 0));
		assertEquals(2, engine.getSessions().size());
		engine.startTimed(6, ACTIVITY, "Dragon harpoon", 5);
		for (int tick = 6; tick <= 11; tick++)
		{
			engine.observeTimed(tick, ACTIVITY, "Dragon harpoon", 5, true, tick == 11 ? 1 : 0);
		}
		assertEquals(INVALIDATED,
			engine.observeTimed(12, ACTIVITY, "Dragon harpoon", 6, true, 0));
		engine.startTimed(12, ACTIVITY, "Dragon harpoon", 6);
		for (int tick = 12; tick <= 18; tick++)
		{
			engine.observeTimed(tick, ACTIVITY, "Dragon harpoon", 6, true, tick == 18 ? 1 : 0);
		}
		List<AttemptSession> sessions = engine.getSessions();
		assertEquals(5, sessions.size());
		assertEquals(6, sessions.get(0).getCycleTicks());
		assertCounts(sessions.get(0), 1, 0, 0);
		assertCounts(timedSession(engine, "Dragon harpoon", 5), 0, 0, 1);
		assertEquals(firstId, timedSession(engine, SETUP, 5).getId());
		assertCounts(timedSession(engine, SETUP, 5), 0, 0, 1);
	}

	@Test
	public void returningToProfileAccumulatesOnlyAfterFreshStart()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 5, 5, 5);
		String id = engine.getCurrentSession().getId();
		engine.startTimed(6, "Lobster fishing", SETUP, 5);
		engine.observeTimed(6, "Lobster fishing", SETUP, 5, true, 0);
		engine.startTimed(7, ACTIVITY, SETUP, 5);
		ticks(engine, 7, 12, 5, 12);
		assertEquals(id, engine.getCurrentSession().getId());
		assertCounts(engine.getCurrentSession(), 2, 0, 1);
	}

	@Test
	public void phaseMismatchRollsBackSilentFailuresAndPreservesEarlierVisibleCatches()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 15, 5, 5, 15);
		assertCounts(engine.getCurrentSession(), 2, 1, 0);
		assertEquals(INVALIDATED, engine.observeTimed(16, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(timedSession(engine, SETUP, 5), 0, 0, 1);
		AttemptSession preserved = engine.getCurrentSession();
		assertEquals(TrackingMethod.SUCCESS_ONLY, preserved.getMethod());
		assertEquals(2L, preserved.getSuccesses());
		assertEquals(0L, preserved.getAttempts());
		assertTrue(Double.isNaN(preserved.getSuccessRate()));
		// The rejected current catch is deliberately the adapter's responsibility.
		engine.recordSuccessOnly(ACTIVITY, SETUP);
		assertEquals(3L, engine.getCurrentSession().getSuccesses());
	}

	@Test
	public void lateFirstCatchRemovesAlreadyPredictedFailure()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 5, 5);
		assertCounts(engine.getCurrentSession(), 0, 1, 0);
		assertEquals(INVALIDATED, engine.observeTimed(6, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 0, 0, 1);
		assertEquals(1, engine.getSessions().size());
	}

	@Test
	public void missedTickRollsBackOnlyLatestStartContributions()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 10, 5, 5);
		engine.interruptTiming();
		engine.startTimed(20, ACTIVITY, SETUP, 5);
		ticks(engine, 20, 30, 5, 30);
		assertCounts(engine.getCurrentSession(), 2, 2, 0);
		assertEquals(INVALIDATED, engine.observeTimed(32, ACTIVITY, SETUP, 5, true, 0));
		assertCounts(timedSession(engine, SETUP, 5), 1, 1, 1);
		assertEquals(TrackingMethod.SUCCESS_ONLY, engine.getCurrentSession().getMethod());
		assertEquals(1L, engine.getCurrentSession().getSuccesses());
	}

	@Test
	public void adapterInvalidationRollsBackLatestStartAndPreservesCatchesIdempotently()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		assertEquals(WAITING_FOR_START, engine.invalidateTiming(0));
		assertTrue(engine.getSessions().isEmpty());
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 10, 5, 5);
		engine.interruptTiming();
		engine.startTimed(20, ACTIVITY, SETUP, 5);
		ticks(engine, 20, 30, 5, 25);
		assertCounts(engine.getCurrentSession(), 2, 2, 0);
		assertEquals(INVALIDATED, engine.invalidateTiming(31));
		assertCounts(timedSession(engine, SETUP, 5), 1, 1, 1);
		assertEquals(TrackingMethod.SUCCESS_ONLY, engine.getCurrentSession().getMethod());
		assertEquals(1L, engine.getCurrentSession().getSuccesses());
		assertEquals(0L, engine.getCurrentSession().getAttempts());
		assertFalse(engine.isTimingActive());
		assertEquals(WAITING_FOR_START, engine.invalidateTiming(32));
		assertCounts(timedSession(engine, SETUP, 5), 1, 1, 1);
		assertEquals(1L, engine.getCurrentSession().getSuccesses());
		assertEquals(2, engine.getSessions().size());
	}

	@Test
	public void multipleOutcomesRollBackPreviousCyclesAndPreserveTheirCatches()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 9, 5, 5);
		assertEquals(INVALIDATED, engine.observeTimed(10, ACTIVITY, SETUP, 5, true, 2));
		assertCounts(timedSession(engine, SETUP, 5), 0, 0, 1);
		assertEquals(1L, engine.getCurrentSession().getSuccesses());
		engine.recordSuccessOnly(ACTIVITY, SETUP, 2);
		assertEquals(3L, engine.getCurrentSession().getSuccesses());
	}

	@Test
	public void explicitRestartChangesDeadlineAndKeepsCompletedCycles()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 7, 5, 5);
		engine.startTimed(8, ACTIVITY, SETUP, 5);
		ticks(engine, 8, 12, 5);
		assertCounts(engine.getCurrentSession(), 1, 0, 1);
		assertEquals(COUNTED, engine.observeTimed(13, ACTIVITY, SETUP, 5, true, 0));
		assertCounts(engine.getCurrentSession(), 1, 1, 1);
	}

	@Test
	public void immediateInterruptDoesNotExcludeAZeroLengthCycle()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 0, 0, 0);
		engine.startTimed(10, ACTIVITY, SETUP, 5);
		ticks(engine, 10, 15, 5, 15);
		engine.interruptTiming();
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
	}

	@Test
	public void startCanBeObservedOnFollowingTickWithoutArtificialOffset()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(100, ACTIVITY, SETUP, 5);
		ticks(engine, 101, 104, 5);
		assertEquals(COUNTED, engine.observeTimed(105, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
	}

	@Test
	public void observedProfilesAccumulateIndependentlyAndHaveNoTimingCadence()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.recordObserved("Cooking", "Range", true);
		String cookingId = engine.getCurrentSession().getId();
		engine.recordObserved("Pickpocketing", "Gloves", false);
		engine.recordObserved(" Cooking ", " Range ", false);
		assertEquals(cookingId, engine.getCurrentSession().getId());
		assertCounts(engine.getCurrentSession(), 1, 1, 0);
		assertEquals(TrackingMethod.OBSERVED, engine.getCurrentSession().getMethod());
		assertEquals(0, engine.getCurrentSession().getCycleTicks());
		assertEquals(2, engine.getSessions().size());
	}

	@Test
	public void successOnlyProfilesCountCatchesWithUnknownDenominator()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.recordSuccessOnly(ACTIVITY, SETUP);
		String id = engine.getCurrentSession().getId();
		engine.recordSuccessOnly(ACTIVITY, SETUP);
		AttemptSession session = engine.getCurrentSession();
		assertEquals(id, session.getId());
		assertEquals(TrackingMethod.SUCCESS_ONLY, session.getMethod());
		assertEquals(2L, session.getSuccesses());
		assertEquals(0L, session.getFailures());
		assertEquals(0L, session.getAttempts());
		assertEquals(0, session.getCycleTicks());
		assertTrue(Double.isNaN(session.getSuccessRate()));
		assertTrue(Double.isNaN(session.getConfidenceLower()));
		assertTrue(Double.isNaN(session.getConfidenceUpper()));
		engine.recordObserved(ACTIVITY, SETUP, true);
		assertEquals(2, engine.getSessions().size());
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
		assertNotEquals(id, engine.getCurrentSession().getId());
	}

	@Test
	public void successOnlyRestoreRejectsFailuresAndPreservesUnknownAttempts()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine(() -> 1_000L);
		AttemptSession valid = new AttemptSession("successes", ACTIVITY, SETUP,
			TrackingMethod.SUCCESS_ONLY, 100, 200, 35, 0, 0, 0);
		AttemptSession invalid = new AttemptSession("failures", ACTIVITY, SETUP,
			TrackingMethod.SUCCESS_ONLY, 100, 300, 35, 1, 0, 0);
		engine.restore(Arrays.asList(valid, invalid));
		assertEquals(1, engine.getSessions().size());
		AttemptSession restored = engine.getSessions().get(0);
		assertEquals(35L, restored.getSuccesses());
		assertEquals(0L, restored.getAttempts());
		assertTrue(Double.isNaN(restored.getSuccessRate()));
		assertTrue(Double.isNaN(restored.getConfidenceLower()));
		assertTrue(Double.isNaN(restored.getConfidenceUpper()));
	}

	@Test
	public void successOnlyCountersDoNotOverflowDespiteUnknownAttemptCount()
	{
		AttemptSession session = new AttemptSession("successes", ACTIVITY, SETUP,
			TrackingMethod.SUCCESS_ONLY, 100, 200, Long.MAX_VALUE - 1, 0, 0, 0);
		assertTrue(session.addCounts(1, 0, 300));
		assertFalse(session.addCounts(1, 0, 400));
		assertEquals(Long.MAX_VALUE, session.getSuccesses());
		assertEquals(0L, session.getAttempts());
	}

	@Test
	public void successOnlyBatchesAccumulateAndZeroBatchCreatesNothing()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.recordSuccessOnly(ACTIVITY, SETUP, 0);
		assertTrue(engine.getSessions().isEmpty());
		engine.recordSuccessOnly(ACTIVITY, SETUP, 3);
		engine.recordSuccessOnly(ACTIVITY, SETUP, 2);
		assertEquals(5L, engine.getCurrentSession().getSuccesses());
		assertEquals(0L, engine.getCurrentSession().getAttempts());
	}

	@Test
	public void newSessionRetainsHistoryAndClearRemovesIt()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.recordObserved("Cooking", "Range", true);
		String id = engine.getCurrentSession().getId();
		engine.startNewSession();
		assertNull(engine.getCurrentSession());
		engine.recordObserved("Cooking", "Range", false);
		assertNotEquals(id, engine.getCurrentSession().getId());
		assertCounts(engine.getCurrentSession(), 0, 1, 0);
		assertEquals(2, engine.getSessions().size());
		engine.clear();
		assertTrue(engine.getSessions().isEmpty());
		assertNull(engine.getCurrentSession());
	}

	@Test
	public void newTimedSessionExcludesOldOpenBoundary()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 7, 5, 5);
		engine.startNewSession();
		assertFalse(engine.isTimingActive());
		engine.startTimed(8, ACTIVITY, SETUP, 5);
		ticks(engine, 8, 13, 5, 13);
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
		assertCounts(engine.getSessions().get(1), 1, 0, 1);
	}

	@Test
	public void returnedSnapshotsCannotChangeLiveCounters()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.recordObserved("Cooking", "Range", true);
		AttemptSession snapshot = engine.getCurrentSession();
		snapshot.addCounts(20, 20, Long.MAX_VALUE);
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
		List<AttemptSession> history = engine.getSessions();
		try
		{
			history.clear();
			fail("history must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertEquals(1, history.size());
		}
		engine.recordObserved("Cooking", "Range", false);
		assertCounts(history.get(0), 1, 0, 0);
	}

	@Test
	public void historyIsBoundedAt200LatestSessions()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		for (int i = 0; i < 220; i++)
		{
			engine.recordObserved("Cooking " + i, "Range", true);
		}
		assertEquals(200, engine.getSessions().size());
		assertEquals("Cooking 219", engine.getSessions().get(0).getActivity());
		assertEquals("Cooking 20", engine.getSessions().get(199).getActivity());
	}

	@Test
	public void evictedTimedSessionCannotResurrectBeyondHistoryLimit()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		for (int i = 0; i < 200; i++)
		{
			engine.recordObserved("Cooking " + i, "Range", true);
		}
		engine.interruptTiming();
		assertFalse(engine.isTimingActive());
		assertEquals(200, engine.getSessions().size());
		assertEquals("Cooking 199", engine.getCurrentSession().getActivity());
	}

	@Test
	public void restoreSortsDeduplicatesSanitizesAndStartsFreshGrouping()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine(() -> 1_000L);
		AttemptSession older = fixture("same", 100, 200, 2, 1);
		AttemptSession newer = fixture("same", 100, 300, 3, 1);
		AttemptSession cleaned = new AttemptSession(null, " Cooking ", null,
			TrackingMethod.OBSERVED, -5, Long.MAX_VALUE, 4, 2, -1, -1);
		engine.restore(Arrays.asList(older, newer, cleaned));
		assertNull(engine.getCurrentSession());
		List<AttemptSession> history = engine.getSessions();
		assertEquals(2, history.size());
		AttemptSession first = history.get(0);
		assertNotNull(first.getId());
		assertFalse(first.getId().isEmpty());
		assertEquals("Cooking", first.getActivity());
		assertEquals("", first.getSetup());
		assertEquals(0, first.getCycleTicks());
		assertEquals(0L, first.getStartedAt());
		assertEquals(1_000L, first.getUpdatedAt());
		assertEquals(0, first.getExcludedWindows());
		assertEquals(3L, history.get(1).getSuccesses());
		engine.recordObserved("Cooking", "Range", true);
		assertEquals(3, engine.getSessions().size());
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
	}

	@Test
	public void malformedRestoredSessionsAreSkippedWithoutBreakingValidHistory()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine(() -> 1_000L);
		AttemptSession negative = fixture("negative", 100, 200, -1, 0);
		AttemptSession overflow = fixture("overflow", 100, 200, Long.MAX_VALUE, 1);
		AttemptSession noActivity = new AttemptSession("blank", " ", "", TrackingMethod.OBSERVED,
			100, 200, 1, 0, 0, 0);
		AttemptSession noMethod = new AttemptSession("method", "Cooking", "", null,
			100, 200, 1, 0, 0, 0);
		AttemptSession noCadence = new AttemptSession("cadence", ACTIVITY, SETUP, TrackingMethod.TIMED,
			100, 200, 1, 0, 0, 0);
		engine.restore(Arrays.asList(null, new AttemptSession(), negative, overflow, noActivity,
			noMethod, noCadence, fixture("valid", 100, 200, 4, 1)));
		assertEquals(1, engine.getSessions().size());
		assertCounts(engine.getSessions().get(0), 4, 1, 0);
		engine.restore(null);
		assertTrue(engine.getSessions().isEmpty());
	}

	@Test
	public void restoreCopiesInputsAndCapsHistory()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine(() -> 1_000L);
		List<AttemptSession> restored = new ArrayList<>();
		for (int i = 0; i < 220; i++)
		{
			restored.add(fixture("id" + i, 0, i, 1, 0));
		}
		engine.restore(restored);
		restored.get(219).addCounts(10, 10, 500);
		assertEquals(200, engine.getSessions().size());
		assertEquals("id219", engine.getSessions().get(0).getId());
		assertCounts(engine.getSessions().get(0), 1, 0, 0);
	}

	@Test
	public void wilsonIntervalMatchesKnown95PercentExampleAndEndpoints()
	{
		AttemptSession session = fixture("example", 1, 1, 95, 5);
		assertEquals(0.95, session.getSuccessRate(), 1e-12);
		assertEquals(0.8882495307680817, session.getConfidenceLower(), 1e-12);
		assertEquals(0.978456320845632, session.getConfidenceUpper(), 1e-12);
		AttemptSession zeroSuccesses = fixture("zero", 1, 1, 0, 10);
		assertEquals(0.0, zeroSuccesses.getConfidenceLower(), 1e-12);
		assertEquals(0.2775327998628892, zeroSuccesses.getConfidenceUpper(), 1e-12);
		AttemptSession allSuccesses = fixture("all", 1, 1, 10, 0);
		assertEquals(0.7224672001371107, allSuccesses.getConfidenceLower(), 1e-12);
		assertEquals(1.0, allSuccesses.getConfidenceUpper(), 1e-12);
	}

	@Test
	public void countersRejectOverflowAndKeepConfidenceFiniteAtLargeSamples()
	{
		AttemptSession session = fixture("large", 1, 1, Long.MAX_VALUE - 1, 0);
		assertTrue(session.addCounts(0, 1, 2));
		assertFalse(session.addCounts(1, 0, 3));
		assertEquals(Long.MAX_VALUE, session.getAttempts());
		assertEquals(1L, session.getFailures());
		assertTrue(Double.isFinite(session.getConfidenceLower()));
		assertTrue(Double.isFinite(session.getConfidenceUpper()));
		session = new AttemptSession("excluded", ACTIVITY, SETUP, TrackingMethod.TIMED,
			1, 1, 0, 0, Integer.MAX_VALUE, 4);
		session.excludeWindow(2);
		assertEquals(Integer.MAX_VALUE, session.getExcludedWindows());
	}

	@Test
	public void removalRejectsNegativeOrUnboundedCountsWithoutMutating()
	{
		AttemptSession session = fixture("bounded", 1, 2, 3, 2);
		assertFalse(session.removeCounts(-1, 0, 3));
		assertFalse(session.removeCounts(0, -1, 3));
		assertFalse(session.removeCounts(4, 0, 3));
		assertFalse(session.removeCounts(0, 3, 3));
		assertCounts(session, 3, 2, 0);
		assertEquals(2L, session.getUpdatedAt());
		assertTrue(session.removeCounts(2, 1, 3));
		assertCounts(session, 1, 1, 0);
		assertEquals(3L, session.getUpdatedAt());
		assertFalse(session.addCounts(-1, 0, 4));
		assertFalse(session.addCounts(0, -1, 4));
		assertCounts(session, 1, 1, 0);
	}

	@Test
	public void sessionTimestampsDoNotMoveBackWhenClockMovesBack()
	{
		AtomicLong clock = new AtomicLong(1_000L);
		AttemptTrackerEngine engine = new AttemptTrackerEngine(clock::get);
		engine.recordObserved("Cooking", null, true);
		clock.set(900L);
		engine.recordObserved("Cooking", null, false);
		assertEquals(1_000L, engine.getCurrentSession().getStartedAt());
		assertEquals(1_000L, engine.getCurrentSession().getUpdatedAt());
	}

	@Test
	public void invalidInputsFailClearly()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		assertInvalid(() -> engine.recordObserved(" ", "", true));
		assertInvalid(() -> engine.recordSuccessOnly(ACTIVITY, SETUP, -1));
		assertInvalid(() -> engine.startTimed(0, ACTIVITY, SETUP, 0));
		assertInvalid(() -> engine.startTimed(0, null, SETUP, 5));
		assertInvalid(() -> engine.startTimed(0, ACTIVITY, SETUP, 5, 0));
		assertInvalid(() -> engine.startTimed(0, ACTIVITY, SETUP, 5, 101));
		assertInvalid(() -> engine.observeTimed(0, ACTIVITY, SETUP, 0, true, 1));
		assertInvalid(() -> engine.observeTimed(0, ACTIVITY, SETUP, 4, true, -1));
		assertTrue(engine.getSessions().isEmpty());
	}

	@Test
	public void invalidRestartDoesNotDiscardAnActiveValidTimer()
	{
		AttemptTrackerEngine engine = new AttemptTrackerEngine();
		engine.startTimed(0, ACTIVITY, SETUP, 5);
		ticks(engine, 0, 4, 5);
		assertInvalid(() -> engine.startTimed(4, ACTIVITY, SETUP, 0));
		assertInvalid(() -> engine.startTimed(4, ACTIVITY, SETUP, 5, 101));
		assertTrue(engine.isTimingActive());
		assertEquals(COUNTED, engine.observeTimed(5, ACTIVITY, SETUP, 5, true, 1));
		assertCounts(engine.getCurrentSession(), 1, 0, 0);
	}

	private static AttemptSession timedSession(AttemptTrackerEngine engine, String setup, int cycle)
	{
		for (AttemptSession session : engine.getSessions())
		{
			if (session.getMethod() == TrackingMethod.TIMED && session.getSetup().equals(setup)
				&& session.getCycleTicks() == cycle)
			{
				return session;
			}
		}
		throw new AssertionError("timed session not found");
	}

	private static AttemptSession fixture(String id, long started, long updated,
		long successes, long failures)
	{
		return new AttemptSession(id, "Cooking", "Range", TrackingMethod.OBSERVED,
			started, updated, successes, failures, 0, 0);
	}

	private static void ticks(AttemptTrackerEngine engine, int first, int last,
		int cycle, int... successfulTicks)
	{
		for (int tick = first; tick <= last; tick++)
		{
			int successes = 0;
			for (int successfulTick : successfulTicks)
			{
				if (successfulTick == tick)
				{
					successes++;
				}
			}
			engine.observeTimed(tick, ACTIVITY, SETUP, cycle, true, successes);
		}
	}

	private static void assertCounts(AttemptSession session, long successes, long failures,
		int excludedWindows)
	{
		assertEquals(successes, session.getSuccesses());
		assertEquals(failures, session.getFailures());
		assertEquals(successes + failures, session.getAttempts());
		assertEquals(excludedWindows, session.getExcludedWindows());
	}

	private static void assertInvalid(Runnable action)
	{
		try
		{
			action.run();
			fail("expected input validation");
		}
		catch (IllegalArgumentException expected)
		{
			assertFalse(expected.getMessage().isEmpty());
		}
	}
}
