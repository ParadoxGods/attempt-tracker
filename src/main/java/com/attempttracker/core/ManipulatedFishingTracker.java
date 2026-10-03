package com.attempttracker.core;

/**
 * Reconstructs the shared skilling timer from qualified, contiguous observations.
 * This is a mechanics model, not a server failed-roll event. Unrecognised actions
 * discontinue it instead of widening a denominator to every active game tick.
 */
public final class ManipulatedFishingTracker
{
	public enum Family { HARPOON, ROD, UNSUPPORTED }
	public enum Result { NONE, ARMED, COUNTED, INVALIDATED }

	/**
	 * One resolved frame. All acceptance, geometry and action qualification must
	 * be decided independently of caught. Local clicks alone are not acceptance.
	 * interactionCleared is a qualified clearing action/transition before combat;
	 * fishingContinues is compatible fishing evidence, not a facing direction.
	 * productionReset denotes accepted three-tick herb-tar/knife-log production.
	 * outgoingAttackSpeed denotes an actual observed attack, not an Attack click.
	 * foodDelay is confirmed consumption, not an Eat click. A flinch cue still
	 * needs this model's expired timer and logically cleared fishing interaction.
	 */
	public static final class Frame
	{
		public final int tick;
		public final Family family;
		public final boolean eligibleGeometry, acceptedFishing, fishingContinues, interactionCleared;
		public final boolean qualifiedFlinchCue, productionReset, competingAction;
		public final int outgoingAttackSpeed, foodDelay, caught, flinchDelay;

		public Frame(int tick, Family family, boolean eligibleGeometry, boolean acceptedFishing,
			boolean fishingContinues, boolean interactionCleared, boolean qualifiedFlinchCue,
			boolean productionReset, int outgoingAttackSpeed, int foodDelay, boolean competingAction, int caught)
		{
			this(tick, family, eligibleGeometry, acceptedFishing, fishingContinues, interactionCleared,
				qualifiedFlinchCue, productionReset, outgoingAttackSpeed, foodDelay, competingAction, caught, 1);
		}
		/** Flinch delay is floor(verified effective weapon speed / 2), not input cadence. */
		public Frame(int tick, Family family, boolean eligibleGeometry, boolean acceptedFishing,
			boolean fishingContinues, boolean interactionCleared, boolean qualifiedFlinchCue,
			boolean productionReset, int outgoingAttackSpeed, int foodDelay, boolean competingAction, int caught,
			int qualifiedFlinchDelay)
		{
			if (tick < 0 || family == null || outgoingAttackSpeed < 0 || outgoingAttackSpeed > 20
				|| (foodDelay != 0 && foodDelay != 2 && foodDelay != 3) || caught < 0
				|| qualifiedFlinchDelay < 0 || qualifiedFlinchDelay > 10)
			{
				throw new IllegalArgumentException("Invalid manipulated fishing frame");
			}
			this.tick = tick; this.family = family; this.eligibleGeometry = eligibleGeometry;
			this.acceptedFishing = acceptedFishing; this.fishingContinues = fishingContinues;
			this.interactionCleared = interactionCleared; this.qualifiedFlinchCue = qualifiedFlinchCue;
			this.productionReset = productionReset; this.outgoingAttackSpeed = outgoingAttackSpeed;
			this.foodDelay = foodDelay; this.competingAction = competingAction; this.caught = caught;
			this.flinchDelay = qualifiedFlinchDelay;
		}
	}

	private long catches, minimumFailures, maximumFailures, segmentCatches, segmentFailures;
	private int lastTick = -1;
	private long timer;
	private Family family = Family.UNSUPPORTED;
	private boolean anchored, manipulated, logicalFishing, frameOwned;

