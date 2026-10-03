package com.attempttracker.core;

/**
 * Bounded fallback for fishing whose server roll schedule is not observable.
 * A potentially active tick permits zero or one roll; a catch fixes one outcome.
 * Input rhythm is never treated as proof that a roll occurred.
 * Strict segments are shadowed so invalidated timing can be recovered once.
 */
public final class AdaptiveFishingSample
{
	private long catches, failuresUpper, shadowCatches, shadowWindows;
	private boolean strict, used;
	private int lastTick = -1;
	private boolean previousFishing;

	public void restore(long catches, long failuresUpper, boolean used)
	{
		if (catches < 0 || failuresUpper < 0) { throw new IllegalArgumentException("Negative sample"); }
		this.catches = catches; this.failuresUpper = failuresUpper; this.used = used;
		shadowCatches = shadowWindows = 0; strict = false; lastTick = -1; previousFishing = false;
	}

	public void observe(int tick, boolean potentiallyFishing, int observedCatches,
		boolean strictActive, boolean strictStarted, boolean strictInvalidated, int newlyStrictCatches)
	{
		if (tick < 0 || observedCatches < 0 || newlyStrictCatches < 0) { throw new IllegalArgumentException("Invalid observation"); }
		if (tick == lastTick) { return; }
		if (lastTick >= 0 && tick < lastTick) { end(); lastTick = -1; }
		if (strictInvalidated && strict) { recoverShadow(); }
		if (strictStarted || (strict && !strictActive)) { end(); }
		if (strictActive && !strictInvalidated) { strict = true; }
		long windows = potentiallyFishing ? previousFishing && lastTick >= 0 ? Math.max(1L, (long) tick - lastTick) : 1 : 0;
		lastTick = tick;
		previousFishing = potentiallyFishing;
		// Multiple catch messages can represent ambiguous/batched outcomes. Exclude
		// that tick rather than equating item or message quantity with roll count.
		if (observedCatches > 1) { if (strict) { end(); } return; }
		if (strict)
		{
			shadowWindows = add(shadowWindows, windows); shadowCatches = add(shadowCatches, observedCatches);
		}
		else if (potentiallyFishing)
		{
			int unmeasured = Math.max(0, observedCatches - newlyStrictCatches);
			catches = add(catches, unmeasured);
			failuresUpper = add(failuresUpper, Math.max(0, windows - observedCatches));
			used = true;
		}
	}

	private void recoverShadow()
	{
		catches = add(catches, shadowCatches); failuresUpper = add(failuresUpper, Math.max(0, shadowWindows - shadowCatches));
		used = true; end();
	}
	/** A deliberate interruption keeps strict completed trials in their own model. */
	public void end() { strict = false; shadowCatches = shadowWindows = 0; }
	/** Offline/unobserved time cannot become possible attempts on the next tick. */
	public void discontinuity() { end(); lastTick = -1; previousFishing = false; }
	public long getCatches() { return catches; }
	public long getFailuresUpper() { return failuresUpper; }
	public boolean isUsed() { return used; }
	private static long add(long total, long extra) { return extra > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + extra; }
}
