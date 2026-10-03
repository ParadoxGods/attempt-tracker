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
	public long twoTickCatches;
	public long twoTickFailures;
	public boolean twoTickTiming;
	public long modeledCatches;
	public long modeledFailureLower;
	public long modeledFailureUpper;
	public boolean modeledTiming;

	public FishingSession copy()
	{
		FishingSession copy = new FishingSession();
		copy.id = id; copy.startedAt = startedAt; copy.endedAt = endedAt;
		copy.catches = catches; copy.measuredCatches = measuredCatches;
		copy.minimumFailures = minimumFailures; copy.maximumFailures = maximumFailures;
		copy.loggedMillis = loggedMillis; copy.fishingMillis = fishingMillis; copy.fishingTicks = fishingTicks;
		copy.variableTiming = variableTiming;
		copy.adaptiveCatches = adaptiveCatches; copy.adaptiveFailureUpper = adaptiveFailureUpper; copy.adaptiveTiming = adaptiveTiming;
		copy.twoTickCatches = twoTickCatches; copy.twoTickFailures = twoTickFailures; copy.twoTickTiming = twoTickTiming;
		copy.modeledCatches = modeledCatches; copy.modeledFailureLower = modeledFailureLower;
		copy.modeledFailureUpper = modeledFailureUpper; copy.modeledTiming = modeledTiming;
		return copy;
	}
	public double rate(boolean upper)
	{
		// Unanchored catches remain in the total but cannot supply a denominator.
		long successes = rateCatches();
		if (!hasSupportedAttempts()) { return Double.NaN; }
		long failures = upper ? rateFailureLower() : failureUpper();
		return successes == 0 ? 0 : successes / ((double) successes + failures);
	}
	public long twoTickAttempts() { return add(twoTickCatches, twoTickFailures); }
	public long modeledAttempts(boolean upper) { return add(modeledCatches, upper ? modeledFailureUpper : modeledFailureLower); }
	public boolean hasSupportedAttempts() { return rateCatches() > 0 || rateFailureLower() > 0; }
	// Adaptive windows are retained for diagnostics but do not establish fishing attempts.
	public long rateCatches() { return modeledTiming ? modeledCatches : twoTickTiming ? twoTickCatches : add(add(measuredCatches, twoTickCatches), modeledCatches); }
	public long rateFailureLower() { return modeledTiming ? modeledFailureLower : twoTickTiming ? twoTickFailures : add(add(minimumFailures, twoTickFailures), modeledFailureLower); }
	public long failureUpper() { return modeledTiming ? modeledFailureUpper : twoTickTiming ? twoTickFailures : add(add(maximumFailures, twoTickFailures), modeledFailureUpper); }
	private static long add(long left, long right) { return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right; }
}