	public Result observe(Frame frame)
	{
		if (frame == null) { throw new IllegalArgumentException("Missing frame"); }
		frameOwned = manipulated;
		if (frame.tick == lastTick) { return Result.NONE; }
		boolean contiguous = lastTick < 0 || (long) frame.tick == (long) lastTick + 1;
		lastTick = frame.tick;
		if (!contiguous || (anchored && family != frame.family)) { end(); }
		if (!frame.eligibleGeometry || frame.family == Family.UNSUPPORTED || frame.competingAction
			|| (frame.productionReset && frame.foodDelay != 0)
			|| (frame.qualifiedFlinchCue && frame.flinchDelay > 0 && frame.acceptedFishing)
			|| (frame.outgoingAttackSpeed != 0 && frame.acceptedFishing))
		{
			end(); return Result.NONE;
		}
		if (frame.caught > 1)
		{
			boolean wasManipulated = manipulated; invalidate();
			return wasManipulated ? Result.INVALIDATED : Result.NONE;
		}
		if (!anchored)
		{
			anchor(frame); return Result.NONE;
		}

		// Timer changes in client input precede flinching/attacking in our turn.
		timer--;
		if (frame.interactionCleared) { logicalFishing = false; }
		boolean primitive = false;
		if (frame.foodDelay != 0)
		{
			timer += frame.foodDelay; logicalFishing = false; primitive = true;
		}
		if (frame.productionReset)
		{
			logicalFishing = false;
			if (timer <= 0) { timer = 2; primitive = true; }
		}
		if (frame.qualifiedFlinchCue && frame.flinchDelay > 0 && timer < 0 && !logicalFishing)
		{
			timer = frame.flinchDelay; primitive = true;
		}
		if (frame.outgoingAttackSpeed != 0)
		{
			timer = frame.outgoingAttackSpeed; logicalFishing = false; primitive = true;
		}
		if (primitive) { manipulated = true; frameOwned = true; }

		boolean fishing = frame.acceptedFishing || (logicalFishing && frame.fishingContinues);
		boolean firstRodInteraction = frame.family == Family.ROD && frame.acceptedFishing;
		boolean roll = manipulated && fishing && timer == 0 && !firstRodInteraction;
		if (frame.caught == 1 && !roll)
		{
			boolean wasManipulated = manipulated;
			if (wasManipulated) { invalidate(); } else { end(); }
			anchor(frame);
			return wasManipulated ? Result.INVALIDATED : Result.NONE;
		}
		if (roll)
		{
			if (getMaximumAttempts() == Long.MAX_VALUE) { end(); return Result.NONE; }
			if (frame.caught == 1) { catches++; segmentCatches++; }
			else { minimumFailures++; maximumFailures++; segmentFailures++; }
			// A fresh harpoon interaction at zero is forced clear even if the
			// client actor target/animation remains visible. An AFK interaction
			// admitted at four survives its later roll; it must not be treated
			// as idle merely because a catch establishes timer zero.
			logicalFishing = frame.family == Family.ROD || !frame.acceptedFishing;
			return Result.COUNTED;
		}

		if (fishing && timer < 0)
		{
			// Ordinary fishing now sets its own cadence (possibly random with
			// lures). Hand control back rather than guess that reset's value.
			end(); return Result.NONE;
		}
		if (frame.acceptedFishing)
		{
			logicalFishing = frame.family == Family.ROD || timer == 4;
		}
		else if (logicalFishing && !frame.fishingContinues)
		{
			// The next rod roll cannot be inferred through missing activity.
			end(); return Result.NONE;
		}
		return primitive ? Result.ARMED : Result.NONE;
	}

	private void anchor(Frame frame)
	{
		// A catch establishes timer zero but does not join a cohort selected
		// by that same successful outcome. No primitive alone invents a seed.
		if (frame.caught == 1 && (frame.acceptedFishing || frame.fishingContinues))
		{
			anchored = true; family = frame.family; timer = 0;
			// Continuation-only evidence cannot establish that a harpoon
			// interaction was fresh at zero. Treat it as still interacting;
			// a verified clear can permit a later flinch, whereas a hit alone
			// cannot turn ordinary AFK fishing into a qualified 2t sample.
			logicalFishing = frame.family == Family.ROD || !frame.acceptedFishing;
		}
	}
	private void invalidate()
	{
		catches -= segmentCatches; minimumFailures -= segmentFailures; maximumFailures -= segmentFailures;
		end();
	}
	/** Deliberate interruptions retain all completed qualified rolls. */
	public void end()
	{
		anchored = manipulated = logicalFishing = false;
		family = Family.UNSUPPORTED; timer = 0; segmentCatches = segmentFailures = 0;
	}
	/** Missing/offline time never resumes an old shared timer. */
	public void discontinuity() { end(); lastTick = -1; frameOwned = false; }
	public void restore(long catches, long failures) { restore(catches, failures, failures); }
	public void restore(long catches, long minimumFailures, long maximumFailures)
	{
		if (catches < 0 || minimumFailures < 0 || maximumFailures < minimumFailures
			|| maximumFailures > Long.MAX_VALUE - catches)
		{
			throw new IllegalArgumentException("Invalid manipulated fishing sample");
		}
		this.catches = catches; this.minimumFailures = minimumFailures; this.maximumFailures = maximumFailures;
		discontinuity();
	}
	public void reset() { restore(0, 0); }
	/** Ownership applies to the last resolved frame, including withdrawals. */
	public boolean ownsFrame() { return frameOwned; }
	public boolean isActive() { return manipulated; }
	public boolean isAnchored() { return anchored; }
	/** Meaningful only while isAnchored() is true. */
	public long getTimer() { return timer; }
	public long getCatches() { return catches; }
	public long getFailures() { return maximumFailures; }
	public long getMinimumFailures() { return minimumFailures; }
	public long getMaximumFailures() { return maximumFailures; }
	public long getMinimumAttempts() { return catches + minimumFailures; }
	public long getMaximumAttempts() { return catches + maximumFailures; }
	/** Upper total for restored bounded samples; new qualified rolls are point counts. */
	public long getAttempts() { return getMaximumAttempts(); }
}
