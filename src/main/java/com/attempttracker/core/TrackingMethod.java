package com.attempttracker.core;

/** Whether outcomes are observed directly or failures are inferred from timing. */
public enum TrackingMethod
{
	OBSERVED,
	TIMED,
	/** Successful outcomes are visible, but no attempt denominator is established. */
	SUCCESS_ONLY
}
