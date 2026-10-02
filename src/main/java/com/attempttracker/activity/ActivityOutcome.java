package com.attempttracker.activity;

import java.util.Objects;

/** A single action outcome described explicitly by a game message. */
public final class ActivityOutcome
{

	private final String activity;
	private final String skillName;
	private final boolean success;
	private final boolean timed;

	ActivityOutcome(String activity, String skillName, boolean success, boolean timed)
	{
		this.activity = Objects.requireNonNull(activity, "activity");
		this.skillName = Objects.requireNonNull(skillName, "skillName");
		this.success = success;
		this.timed = timed;
	}

	public String getActivity()
	{
		return activity;
	}

	/** The spelling of the corresponding RuneLite Skill enum constant. */
	public String getSkillName()
	{
		return skillName;
	}

	public boolean isSuccess()
	{
		return success;
	}

	/**
	 * Whether an adapter must identify timed rolls to measure silent failures.
	 * This flag does not itself assert that a failure or a particular roll happened.
	 */
	public boolean isTimed()
	{
		return timed;
	}
}
