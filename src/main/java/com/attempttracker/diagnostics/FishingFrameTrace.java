package com.attempttracker.diagnostics;

/**
 * Bounded optional callback evidence, without arbitrary chat, names or item dumps.
 * Event order is client callback order; it is not guaranteed server execution order.
 * Callers supply fixed category literals and numeric observations only.
 */
public final class FishingFrameTrace
{
	private static final int MAX_EVENTS = 64, MAX_CHARACTERS = 4096, MAX_VALUES = 16;
	private StringBuilder buffer;
	private int count;
	private boolean truncated;

	public void clear()
	{
		if (buffer != null) { buffer.setLength(0); }
		count = 0; truncated = false;
	}

	public void record(int gameCycle, String fixedCategory, int... numericValues)
	{
		validateCategory(fixedCategory);
		if (truncated) { return; }
		if (count == MAX_EVENTS || numericValues == null || numericValues.length > MAX_VALUES)
		{
			truncated = true; return;
		}
		StringBuilder token = new StringBuilder(64);
		token.append(count).append('@').append(gameCycle).append(':').append(fixedCategory);
		for (int value : numericValues) { token.append(':').append(value); }
		int separator = count == 0 ? 0 : 1;
		if ((buffer == null ? 0 : buffer.length()) + separator + token.length() > MAX_CHARACTERS)
		{
			truncated = true; return;
		}
		if (buffer == null) { buffer = new StringBuilder(256); }
		if (separator != 0) { buffer.append('|'); }
		buffer.append(token); count++;
	}

	private static void validateCategory(String category)
	{
		if (category == null || category.isEmpty() || category.length() > 40)
		{
			throw new IllegalArgumentException("Trace category must be a fixed identifier");
		}
		for (int i = 0; i < category.length(); i++)
		{
			char character = category.charAt(i);
			if (!(character >= 'a' && character <= 'z') && !(character >= 'A' && character <= 'Z')
				&& !(character >= '0' && character <= '9') && character != '_' && character != '-')
			{
				throw new IllegalArgumentException("Trace category must be a fixed identifier");
			}
		}
	}

	public String events() { return buffer == null || buffer.length() == 0 ? "" : buffer.toString(); }
	public boolean truncated() { return truncated; }
}
