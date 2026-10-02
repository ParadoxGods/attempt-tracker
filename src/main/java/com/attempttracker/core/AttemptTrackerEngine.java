package com.attempttracker.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Counting and session history without client dependencies.
 *
 * Timed mode requires an explicit action start and one observation every game
 * tick. A start at T with first delay D makes T + D the first attempt deadline;
 * subsequent deadlines are C ticks apart. Each uninterrupted deadline
 * contributes one success or one inferred failure.
 * Missing ticks, off-deadline outcomes, configuration changes and interruption
 * discard the unfinished cycle. Excluded windows are discarded timing windows,
 * not known failed attempts. An invalid timer requires another explicit start.
 * Ambiguous timing rolls back the latest start's estimated trials and preserves
 * its visible catches separately. Intentional interruption keeps completed trials.
 */
public final class AttemptTrackerEngine
{
	private static final int MAX_HISTORY = 200;

	/** Result of a tick observation; rejected successes remain the caller's responsibility. */
	public enum TimedResult
	{
		/** No active start-anchored timer; outcomes have not been counted. */
		WAITING_FOR_START,
		/** Accepted tick without a new completed attempt (also duplicate ticks). */
		WAITING,
		/** Exactly one complete active cycle has entered the denominator. */
		COUNTED,
		/** Timing stopped; any successfulRolls in this call remain uncounted. */
		INVALIDATED
	}

	private final LongSupplier clock;
	private final List<AttemptSession> sessions = new ArrayList<>();
	private final Map<Profile, AttemptSession> activeProfiles = new HashMap<>();
	private AttemptSession currentSession;
	private Profile timedProfile;
	private AttemptSession timedSession;
	private int boundaryTick;
	private long nextDeadlineTick;
	private int lastTick;
	private boolean observedLastTick;
	private long startSuccesses;
	private long startFailures;

	public AttemptTrackerEngine()
	{
		this(System::currentTimeMillis);
	}

