package com.attempttracker.core;

/**
 * A serializable session value. The engine returns copies so callers cannot alter
 * live counters. Timed attempts count completed active cycles after an explicit
 * action start; catches are successes and silent cycle deadlines are failures.
 */
public final class AttemptSession
{
	private static final double WILSON_Z = 1.959963984540054;

	private String id;
	private String activity;
	private String setup;
	private TrackingMethod method;
	private long startedAt;
	private long updatedAt;
	private long successes;
	private long failures;
	private int excludedWindows;
	private int cycleTicks;

	/** For JSON deserialization. Pass deserialized values through engine.restore(). */
	public AttemptSession()
	{
	}

	/** Constructs a persisted value; restore validates it before using its counters. */
	public AttemptSession(String id, String activity, String setup, TrackingMethod method,
		long startedAt, long updatedAt, long successes, long failures,
		int excludedWindows, int cycleTicks)
	{
		this.id = id;
		this.activity = activity;
		this.setup = setup;
		this.method = method;
		this.startedAt = startedAt;
		this.updatedAt = updatedAt;
		this.successes = successes;
		this.failures = failures;
		this.excludedWindows = excludedWindows;
		this.cycleTicks = cycleTicks;
	}

	public String getId() { return id; }
	public String getActivity() { return activity; }
	public String getSetup() { return setup; }
	public TrackingMethod getMethod() { return method; }
	public long getStartedAt() { return startedAt; }
	public long getUpdatedAt() { return updatedAt; }
	public long getSuccesses() { return successes; }
	public long getFailures() { return failures; }
	public int getExcludedWindows() { return excludedWindows; }
	public int getCycleTicks() { return cycleTicks; }

	public long getAttempts()
	{
		if (method == TrackingMethod.SUCCESS_ONLY)
		{
			return 0;
		}
		// Also safe on malformed deserialized values awaiting restore.
		if (successes < 0 || failures < 0)
		{
			return 0;
		}
		return successes > Long.MAX_VALUE - failures ? Long.MAX_VALUE : successes + failures;
	}

	public double getSuccessRate()
	{
		if (method == TrackingMethod.SUCCESS_ONLY || successes < 0 || failures < 0
			|| (successes == 0 && failures == 0))
		{
			return Double.NaN;
		}
		return successes / ((double) successes + failures);
	}

	public double getConfidenceLower()
	{
		return confidenceBound(false);
	}

	public double getConfidenceUpper()
	{
		return confidenceBound(true);
	}

	/** A two-sided 95% Wilson interval for the observed trial proportion. */
	private double confidenceBound(boolean upper)
	{
		double rate = getSuccessRate();
		if (Double.isNaN(rate))
		{
			return Double.NaN;
		}
		double n = (double) successes + failures;
		double zSquared = WILSON_Z * WILSON_Z;
		double denominator = 1.0 + zSquared / n;
		double center = (rate + zSquared / (2.0 * n)) / denominator;
		double margin = WILSON_Z * Math.sqrt(rate * (1.0 - rate) / n
			+ zSquared / (4.0 * n * n)) / denominator;
		return Math.max(0.0, Math.min(1.0, upper ? center + margin : center - margin));
	}

	public AttemptSession copy()
	{
		return new AttemptSession(id, activity, setup, method, startedAt, updatedAt,
			successes, failures, excludedWindows, cycleTicks);
	}

	boolean addCounts(long addedSuccesses, long addedFailures, long now)
	{
		if (addedSuccesses < 0 || addedFailures < 0 || successes < 0 || failures < 0
			|| successes > Long.MAX_VALUE - failures)
		{
			return false;
		}
		long remaining = Long.MAX_VALUE - successes - failures;
		if (addedSuccesses > remaining || addedFailures > remaining - addedSuccesses)
		{
			return false;
		}
		successes += addedSuccesses;
		failures += addedFailures;
		touch(now);
		return true;
	}

	/** Removes only bounded contributions from an invalidated timing run. */
	boolean removeCounts(long removedSuccesses, long removedFailures, long now)
	{
		if (removedSuccesses < 0 || removedFailures < 0
			|| removedSuccesses > successes || removedFailures > failures)
		{
			return false;
		}
		successes -= removedSuccesses;
		failures -= removedFailures;
		touch(now);
		return true;
	}

	void excludeWindow(long now)
	{
		if (excludedWindows < Integer.MAX_VALUE)
		{
			excludedWindows++;
		}
		touch(now);
	}

	void touch(long now)
	{
		updatedAt = Math.max(updatedAt, Math.max(startedAt, now));
	}
}
