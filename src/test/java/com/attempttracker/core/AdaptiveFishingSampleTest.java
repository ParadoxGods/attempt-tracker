package com.attempttracker.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveFishingSampleTest
{
	@Test public void boundsContainHiddenRatesForDifferentCadencesAndPhases()
	{
		for (int cadence = 1; cadence <= 9; cadence++)
		{
			for (int phase = 0; phase < cadence; phase++)
			{
				AdaptiveFishingSample sample = new AdaptiveFishingSample(); int rolls = 0, catches = 0;
				for (int tick = 0; tick < 600; tick++)
				{
					boolean roll = tick % cadence == phase; if (roll) { rolls++; }
					int caught = roll && rolls % 3 != 0 ? 1 : 0; catches += caught;
					sample.observe(tick, true, caught, false, false, false, 0);
				}
				FishingSession session = new FishingSession(); session.catches = session.adaptiveCatches = sample.getCatches(); session.adaptiveFailureUpper = sample.getFailuresUpper();
				assertEquals(catches, session.adaptiveCatches);
				assertTrue(catches / ((double)catches + session.adaptiveFailureUpper) <= (double)catches / rolls);
				assertTrue(Double.isNaN(session.rate(false))); assertTrue(Double.isNaN(session.rate(true)));
			}
		}
	}
	@Test public void twoTickActivityWindowsRemainDiagnosticEvidenceWithoutEstablishingRate()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample();
		for (int tick = 0; tick < 20; tick++) { sample.observe(tick, true, tick % 2 == 1 ? 1 : 0, false, false, false, 0); }
		assertEquals(10, sample.getCatches()); assertEquals(10, sample.getFailuresUpper());
		FishingSession session = new FishingSession(); session.catches = session.adaptiveCatches = sample.getCatches();
		session.adaptiveFailureUpper = sample.getFailuresUpper();
		assertEquals(10, session.adaptiveCatches); assertEquals(10, session.adaptiveFailureUpper);
		assertEquals(0, session.rateCatches()); assertTrue(Double.isNaN(session.rate(false))); assertTrue(Double.isNaN(session.rate(true)));
	}
	@Test public void invalidatedStrictRunIsRecoveredExactlyOnceAndNotDoubleCounted()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample();
		for (int tick = 0; tick <= 6; tick++) { sample.observe(tick, true, tick == 4 || tick == 6 ? 1 : 0, tick < 6, tick == 0, tick == 6, tick == 4 ? 1 : 0); }
		assertEquals(2, sample.getCatches()); assertEquals(5, sample.getFailuresUpper());
		sample.observe(6, true, 1, false, false, true, 0);
		assertEquals(2, sample.getCatches());
		sample.observe(7, true, 0, false, false, true, 0);
		assertEquals(2, sample.getCatches()); assertEquals(6, sample.getFailuresUpper());
	}
	@Test public void ordinaryCompletedTrialsRemainSeparateAndStopsExcludeOfflineTime()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample();
		for (int tick = 0; tick <= 9; tick++) { sample.observe(tick, true, tick == 4 || tick == 9 ? 1 : 0, true, tick == 0, false, tick == 4 || tick == 9 ? 1 : 0); }
		sample.observe(10, false, 0, false, false, false, 0);
		assertEquals(0, sample.getCatches()); assertEquals(0, sample.getFailuresUpper()); assertFalse(sample.isUsed());
		sample.discontinuity(); sample.observe(5000, true, 1, false, false, false, 0);
		assertEquals(1, sample.getCatches()); assertEquals(0, sample.getFailuresUpper());
	}
	@Test public void missingActiveTicksWidenBoundsButPausedGapsDoNot()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample();
		sample.observe(0, true, 0, false, false, false, 0);
		sample.observe(4, true, 1, false, false, false, 0);
		assertEquals(4, sample.getFailuresUpper());
		sample.observe(5, false, 0, false, false, false, 0);
		sample.observe(10000, true, 1, false, false, false, 0);
		assertEquals(4, sample.getFailuresUpper()); assertEquals(2, sample.getCatches());
	}
	@Test public void ambiguousBatchesAndUnrelatedCatchesAreExcluded()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample();
		sample.observe(0, true, 2, false, false, false, 0);
		sample.observe(1, false, 1, false, false, false, 0);
		assertEquals(0, sample.getCatches()); assertEquals(0, sample.getFailuresUpper());
	}
	@Test public void restoredSampleSurvivesNewStrictRunAndManualResetClearsIt()
	{
		AdaptiveFishingSample sample = new AdaptiveFishingSample(); sample.restore(12, 20, true);
		sample.observe(0, true, 0, true, true, false, 0); sample.observe(4, true, 1, true, false, false, 1);
		assertEquals(12, sample.getCatches()); assertEquals(20, sample.getFailuresUpper());
		sample.restore(0, 0, false); assertEquals(0, sample.getCatches()); assertFalse(sample.isUsed());
	}
}
