package com.attempttracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development launcher following RuneLite's official external-plugin template. */
public final class AttemptTrackerLauncher
{
	private AttemptTrackerLauncher() { }

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AttemptTrackerPlugin.class);
		RuneLite.main(args);
	}
}