	AttemptTrackerEngine(LongSupplier clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** Loads validated history. Restored sessions do not resume old timing/grouping. */
	public synchronized void restore(List<AttemptSession> restored)
	{
		clear();
		if (restored == null)
		{
			return;
		}
		long now = now();
		List<AttemptSession> valid = new ArrayList<>();
		for (AttemptSession value : restored)
		{
			AttemptSession sanitized = sanitize(value, now);
			if (sanitized != null)
			{
				valid.add(sanitized);
			}
		}
		valid.sort(Comparator.comparingLong(AttemptSession::getUpdatedAt).reversed()
			.thenComparing(Comparator.comparingLong(AttemptSession::getStartedAt).reversed()));
		Set<String> ids = new HashSet<>();
		for (AttemptSession value : valid)
		{
			if (ids.add(value.getId()))
			{
				sessions.add(value);
				if (sessions.size() == MAX_HISTORY)
				{
					break;
				}
			}
		}
	}

	public synchronized List<AttemptSession> getSessions()
	{
		List<AttemptSession> snapshots = new ArrayList<>(sessions.size());
		for (AttemptSession session : sessions)
		{
			snapshots.add(session.copy());
		}
		return Collections.unmodifiableList(snapshots);
	}

	public synchronized AttemptSession getCurrentSession()
	{
		return currentSession == null ? null : currentSession.copy();
	}

	public synchronized void recordObserved(String activity, String setup, boolean success)
	{
		Profile profile = new Profile(requiredActivity(activity), normalizedSetup(setup),
			TrackingMethod.OBSERVED, 0);
		AttemptSession session = sessionFor(profile);
		session.addCounts(success ? 1 : 0, success ? 0 : 1, now());
		makeLatest(session);
	}

	/** Records catches without claiming that failed rolls or total attempts are known. */
	public synchronized void recordSuccessOnly(String activity, String setup)
	{
		recordSuccessOnly(activity, setup, 1);
	}

	/** Preserves a batch of visible outcomes without an attempt denominator. */
	public synchronized void recordSuccessOnly(String activity, String setup, long successfulRolls)
	{
		if (successfulRolls < 0)
		{
			throw new IllegalArgumentException("successfulRolls must be nonnegative");
		}
		Profile profile = new Profile(requiredActivity(activity), normalizedSetup(setup),
			TrackingMethod.SUCCESS_ONLY, 0);
		if (successfulRolls == 0)
		{
			return;
		}
		AttemptSession session = sessionFor(profile);
		session.addCounts(successfulRolls, 0, now());
		makeLatest(session);
	}

	/**
	 * Starts or restarts a timer from an explicit action-start signal. The first
	 * deadline is tick + cycleTicks; the start tick is not an attempt. Call this
	 * before observeTimed when both events belong to the same game tick.
	 */
	public synchronized void startTimed(int tick, String activity, String setup, int cycleTicks)
	{
		startTimed(tick, activity, setup, cycleTicks, cycleTicks);
	}

	/** Starts a timer with a separately validated delay before its first attempt. */
	public synchronized void startTimed(int tick, String activity, String setup, int cycleTicks,
		int firstDelayTicks)
	{
		Profile profile = timedProfile(activity, setup, cycleTicks);
		if (firstDelayTicks < 1 || firstDelayTicks > 100)
		{
			throw new IllegalArgumentException("firstDelayTicks must be between 1 and 100");
		}
		discardTiming(false, tick);
		timedProfile = profile;
		timedSession = sessionFor(profile);
		boundaryTick = tick;
		nextDeadlineTick = (long) tick + firstDelayTicks;
		lastTick = tick;
		observedLastTick = false;
		startSuccesses = 0;
		startFailures = 0;
		timedSession.touch(now());
		makeLatest(timedSession);
	}

	public synchronized boolean isTimingActive()
	{
		return timedSession != null;
	}

	/**
	 * Supply every game tick, with zero successfulRolls when no catch occurred.
	 * COUNTED means this tick completed one active cycle. WAITING includes the
	 * start tick, intervening ticks and ignored duplicate observations. Outcomes
	 * before or between deadlines invalidate timing, as do multiple successes,
	 * missing ticks and profile changes. None establishes a replacement anchor.
	 * Ambiguity rolls back trials since the latest start and preserves their
	 * visible catches as SUCCESS_ONLY. An ineligible observation intentionally
	 * interrupts instead: completed trials remain. Ineligible observations do not
	 * require activity/setup/cadence values.
	 */
	public synchronized TimedResult observeTimed(int tick, String activity, String setup,
		int cycleTicks, boolean eligible, int successfulRolls)
	{
		if (successfulRolls < 0)
		{
			throw new IllegalArgumentException("successfulRolls must be nonnegative");
		}
		if (!eligible)
		{
			boolean wasActive = isTimingActive();
			discardTiming(false, tick);
			return wasActive ? TimedResult.INVALIDATED : TimedResult.WAITING_FOR_START;
		}
		Profile profile = timedProfile(activity, setup, cycleTicks);
		if (!isTimingActive())
		{
			return TimedResult.WAITING_FOR_START;
		}
		long difference = (long) tick - lastTick;
		if (difference == 0 && observedLastTick)
		{
			return TimedResult.WAITING;
		}
		if ((difference != 1 && !(difference == 0 && !observedLastTick))
			|| !profile.equals(timedProfile) || successfulRolls > 1)
		{
			discardTiming(true, tick);
			return TimedResult.INVALIDATED;
		}
		lastTick = tick;
		observedLastTick = true;
		if ((long) tick < nextDeadlineTick)
		{
			if (successfulRolls != 0)
			{
				discardTiming(true, tick);
				return TimedResult.INVALIDATED;
			}
			return TimedResult.WAITING;
		}
		// Never fill unknown cycles or accept an outcome after its deadline.
		if ((long) tick != nextDeadlineTick
			|| !timedSession.addCounts(successfulRolls, successfulRolls == 0 ? 1 : 0, now()))
		{
			discardTiming(true, tick);
			return TimedResult.INVALIDATED;
		}
		boundaryTick = tick;
		nextDeadlineTick = (long) tick + cycleTicks;
		startSuccesses += successfulRolls;
		startFailures += successfulRolls == 0 ? 1 : 0;
		makeLatest(timedSession);
		return TimedResult.COUNTED;
	}

	public synchronized void interruptTiming()
	{
		discardTiming(false, lastTick);
	}

	/**
	 * Rejects ambiguous timing evidence detected by the client adapter. Unlike an
	 * intentional stop, this rolls back the latest start's estimated trials and
	 * preserves its already counted visible catches without a denominator.
	 */
	public synchronized TimedResult invalidateTiming(int tick)
	{
		if (!isTimingActive())
		{
			return TimedResult.WAITING_FOR_START;
		}
		discardTiming(true, tick);
		return TimedResult.INVALIDATED;
	}

	/** Begins new grouping while retaining historical session snapshots. */
	public synchronized void startNewSession()
	{
		interruptTiming();
		activeProfiles.clear();
		currentSession = null;
	}

	public synchronized void clear()
	{
		sessions.clear();
		activeProfiles.clear();
		currentSession = null;
		timedProfile = null;
		timedSession = null;
		observedLastTick = false;
		startSuccesses = 0;
		startFailures = 0;
	}

	private void discardTiming(boolean ambiguous, int interruptionTick)
	{
		long preservedSuccesses = ambiguous ? startSuccesses : 0;
		Profile discardedProfile = timedProfile;
		if (ambiguous && timedSession != null
			&& !timedSession.removeCounts(startSuccesses, startFailures, now()))
		{
			throw new IllegalStateException("timed contributions exceed session counters");
		}
		if (timedSession != null
			&& (ambiguous || (long) interruptionTick > boundaryTick || (long) lastTick > boundaryTick))
		{
			timedSession.excludeWindow(now());
			makeLatest(timedSession);
		}
		timedSession = null;
		timedProfile = null;
		observedLastTick = false;
		startSuccesses = 0;
		startFailures = 0;
		if (preservedSuccesses != 0 && discardedProfile != null)
		{
			recordSuccessOnly(discardedProfile.activity, discardedProfile.setup, preservedSuccesses);
		}
	}

	private AttemptSession sessionFor(Profile profile)
	{
		AttemptSession session = activeProfiles.get(profile);
		if (session == null)
		{
			long now = now();
			session = new AttemptSession(UUID.randomUUID().toString(), profile.activity,
				profile.setup, profile.method, now, now, 0, 0, 0, profile.cycleTicks);
			activeProfiles.put(profile, session);
			sessions.add(0, session);
			trimHistory();
		}
		return session;
	}

	private void makeLatest(AttemptSession session)
	{
		sessions.remove(session);
		sessions.add(0, session);
		currentSession = session;
	}

	private void trimHistory()
	{
		while (sessions.size() > MAX_HISTORY)
		{
			AttemptSession removed = sessions.remove(sessions.size() - 1);
			activeProfiles.values().removeIf(value -> value == removed);
			if (timedSession == removed)
			{
				timedSession = null;
				timedProfile = null;
				observedLastTick = false;
				startSuccesses = 0;
				startFailures = 0;
			}
		}
	}

	private long now()
	{
		return Math.max(0L, clock.getAsLong());
	}

	private static String requiredActivity(String activity)
	{
		if (activity == null || activity.trim().isEmpty())
		{
			throw new IllegalArgumentException("activity must not be blank");
		}
		return activity.trim();
	}

	private static String normalizedSetup(String setup)
	{
		return setup == null ? "" : setup.trim();
	}

	private static Profile timedProfile(String activity, String setup, int cycleTicks)
	{
		if (cycleTicks < 1)
		{
			throw new IllegalArgumentException("cycleTicks must be positive");
		}
		return new Profile(requiredActivity(activity), normalizedSetup(setup),
			TrackingMethod.TIMED, cycleTicks);
	}

	private static AttemptSession sanitize(AttemptSession value, long now)
	{
		if (value == null || value.getActivity() == null || value.getActivity().trim().isEmpty()
			|| value.getMethod() == null || value.getSuccesses() < 0 || value.getFailures() < 0
			|| value.getSuccesses() > Long.MAX_VALUE - value.getFailures()
			|| (value.getMethod() == TrackingMethod.SUCCESS_ONLY && value.getFailures() != 0)
			|| (value.getMethod() == TrackingMethod.TIMED && value.getCycleTicks() < 1))
		{
			return null;
		}
		String id = value.getId() == null ? "" : value.getId().trim();
		if (id.isEmpty())
		{
			id = UUID.randomUUID().toString();
		}
		long startedAt = Math.max(0L, Math.min(now, value.getStartedAt()));
		long updatedAt = Math.max(startedAt, Math.min(now, value.getUpdatedAt()));
		return new AttemptSession(id, value.getActivity().trim(), normalizedSetup(value.getSetup()),
			value.getMethod(), startedAt, updatedAt, value.getSuccesses(), value.getFailures(),
			Math.max(0, value.getExcludedWindows()),
			value.getMethod() == TrackingMethod.TIMED ? value.getCycleTicks() : 0);
	}

	private static final class Profile
	{
		private final String activity;
		private final String setup;
		private final TrackingMethod method;
		private final int cycleTicks;

		private Profile(String activity, String setup, TrackingMethod method, int cycleTicks)
		{
			this.activity = activity;
			this.setup = setup;
			this.method = method;
			this.cycleTicks = cycleTicks;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other) { return true; }
			if (!(other instanceof Profile)) { return false; }
			Profile profile = (Profile) other;
			return cycleTicks == profile.cycleTicks && activity.equals(profile.activity)
				&& setup.equals(profile.setup) && method == profile.method;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(activity, setup, method, cycleTicks);
		}
	}
}
