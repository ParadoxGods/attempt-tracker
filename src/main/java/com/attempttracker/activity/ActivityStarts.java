package com.attempttracker.activity;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.runelite.api.Skill;

/**
 * Exact gathering-start messages that can establish a fixed-cycle schedule.
 * A start is not itself a successful or failed result. The caller must filter
 * game-message channels and validate the target, animation and timing mode.
 */
public final class ActivityStarts
{
	private static final Pattern FORMATTING = Pattern.compile("<[^>]*>|@[a-z0-9_]+@", Pattern.CASE_INSENSITIVE);
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private ActivityStarts() { }

	public static Optional<Skill> parseStart(String message)
	{
		String clean = normalize(message);
		if (matches(clean, "you start harpooning fish") || matches(clean, "you cast out your line"))
		{
			return Optional.of(Skill.FISHING);
		}
		if (matches(clean, "you swing your pick at the rock"))
		{
			return Optional.of(Skill.MINING);
		}
		if (matches(clean, "you swing your axe at the tree"))
		{
			return Optional.of(Skill.WOODCUTTING);
		}
		return Optional.empty();
	}

	public static boolean isHarpooning(String message)
	{
		return matches(normalize(message), "you start harpooning fish");
	}

	private static boolean matches(String message, String body)
	{
		return message.equals(body) || message.equals(body + ".") || message.equals(body + "!") || message.equals(body + "...");
	}

	private static String normalize(String message)
	{
		return WHITESPACE.matcher(FORMATTING.matcher(message == null ? "" : message).replaceAll("")
			.replace('\u00a0', ' ').replace('\u2019', '\''))
			.replaceAll(" ").trim().toLowerCase(Locale.ROOT);
	}
}
