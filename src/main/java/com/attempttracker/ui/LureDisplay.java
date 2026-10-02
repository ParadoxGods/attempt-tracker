package com.attempttracker.ui;

/** Live game choice, displayed separately from historical session totals. */
public final class LureDisplay
{
	public final String amount;
	public final String note;
	public final String tooltip;
	public LureDisplay(String amount, String note, String tooltip)
	{
		this.amount = amount; this.note = note; this.tooltip = tooltip;
	}
	public static LureDisplay waiting()
	{
		return new LureDisplay("--", "Log in to detect", "The live lure choice is available while logged in.");
	}
}
