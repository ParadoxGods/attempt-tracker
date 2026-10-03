package com.attempttracker.core;

/**
 * Reconstructs attack-assisted two-tick rolls from qualified, contiguous frames.
 * The adapter must establish the flinch preconditions; a hitsplat or click alone
 * is not sufficient. A caught fish establishes timer zero, but that anchoring
 * catch is excluded from this measured cohort. Subsequent rolls require a
 * qualified flinch on the next tick and accepted fishing one tick later.
 */
public final class TwoTickFishingTracker
{
	public enum Result { NONE, ARMED, COUNTED, INVALIDATED }

	private long catches, failures, segmentCatches, segmentFailures;
	private int lastTick = -1;
	private long anchorTick = -1, dueTick = -1;

	/** Restored totals never resume an old server timer or segment. */
	public void restore(long catches, long failures)
	{
		if (catches < 0 || failures < 0 || failures > Long.MAX_VALUE - catches)
		{
			throw new IllegalArgumentException("Invalid two-tick sample");
		}
		this.catches = catches; this.failures = failures;
		discontinuity();
	}

	/**
	 * Observe one resolved server frame. Eligibility/conflicts must be decided
	 * independently of the catch outcome. acceptedFishing means a server-observed
	 * harpooning start or fresh animation paired with a fishing interaction,
	 * not a local menu click. idleAfter is the observed cleared actor target;
	 * it is not a direct server interaction/timer accessor. qualifiedFlinch includes
	 * the previous idle state, combat hit/retarget, auto-retaliate, weapon/style,
	 * stationary position and absence of competing actions.
	 */
	public Result observe(int tick, boolean eligible, boolean qualifiedFlinch,
		boolean acceptedFishing, boolean idleAfter, boolean conflict, int caught)
	{
		if (tick < 0 || caught < 0) { throw new IllegalArgumentException("Invalid observation"); }
		if (tick == lastTick) { return Result.NONE; }
		boolean contiguous = lastTick < 0 || (long) tick == (long) lastTick + 1;
		lastTick = tick;
		if (!contiguous) { end(); }
		if (!eligible || conflict)
		{
			end(); return Result.NONE;
		}
		if (caught > 1)
		{
			boolean wasActive = isActive(); invalidate();
			return wasActive ? Result.INVALIDATED : Result.NONE;
		}
		if (isAwaitingRoll())
		{
			if (tick == dueTick && acceptedFishing && idleAfter && !qualifiedFlinch)
			{
				if (getAttempts() == Long.MAX_VALUE) { end(); return Result.NONE; }
				if (caught == 1) { catches++; segmentCatches++; }
				else { failures++; segmentFailures++; }
				anchorTick = tick; dueTick = -1;
				return Result.COUNTED;
			}
			// A missed/uncleared due interaction is excluded regardless of its
			// outcome. It cannot support another flinch from a known timer.
			end(); anchor(tick, acceptedFishing, idleAfter, caught);
			return Result.NONE;
		}
		if (isActive())
		{
			if (caught == 1)
			{
				// A catch between modelled rolls contradicts this segment's phase.
				invalidate(); anchor(tick, acceptedFishing, idleAfter, caught);
				return Result.INVALIDATED;
			}
			if ((long) tick == anchorTick + 1 && qualifiedFlinch && !acceptedFishing)
			{
				dueTick = (long) tick + 1;
				return Result.ARMED;
			}
			end();
		}
		anchor(tick, acceptedFishing, idleAfter, caught);
		return Result.NONE;
	}

	private void anchor(int tick, boolean acceptedFishing, boolean idleAfter, int caught)
	{
		if (caught == 1 && acceptedFishing && idleAfter) { anchorTick = tick; }
	}
	private void invalidate()
	{
		catches -= segmentCatches; failures -= segmentFailures; end();
	}
	/** A deliberate interruption retains every completed qualified roll. */
	public void end()
	{
		anchorTick = dueTick = -1; segmentCatches = segmentFailures = 0;
	}
	/** Offline/unobserved time cannot continue a reconstructed server timer. */
	public void discontinuity() { end(); lastTick = -1; }
	public void reset() { restore(0, 0); }
	public long getCatches() { return catches; }
	public long getFailures() { return failures; }
	public long getAttempts() { return catches + failures; }
	public boolean isActive() { return anchorTick >= 0; }
	public boolean isAwaitingRoll() { return dueTick >= 0; }
}
