package com.attempttracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Skill;

/** Bounded plain-prefix configuration. User input never becomes executable regex. */
final class CustomActivity
{
	private static final Pattern FORMATTING = Pattern.compile("<[^>]*>|@[a-z0-9_]+@", Pattern.CASE_INSENSITIVE);
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	final boolean enabled;
	final String name;
	final Skill skill;
	final boolean timed;
	final int cycle;
	final int firstDelay;
	final List<String> starts;
	final List<String> successes;
	final List<String> failures;
	final Set<Integer> animations;
	final String problem;

	CustomActivity(AttemptTrackerConfig config)
	{
		enabled = config.customEnabled();
		name = trim(config.customName(), 100);
		skill = config.customSkill();
		timed = config.customMode() == AttemptTrackerConfig.CustomMode.TIMED;
		cycle = config.customCycle();
		firstDelay = config.customFirstRollDelay() == 0 ? cycle : config.customFirstRollDelay();
		starts = prefixes(config.customStart());
		successes = prefixes(config.customSuccess()); failures = prefixes(config.customFailure());
		animations = new HashSet<>();
		boolean invalidAnimation = false;
		String raw = trim(config.customAnimations(), 1000);
		if (!raw.isEmpty())
		{
			for (String part : raw.split(",", -1))
			{
				try { int value = Integer.parseInt(part.trim()); if (value < 0) { invalidAnimation = true; } else { animations.add(value); } }
				catch (NumberFormatException ex) { invalidAnimation = true; }
			}
		}
		String error = "";
		if (name.isEmpty() || skill == null || skill == Skill.OVERALL) { error = "Set a custom activity name and skill."; }
		else if (config.customMode() == null) { error = "Choose a custom counting method."; }
		else if (successes.isEmpty()) { error = "Set at least one custom success prefix."; }
		else if (!timed && failures.isEmpty()) { error = "Observed custom mode needs failure prefixes too."; }
		else if (timed && starts.isEmpty()) { error = "Timed custom mode needs at least one start-message prefix."; }
		else if (timed && (cycle < 1 || cycle > 100)) { error = "Set a fixed cycle between 1 and 100 ticks for timed custom mode."; }
		else if (timed && (firstDelay < 1 || firstDelay > 100)) { error = "Set a first-roll delay between 1 and 100 ticks, or 0 to use the cycle."; }
		else if (timed && (animations.isEmpty() || invalidAnimation)) { error = "Set valid active animation IDs for timed custom mode."; }
		else
		{
			for (String success : successes)
			{
				for (String failure : failures)
				{
					if (success.startsWith(failure) || failure.startsWith(success)) { error = "Custom success and failure prefixes overlap."; }
				}
			}
			if (timed)
			{
				for (String start : starts)
				{
					for (String success : successes)
					{
						if (start.startsWith(success) || success.startsWith(start)) { error = "Custom start and outcome prefixes overlap."; }
					}
					for (String failure : failures)
					{
						if (start.startsWith(failure) || failure.startsWith(start)) { error = "Custom start and outcome prefixes overlap."; }
					}
				}
			}
		}
		problem = enabled ? error : "";
	}

	boolean usable() { return enabled && problem.isEmpty(); }
	boolean matchesStart(String message)
	{
		if (!usable() || !timed) { return false; }
		String clean = normalize(message);
		for (String prefix : starts) { if (clean.startsWith(prefix)) { return true; } }
		return false;
	}
	Boolean outcome(String message)
	{
		if (!usable()) { return null; }
		String clean = normalize(message);
		for (String prefix : successes) { if (clean.startsWith(prefix)) { return true; } }
		for (String prefix : failures) { if (clean.startsWith(prefix)) { return false; } }
		return null;
	}

	String signature() { return name + " / " + skill + " / " + timed + " / " + cycle + " / first delay " + firstDelay + " / " + starts + " / " + successes + " / " + failures + " / " + new java.util.TreeSet<>(animations); }
	static String trim(String value, int maximum) { String text = value == null ? "" : value.trim(); return text.substring(0, Math.min(text.length(), maximum)); }
	static String normalize(String value)
	{
		return WHITESPACE.matcher(FORMATTING.matcher(value == null ? "" : value).replaceAll("")
			.replace('\u00a0', ' ').replace('\u2019', '\''))
			.replaceAll(" ").trim().toLowerCase(Locale.ROOT);
	}
	private static List<String> prefixes(String input)
	{
		List<String> result = new ArrayList<>();
		for (String line : trim(input, 8000).split("\\R"))
		{
			String value = normalize(trim(line, 240));
			if (!value.isEmpty() && !result.contains(value)) { result.add(value); }
			if (result.size() == 32) { break; }
		}
		return result;
	}
}
