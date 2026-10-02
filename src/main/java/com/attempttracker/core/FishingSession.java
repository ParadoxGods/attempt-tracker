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

	public FishingSession copy()
	{
		FishingSession copy = new FishingSession();
		copy.id = id; copy.startedAt = startedAt; copy.endedAt = endedAt;
		copy.catches = catches; copy.measuredCatches = measuredCatches;
		copy.minimumFailures = minimumFailures; copy.maximumFailures = maximumFailures;
		copy.loggedMillis = loggedMillis; copy.fishingMillis = fishingMillis; copy.fishingTicks = fishingTicks;
		copy.variableTiming = variableTiming;
		return copy;
	}
	public double rate(boolean upper)
	{
		// Unanchored catches remain in the total but cannot supply a denominator.
		if (measuredCatches + maximumFailures == 0) { return Double.NaN; }
		long failures = upper ? minimumFailures : maximumFailures;
		return measuredCatches == 0 ? 0 : measuredCatches / ((double) measuredCatches + failures);
	}
}
