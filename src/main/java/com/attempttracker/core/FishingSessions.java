package com.attempttracker.core;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Session totals and monotonic active clocks, independent of client tick numbering. */
public final class FishingSessions
{
	private final LongSupplier nanoClock;
	private final List<FishingSession> sessions = new ArrayList<>();
	private final VariableRollTracker variable = new VariableRollTracker();
	private Map<String, long[]> previous = new HashMap<>();
	private long lastNanos;
	private boolean logged, fishing, variableActive;
	private long runMinimum, runMaximum, runSuccesses;

	public FishingSessions() { this(System::nanoTime); }
	public FishingSessions(LongSupplier nanoClock) { this.nanoClock = nanoClock; lastNanos = nanoClock.getAsLong(); sessions.add(new FishingSession()); }
	public void restore(List<FishingSession> saved)
	{
		sessions.clear();
		if (saved != null)
		{
			for (FishingSession entry : saved)
			{
				if (entry != null && entry.id != null && entry.startedAt > 0 && entry.catches >= 0 && entry.measuredCatches >= 0
					&& entry.measuredCatches <= entry.catches && entry.minimumFailures >= 0 && entry.maximumFailures >= entry.minimumFailures
					&& entry.loggedMillis >= 0 && entry.fishingMillis >= 0 && entry.fishingMillis <= entry.loggedMillis && entry.fishingTicks >= 0)
				{
					sessions.add(entry.copy()); if (sessions.size() == 200) { break; }
				}
			}
		}
		if (sessions.isEmpty() || sessions.get(0).endedAt != 0) { sessions.add(0, new FishingSession()); }
		logged = fishing = variableActive = false; lastNanos = nanoClock.getAsLong();
	}
	public void baseline(List<AttemptSession> history)
	{
		previous.clear(); for (AttemptSession entry : history) { previous.put(entry.getId(), totals(entry)); }
	}
	public void importPrevious(List<AttemptSession> history)
	{
		for (AttemptSession entry : history)
		{
			if (entry.getActivity().startsWith("Fishing:") && entry.getMethod() == TrackingMethod.TIMED)
			{
				FishingSession current = current(); current.startedAt = entry.getStartedAt(); current.catches = current.measuredCatches = entry.getSuccesses();
				current.minimumFailures = current.maximumFailures = entry.getFailures(); break;
			}
		}
		baseline(history);
	}
	public void sync(List<AttemptSession> history)
	{
		long[] delta = new long[3]; Map<String, long[]> next = new HashMap<>();
		for (AttemptSession entry : history)
		{
			long[] total = totals(entry); long[] old = previous.getOrDefault(entry.getId(), new long[3]);
			for (int i = 0; i < 3; i++) { delta[i] += total[i] - old[i]; }
			next.put(entry.getId(), total);
		}
		FishingSession current = current();
		current.catches = Math.max(0, current.catches + delta[0]); current.measuredCatches = Math.max(0, current.measuredCatches + delta[1]);
		current.minimumFailures = Math.max(0, current.minimumFailures + delta[2]);
		current.maximumFailures = Math.max(current.minimumFailures, current.maximumFailures + delta[2]); previous = next;
	}
	private static long[] totals(AttemptSession entry)
	{
		long[] total = new long[3];
		if (!entry.getActivity().startsWith("Fishing:")) { return total; }
		total[0] = entry.getSuccesses();
		if (entry.getMethod() != TrackingMethod.SUCCESS_ONLY) { total[1] = entry.getSuccesses(); total[2] = entry.getFailures(); }
		return total;
	}
	public void advance(boolean nextLogged, boolean nextFishing)
	{
		long now = nanoClock.getAsLong(); long elapsed = Math.max(0, now - lastNanos);
		if (logged) { current().loggedMillis += elapsed / 1_000_000; }
		if (fishing) { current().fishingMillis += elapsed / 1_000_000; }
		lastNanos = now; logged = nextLogged; fishing = nextLogged && nextFishing;
	}
	public void fishingTick() { current().fishingTicks++; }
	public void reset()
	{
		advance(logged, false); stopVariable(); current().endedAt = System.currentTimeMillis();
		sessions.add(0, new FishingSession()); if (sessions.size() > 200) { sessions.remove(sessions.size() - 1); }
	}
	public void startVariable(int tick, int shortest, int longest)
	{
		stopVariable(); variable.start(tick, shortest, longest, shortest - 1, longest - 1);
		variableActive = true; runMinimum = runMaximum = runSuccesses = 0; current().variableTiming = true;
	}
	public boolean observeVariable(int tick, int catches)
	{
		if (!variableActive) { return false; }
		if (!variable.observe(tick, catches))
		{
			current().minimumFailures -= runMinimum; current().maximumFailures -= runMaximum; current().measuredCatches -= runSuccesses;
			stopVariable(); return false;
		}
		current().minimumFailures += variable.getMinimumFailures() - runMinimum;
		current().maximumFailures += variable.getMaximumFailures() - runMaximum;
		current().measuredCatches += variable.getSuccesses() - runSuccesses;
		runMinimum = variable.getMinimumFailures(); runMaximum = variable.getMaximumFailures(); runSuccesses = variable.getSuccesses();
		return true;
	}
	public boolean isVariableActive() { return variableActive; }
	public void stopVariable() { variableActive = false; runMinimum = runMaximum = runSuccesses = 0; }
	public FishingSession current() { return sessions.get(0); }
	public List<FishingSession> snapshots()
	{
		List<FishingSession> copy = new ArrayList<>(); for (FishingSession entry : sessions) { copy.add(entry.copy()); } return copy;
	}
}
