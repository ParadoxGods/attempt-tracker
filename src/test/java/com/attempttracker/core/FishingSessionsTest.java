package com.attempttracker.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingSessionsTest
{
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
