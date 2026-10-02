package com.attempttracker.core;

import java.util.HashMap;
import java.util.Map;

/** All schedules consistent with variable roll intervals and observed catches. */
public final class VariableRollTracker
{
	private Map<Long, long[]> schedules = new HashMap<>();
	private int shortest;
	private int longest;
	private int lastTick;
	private boolean observed;
	private long successes;
	private long minimum;
	private long maximum;

	public void start(int tick, int shortest, int longest, int firstShortest, int firstLongest)
	{
		if (shortest < 1 || longest < shortest || longest > 100 || firstShortest < 1 || firstLongest < firstShortest || firstLongest > 100)
		{
			throw new IllegalArgumentException("Invalid roll intervals");
		}
		this.shortest = shortest; this.longest = longest; lastTick = tick; observed = false;
		successes = minimum = maximum = 0; schedules.clear();
		for (int delay = firstShortest; delay <= firstLongest; delay++) { schedules.put((long) tick + delay, new long[]{0, 0}); }
	}

	/** False rejects the current run; never manufactures a precise random interval. */
	public boolean observe(int tick, int catches)
	{
		if (schedules.isEmpty() || catches < 0 || catches > 1 || (observed ? (long) tick - lastTick != 1 : tick != lastTick)) { return false; }
		Map<Long, long[]> next = new HashMap<>();
		for (Map.Entry<Long, long[]> entry : schedules.entrySet())
		{
			long due = entry.getKey(); long[] failures = entry.getValue();
			if (due > tick)
			{
				if (catches == 0) { merge(next, due, failures[0], failures[1]); }
			}
			else if (due == tick)
			{
				long failed = catches == 0 ? 1 : 0;
				for (int delay = shortest; delay <= longest; delay++) { merge(next, (long) tick + delay, failures[0] + failed, failures[1] + failed); }
			}
		}
		if (next.isEmpty()) { return false; }
		schedules = next; lastTick = tick; observed = true; successes += catches;
		minimum = Long.MAX_VALUE; maximum = 0;
		for (long[] failures : schedules.values()) { minimum = Math.min(minimum, failures[0]); maximum = Math.max(maximum, failures[1]); }
		return true;
	}

	private static void merge(Map<Long, long[]> schedules, long due, long min, long max)
	{
		long[] existing = schedules.get(due);
		if (existing == null) { schedules.put(due, new long[]{min, max}); }
		else { existing[0] = Math.min(existing[0], min); existing[1] = Math.max(existing[1], max); }
	}
	public long getMinimumFailures() { return minimum; }
	public long getMaximumFailures() { return maximum; }
	public long getSuccesses() { return successes; }
}
