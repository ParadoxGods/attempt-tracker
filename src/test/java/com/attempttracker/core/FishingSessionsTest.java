package com.attempttracker.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingSessionsTest
{
	@Test public void focusedTwoTickSampleExcludesEarlierAdaptiveWindows()
	{
		FishingSessions book = new FishingSessions(); FishingSession session = book.current();
		session.catches = 100; session.measuredCatches = 10; session.minimumFailures = session.maximumFailures = 5;
		book.adaptiveSample(50, 100, true); book.twoTickSample(40, 10, true);
		assertEquals(100, session.catches); assertEquals(50, session.twoTickAttempts());
		assertEquals(40, session.rateCatches()); assertEquals(10, session.rateFailureLower()); assertEquals(10, session.failureUpper());
		assertEquals(0.8, session.rate(false), 0); assertEquals(0.8, session.rate(true), 0);
		book.twoTickSample(40, 10, false);
		assertEquals(100, session.rateCatches()); assertEquals(15, session.rateFailureLower()); assertEquals(115, session.failureUpper());
		assertEquals(100.0 / 215, session.rate(false), 0.000001); assertEquals(100.0 / 115, session.rate(true), 0.000001);
	}
	@Test public void twoTickRestoreAndResetKeepCohortWithoutInventingEmptyRate()
	{
		FishingSessions book = new FishingSessions(); book.current().catches = 45; book.twoTickSample(40, 10, true);
		FishingSessions resumed = new FishingSessions(); resumed.restore(book.snapshots());
		assertEquals(40, resumed.current().twoTickCatches); assertEquals(10, resumed.current().twoTickFailures);
		assertTrue(resumed.current().twoTickTiming); assertEquals(0.8, resumed.current().rate(false), 0);
		resumed.reset(); assertEquals(40, resumed.snapshots().get(1).twoTickCatches); assertEquals(10, resumed.snapshots().get(1).twoTickFailures);
		assertEquals(0, resumed.current().twoTickAttempts()); assertFalse(resumed.current().twoTickTiming);
		resumed.twoTickSample(0, 0, true); assertTrue(Double.isNaN(resumed.current().rate(false)));
		resumed.twoTickSample(0, 2, true); assertEquals(0, resumed.current().rate(false), 0);
	}
	@Test public void cohortCatchesCannotOverlapAndCorruptOrOverflowingHistoryIsRejected()
	{
		FishingSessions book = new FishingSessions(); book.current().catches = 50; book.current().measuredCatches = 5;
		book.adaptiveSample(45, 100, true); book.twoTickSample(40, 10, true); assertEquals(5, book.current().adaptiveCatches);
		book.adaptiveSample(45, 100, true); assertEquals(5, book.current().adaptiveCatches);
		FishingSession overlapping = book.current().copy(); overlapping.id = "overlap"; overlapping.adaptiveCatches = 6;
		FishingSession overflowing = book.current().copy(); overflowing.id = "overflow"; overflowing.twoTickFailures = Long.MAX_VALUE;
		FishingSession valid = book.current().copy();
		book.restore(Arrays.asList(overlapping, overflowing, valid)); assertEquals(1, book.snapshots().size()); assertEquals(valid.id, book.current().id);
		FishingSession huge = new FishingSession(); huge.twoTickCatches = Long.MAX_VALUE; huge.twoTickFailures = 1;
		assertEquals(Long.MAX_VALUE, huge.twoTickAttempts());
		huge.twoTickTiming = false; huge.minimumFailures = huge.maximumFailures = Long.MAX_VALUE; huge.adaptiveFailureUpper = Long.MAX_VALUE;
		assertEquals(Long.MAX_VALUE, huge.rateFailureLower()); assertEquals(Long.MAX_VALUE, huge.failureUpper());
	}
	@Test public void unanchoredCatchDoesNotHideRateForMeasuredSample()
	{
		FishingSession session = new FishingSession(); session.catches = 15; session.measuredCatches = 14;
		session.minimumFailures = 5; session.maximumFailures = 6;
		assertEquals(14.0 / 20, session.rate(false), 0.000001);
		assertEquals(14.0 / 19, session.rate(true), 0.000001);
		assertEquals(15, session.catches);
	}
	@Test public void unmeasuredCatchesAloneCannotInventRate()
	{
		FishingSession session = new FishingSession(); session.catches = 4;
		assertTrue(Double.isNaN(session.rate(false))); assertTrue(Double.isNaN(session.rate(true)));
		session.maximumFailures = 1;
		assertEquals(0, session.rate(false), 0); assertEquals(0, session.rate(true), 0);
	}
	@Test public void clocksPauseWithoutResettingAndRestartExcludesOfflineTime()
	{
		AtomicLong clock = new AtomicLong(); FishingSessions book = new FishingSessions(clock::get);
		String id = book.current().id; book.advance(true, false); clock.set(2_000_000_000L); book.advance(true, true);
		clock.set(5_000_000_000L); book.advance(false, false); clock.set(50_000_000_000L); book.advance(true, false);
		assertEquals(5000, book.current().loggedMillis); assertEquals(3000, book.current().fishingMillis); assertEquals(id, book.current().id);
		FishingSessions resumed = new FishingSessions(clock::get); resumed.restore(book.snapshots());
		clock.set(500_000_000_000L); resumed.advance(true, false);
		assertEquals(id, resumed.current().id); assertEquals(5000, resumed.current().loggedMillis);
	}
	@Test public void onlyResetArchivesAndZeroesSession()
	{
		FishingSessions book = new FishingSessions(); book.current().catches = book.current().measuredCatches = 12;
		String id = book.current().id; book.reset(); assertNotEquals(id, book.current().id); assertEquals(0, book.current().catches);
		assertEquals(12, book.snapshots().get(1).catches); assertTrue(book.snapshots().get(1).endedAt > 0);
	}
	@Test public void profileEvictionDoesNotEraseManualSessionCounts()
	{
		FishingSessions book = new FishingSessions(); book.sync(Arrays.asList(entry("a", 10, 2), entry("b", 20, 3)));
		book.sync(Collections.singletonList(entry("b", 21, 3)));
		assertEquals(31, book.current().catches); assertEquals(5, book.current().minimumFailures);
		book.sync(Collections.singletonList(entry("b", 21, 0)));
		assertEquals(2, book.current().minimumFailures);
	}
	@Test public void variableRunInvalidationRetainsEarlierRunAndObservedCatches()
	{
		FishingSessions book = new FishingSessions(); book.current().catches = book.current().measuredCatches = 10;
		book.current().minimumFailures = book.current().maximumFailures = 2;
		book.startVariable(0, 5, 6); for (int tick = 0; tick <= 10; tick++) { assertTrue(book.observeVariable(tick, 0)); }
		assertTrue(book.current().maximumFailures > 2); assertFalse(book.observeVariable(12, 0));
		assertEquals(10, book.current().measuredCatches); assertEquals(2, book.current().minimumFailures); assertEquals(2, book.current().maximumFailures);
	}
	private static AttemptSession entry(String id, long catches, long failures)
	{
		return new AttemptSession(id, "Fishing: Shark", "setup", TrackingMethod.TIMED, 1, 1, catches, failures, 0, 5);
	}
}
