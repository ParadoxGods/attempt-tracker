package com.attempttracker.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class VariableRollTrackerTest
{
	@Test public void boundsAgreeWithExhaustiveSchedules()
	{
		for (int pattern = 0; pattern < 32; pattern++)
		{
			boolean[] catches = new boolean[36]; int due = 4;
			for (int roll = 0; due < catches.length; roll++)
			{
				catches[due] = (pattern & (1 << (roll % 5))) != 0; due += 5 + (roll % 2);
			}
			VariableRollTracker tracker = new VariableRollTracker(); tracker.start(0, 5, 6, 4, 5);
			List<long[]> schedules = new ArrayList<>(); schedules.add(new long[]{4, 0}); schedules.add(new long[]{5, 0});
			for (int tick = 0; tick < catches.length; tick++)
			{
				List<long[]> next = new ArrayList<>();
				for (long[] path : schedules)
				{
					if (path[0] > tick && !catches[tick]) { next.add(path); }
					else if (path[0] == tick)
					{
						for (int interval = 5; interval <= 6; interval++) { next.add(new long[]{tick + interval, path[1] + (catches[tick] ? 0 : 1)}); }
					}
				}
				schedules = next; assertTrue(tracker.observe(tick, catches[tick] ? 1 : 0));
				assertEquals(schedules.stream().mapToLong(path -> path[1]).min().getAsLong(), tracker.getMinimumFailures());
				assertEquals(schedules.stream().mapToLong(path -> path[1]).max().getAsLong(), tracker.getMaximumFailures());
			}
		}
	}
	@Test public void missedTicksAndImpossibleCatchesInvalidateInsteadOfInventingRolls()
	{
		VariableRollTracker tracker = new VariableRollTracker(); tracker.start(0, 5, 6, 4, 5);
		assertTrue(tracker.observe(0, 0)); assertFalse(tracker.observe(2, 0)); assertFalse(tracker.observe(1, 1));
		assertEquals(0, tracker.getSuccesses()); assertEquals(0, tracker.getMaximumFailures());
	}
	@Test public void longIdleFailureRunHasBoundedStateAndBothPossibleTotals()
	{
		VariableRollTracker tracker = new VariableRollTracker(); tracker.start(0, 5, 6, 4, 5);
		for (int tick = 0; tick <= 6000; tick++) { assertTrue(tracker.observe(tick, 0)); }
		assertEquals(1000, tracker.getMinimumFailures()); assertEquals(1200, tracker.getMaximumFailures());
	}
}
