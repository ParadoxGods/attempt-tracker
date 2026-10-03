package com.attempttracker.core;

import java.util.UUID;

/** User-controlled session: logout, gear changes and new spots never reset it. */
public final class FishingSession
{
	public String id = UUID.randomUUID().toString();
	public long startedAt = System.currentTimeMillis();
	public long endedAt;
	public long catches;
	public long measuredCatches;
	public long minimumFailures;
	public long maximumFailures;
	public long loggedMillis;
	public long fishingMillis;
	public long fishingTicks;
	public boolean variableTiming;
	public long adaptiveCatches;
	public long adaptiveFailureUpper;
	public boolean adaptiveTiming;

	public FishingSession copy()
	{
		FishingSession copy = new FishingSession();
		copy.id = id; copy.startedAt = startedAt; copy.endedAt = endedAt;
		copy.catches = catches; copy.measuredCatches = measuredCatches;
		copy.minimumFailures = minimumFailures; copy.maximumFailures = maximumFailures;
		copy.loggedMillis = loggedMillis; copy.fishingMillis = fishingMillis; copy.fishingTicks = fishingTicks;
		copy.variableTiming = variableTiming;
		copy.adaptiveCatches = adaptiveCatches; copy.adaptiveFailureUpper = adaptiveFailureUpper; copy.adaptiveTiming = adaptiveTiming;
		return copy;
	}
	public double rate(boolean upper)
	{
		// Unanchored catches remain in the total but cannot supply a denominator.
		long successes = rateCatches();
		if (successes == 0 && maximumFailures == 0) { return Double.NaN; }
		long failures = upper ? minimumFailures : failureUpper();
		return successes == 0 ? 0 : successes / ((double) successes + failures);
	}
	public long rateCatches() { return measuredCatches + adaptiveCatches; }
	public long failureUpper() { return adaptiveFailureUpper > Long.MAX_VALUE - maximumFailures ? Long.MAX_VALUE : maximumFailures + adaptiveFailureUpper; }
}
