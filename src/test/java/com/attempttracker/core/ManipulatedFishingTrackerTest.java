package com.attempttracker.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.attempttracker.core.ManipulatedFishingTracker.Family.*;
import static com.attempttracker.core.ManipulatedFishingTracker.Result.*;

public class ManipulatedFishingTrackerTest
{
	@Test public void twoTickHarpoonCountsFailuresDespiteLingeringClientFishingState()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		for (int roll = 1; roll <= 50; roll++)
		{
			assertEquals(ARMED, tracker.observe(frame(roll * 2 - 1, HARPOON, false, false, false, true, false, 0, 0, 0)));
			assertEquals(COUNTED, tracker.observe(frame(roll * 2, HARPOON, true, true, false, false, false, 0, 0, roll % 5 == 0 ? 0 : 1)));
		}
		assertEquals(40, tracker.getCatches()); assertEquals(10, tracker.getMinimumFailures()); assertEquals(10, tracker.getMaximumFailures());
		assertEquals(0.8, (double) tracker.getCatches() / tracker.getAttempts(), 0);
	}
	@Test public void ordinaryCatchAnchorNeverOwnsFramesOrCountsUnmanipulatedFishing()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		assertTrue(tracker.isAnchored()); assertFalse(tracker.isActive()); assertFalse(tracker.ownsFrame());
		assertEquals(NONE, tracker.observe(frame(1, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertFalse(tracker.isAnchored()); assertFalse(tracker.ownsFrame()); assertEquals(0, tracker.getAttempts());
	}
	@Test public void productionHarpoonRequiresFreshInteractionAtEveryRoll()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		assertEquals(ARMED, tracker.observe(frame(1, HARPOON, false, false, false, false, true, 0, 0, 0)));
		assertEquals(2, tracker.getTimer()); assertTrue(tracker.ownsFrame());
		assertEquals(NONE, tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0)));
		// The early harpoon was automatically cleared. Its lingering client
		// animation/target on tick 3 does not supply an attempted fish.
		assertEquals(NONE, tracker.observe(frame(3, HARPOON, false, true, false, false, false, 0, 0, 0)));
		assertEquals(0, tracker.getAttempts());
		assertEquals(ARMED, tracker.observe(frame(4, HARPOON, false, false, false, false, true, 0, 0, 0)));
		tracker.observe(frame(5, HARPOON, false, false, false, false, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(6, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getFailures());
	}
	@Test public void productionRodRetainsInteractionAndCountsSilentFailureOnThirdTick()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, ROD);
		assertEquals(ARMED, tracker.observe(frame(1, ROD, false, false, false, false, true, 0, 0, 0)));
		assertEquals(NONE, tracker.observe(frame(2, ROD, true, true, false, false, false, 0, 0, 0)));
		assertEquals(COUNTED, tracker.observe(frame(3, ROD, false, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getAttempts()); assertEquals(1, tracker.getFailures());
	}
	@Test public void alternatingProductionAndKarambwanRodCyclesGiveTwoPointFiveTicks()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, ROD);
		int tick = 0;
		for (int roll = 1; roll <= 100; roll++)
		{
			if (roll % 2 == 1)
			{
				assertEquals(ARMED, tracker.observe(frame(++tick, ROD, false, false, false, false, true, 0, 0, 0)));
				tracker.observe(frame(++tick, ROD, true, true, false, false, false, 0, 0, 0));
			}
			else
			{
				assertEquals(ARMED, tracker.observe(frame(++tick, ROD, true, true, false, false, false, 0, 2, 0)));
			}
			assertEquals(COUNTED, tracker.observe(frame(++tick, ROD, false, true, false, false, false, 0, 0, roll % 5 == 0 ? 0 : 1)));
		}
		assertEquals(250, tick); assertEquals(100, tracker.getAttempts()); assertEquals(80, tracker.getCatches()); assertEquals(20, tracker.getFailures());
	}
	@Test public void irregularKnownResetCompositionsCountAttemptsWithoutFixedRhythm()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		// 2t flinch, 3t production, then a delayed two-tick food addition
		// brings the expired timer directly to zero on the fishing tick.
		tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0)));
		tracker.observe(frame(3, HARPOON, false, false, false, false, true, 0, 0, 0));
		tracker.observe(frame(4, HARPOON, false, false, false, false, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(5, HARPOON, true, true, false, false, false, 0, 0, 1)));
		tracker.observe(frame(6, HARPOON, false, false, true, false, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(7, HARPOON, true, true, false, false, false, 0, 2, 0)));
		assertEquals(3, tracker.getAttempts()); assertEquals(1, tracker.getCatches()); assertEquals(2, tracker.getFailures());
	}
	@Test public void foodAddsToPositiveTimerRatherThanStartingAConstantCycle()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		tracker.observe(frame(1, HARPOON, false, false, false, false, false, 4, 0, 0));
		tracker.observe(frame(2, HARPOON, false, false, false, false, false, 0, 3, 0));
		assertEquals(6, tracker.getTimer());
		for (int tick = 3; tick < 8; tick++) { tracker.observe(frame(tick, HARPOON, false, false, false, false, false, 0, 0, 0)); }
		assertEquals(COUNTED, tracker.observe(frame(8, HARPOON, true, true, false, false, false, 0, 0, 1)));
		assertEquals(1, tracker.getCatches()); assertEquals(1, tracker.getAttempts());
	}
	@Test public void actualOutgoingAttackSetsFullWeaponSpeedAndCanChangeTheMethod()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		assertEquals(ARMED, tracker.observe(frame(1, HARPOON, false, false, false, true, false, 2, 0, 0)));
		assertEquals(2, tracker.getTimer());
		tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(3, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getFailures());
	}
	@Test public void slowerWeaponsSupportThreeAndFourTickFlinchCycles()
	{
		for (int delay : new int[]{2, 3})
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
			int tick = 0;
			for (int roll = 1; roll <= 20; roll++)
			{
				assertEquals(ARMED, tracker.observe(new ManipulatedFishingTracker.Frame(++tick, HARPOON,
					true, false, false, false, true, false, 0, 0, false, 0, delay)));
				assertEquals(delay, tracker.getTimer());
				for (int remaining = delay - 1; remaining > 0; remaining--)
				{
					assertEquals(NONE, tracker.observe(frame(++tick, HARPOON, false, false, false, false, false, 0, 0, 0)));
					assertEquals(remaining, tracker.getTimer());
				}
				assertEquals(COUNTED, tracker.observe(frame(++tick, HARPOON, true, true, false, false, false, 0, 0, roll % 4 == 0 ? 0 : 1)));
			}
			assertEquals(20 * (delay + 1), tick); assertEquals(15, tracker.getCatches()); assertEquals(5, tracker.getFailures());
		}
	}
	@Test public void incomingFlinchUsesHalfSpeedWhileObservedOutgoingAttackUsesFullSpeed()
	{
		ManipulatedFishingTracker incoming = new ManipulatedFishingTracker(), outgoing = new ManipulatedFishingTracker();
		anchor(incoming, 0, HARPOON); anchor(outgoing, 0, HARPOON);
		assertEquals(ARMED, incoming.observe(new ManipulatedFishingTracker.Frame(1, HARPOON,
			true, false, false, false, true, false, 0, 0, false, 0, 3)));
		assertEquals(ARMED, outgoing.observe(frame(1, HARPOON, false, false, false, false, false, 6, 0, 0)));
		assertEquals(3, incoming.getTimer()); assertEquals(6, outgoing.getTimer());
		for (int tick = 2; tick < 4; tick++) { incoming.observe(frame(tick, HARPOON, false, false, false, false, false, 0, 0, 0)); }
		assertEquals(COUNTED, incoming.observe(frame(4, HARPOON, true, true, false, false, false, 0, 0, 0)));
		for (int tick = 2; tick < 7; tick++) { outgoing.observe(frame(tick, HARPOON, false, false, false, false, false, 0, 0, 0)); }
		assertEquals(COUNTED, outgoing.observe(frame(7, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertEquals(1, incoming.getFailures()); assertEquals(1, outgoing.getFailures());
	}
	@Test public void zeroOrUnqualifiedFlinchDelayDoesNotEstablishManipulation()
	{
		for (boolean cue : new boolean[]{false, true})
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
			assertEquals(NONE, tracker.observe(new ManipulatedFishingTracker.Frame(1, HARPOON,
				true, false, false, false, cue, false, 0, 0, false, 0, cue ? 0 : 3)));
			assertEquals(-1, tracker.getTimer()); assertFalse(tracker.isActive()); assertFalse(tracker.ownsFrame());
			tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0)); assertEquals(0, tracker.getAttempts());
		}
	}
	@Test public void invalidFlinchDelaysAreRejected()
	{
		for (int delay : new int[]{-1, 11})
		{
			try
			{
				new ManipulatedFishingTracker.Frame(0, HARPOON, true, false, false, false, true, false, 0, 0, false, 0, delay);
				fail("Expected invalid flinch delay");
			}
			catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
		}
	}
	@Test public void harpoonTimerFourExceptionAllowsContinuousFishingAfterKnownAttack()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		tracker.observe(frame(1, HARPOON, false, false, false, false, false, 5, 0, 0));
		tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0));
		for (int tick = 3; tick < 6; tick++) { tracker.observe(frame(tick, HARPOON, false, true, false, false, false, 0, 0, 0)); }
		assertEquals(COUNTED, tracker.observe(frame(6, HARPOON, false, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getFailures());
	}
	@Test public void continuousHarpoonCatchDoesNotProveLogicalClearForAFlinch()
	{
		for (boolean continues : new boolean[]{false, true})
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker();
			assertEquals(NONE, tracker.observe(frame(0, HARPOON, false, true, false, false, false, 0, 0, 1)));
			assertTrue(tracker.isAnchored()); assertFalse(tracker.isActive());
			assertEquals(NONE, tracker.observe(frame(1, HARPOON, false, continues, false, true, false, 0, 0, 0)));
			assertFalse(tracker.isAnchored()); assertFalse(tracker.ownsFrame()); assertEquals(0, tracker.getAttempts());
		}
	}
	@Test public void explicitClearAfterContinuousCatchCanEstablishARealFlinch()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker();
		tracker.observe(frame(0, HARPOON, false, true, false, false, false, 0, 0, 1));
		assertEquals(ARMED, tracker.observe(frame(1, HARPOON, false, false, true, true, false, 0, 0, 0)));
		assertEquals(COUNTED, tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getFailures());
	}
	@Test public void retainedHarpoonInteractionSurvivesRollAndBlocksFalseFlinch()
	{
		for (boolean explicitClear : new boolean[]{false, true})
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
			tracker.observe(frame(1, HARPOON, false, false, false, false, false, 5, 0, 0));
			tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0));
			for (int tick = 3; tick < 6; tick++) { tracker.observe(frame(tick, HARPOON, false, true, false, false, false, 0, 0, 0)); }
			assertEquals(COUNTED, tracker.observe(frame(6, HARPOON, false, true, false, false, false, 0, 0, 0)));
			assertEquals(explicitClear ? ARMED : NONE, tracker.observe(frame(7, HARPOON, false, false, explicitClear, true, false, 0, 0, 0)));
			assertEquals(explicitClear, tracker.isActive()); assertEquals(1, tracker.getFailures());
			assertEquals(explicitClear ? COUNTED : NONE, tracker.observe(frame(8, HARPOON, true, true, false, false, false, 0, 0, 1)));
			assertEquals(explicitClear ? 1 : 0, tracker.getCatches());
		}
	}
	@Test public void rodFirstInteractionOnDueTickCannotProduceAnAttempt()
	{
		for (int caught = 0; caught <= 1; caught++)
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, ROD);
			tracker.observe(frame(1, ROD, false, false, true, true, false, 0, 0, 0));
			assertEquals(caught == 0 ? NONE : INVALIDATED, tracker.observe(frame(2, ROD, true, true, false, false, false, 0, 0, caught)));
			assertEquals(0, tracker.getAttempts());
		}
	}
	@Test public void rodFlinchRequiresLogicallyClearedInteraction()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, ROD);
		assertEquals(NONE, tracker.observe(frame(1, ROD, false, true, false, true, false, 0, 0, 0)));
		assertFalse(tracker.isActive()); assertEquals(0, tracker.getAttempts());
		anchor(tracker, 2, ROD);
		assertEquals(ARMED, tracker.observe(frame(3, ROD, false, false, true, true, false, 0, 0, 0)));
		assertEquals(1, tracker.getTimer());
	}
	@Test public void primitiveCuesWithoutKnownTimerNeverInventAnchorOrFailure()
	{
		for (int primitive = 0; primitive < 4; primitive++)
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker();
			tracker.observe(frame(0, HARPOON, false, false, false, primitive == 0, primitive == 1, primitive == 2 ? 2 : 0, primitive == 3 ? 3 : 0, 0));
			tracker.observe(frame(1, HARPOON, true, true, false, false, false, 0, 0, 0));
			assertEquals(0, tracker.getAttempts()); assertFalse(tracker.isActive()); assertFalse(tracker.ownsFrame());
		}
	}
	@Test public void cueWhileTimerPositiveDoesNotResetIt()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		tracker.observe(frame(1, HARPOON, false, false, false, false, true, 0, 0, 0));
		assertEquals(NONE, tracker.observe(frame(2, HARPOON, false, false, false, true, true, 0, 0, 0)));
		assertEquals(1, tracker.getTimer());
		assertEquals(COUNTED, tracker.observe(frame(3, HARPOON, true, true, false, false, false, 0, 0, 0)));
	}
	@Test public void missingHarpoonInputExcludesThatRollAndLateKnownFlinchCanResume()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
		assertEquals(NONE, tracker.observe(frame(2, HARPOON, false, true, false, false, false, 0, 0, 0)));
		assertEquals(0, tracker.getAttempts());
		tracker.observe(frame(3, HARPOON, false, false, false, true, false, 0, 0, 0));
		assertEquals(COUNTED, tracker.observe(frame(4, HARPOON, true, true, false, false, false, 0, 0, 0)));
		assertEquals(1, tracker.getAttempts());
	}
	@Test public void missingRodContinuityStopsRatherThanInventingDueFailure()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, ROD);
		tracker.observe(frame(1, ROD, false, false, false, false, true, 0, 0, 0));
		tracker.observe(frame(2, ROD, true, true, false, false, false, 0, 0, 0));
		assertEquals(NONE, tracker.observe(frame(3, ROD, false, false, false, false, false, 0, 0, 0)));
		assertFalse(tracker.isAnchored()); assertEquals(0, tracker.getAttempts());
	}
	@Test public void contradictoryCatchWithdrawsOnlyCurrentContinuousSegment()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); tracker.restore(5, 7, 9);
		anchor(tracker, 0, HARPOON); tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
		tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0)); tracker.end();
		anchor(tracker, 3, HARPOON); tracker.observe(frame(4, HARPOON, false, false, false, true, false, 0, 0, 0));
		tracker.observe(frame(5, HARPOON, true, true, false, false, false, 0, 0, 1));
		assertEquals(INVALIDATED, tracker.observe(frame(6, HARPOON, true, true, false, false, false, 0, 0, 1)));
		assertEquals(5, tracker.getCatches()); assertEquals(8, tracker.getMinimumFailures()); assertEquals(10, tracker.getMaximumFailures());
		assertTrue(tracker.isAnchored()); assertFalse(tracker.isActive()); assertTrue(tracker.ownsFrame());
	}
	@Test public void ambiguousMultipleCatchesWithdrawCurrentSegment()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); tracker.restore(2, 3);
		anchor(tracker, 0, HARPOON); tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
		tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0));
		assertEquals(INVALIDATED, tracker.observe(frame(3, HARPOON, true, true, false, false, false, 0, 0, 2)));
		assertEquals(2, tracker.getCatches()); assertEquals(3, tracker.getFailures()); assertFalse(tracker.isAnchored());
	}
	@Test public void interruptionsAndUnorderedActionsPreservePriorCompletedTrials()
	{
		for (int reason = 0; reason < 5; reason++)
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
			tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
			tracker.observe(frame(2, HARPOON, true, true, false, false, false, 0, 0, 0));
			ManipulatedFishingTracker.Frame interrupted = new ManipulatedFishingTracker.Frame(3, reason == 1 ? UNSUPPORTED : HARPOON,
				reason != 0, reason == 4, false, false, false, reason == 3, reason == 4 ? 2 : 0, reason == 3 ? 3 : 0, reason == 2, 0);
			assertEquals(NONE, tracker.observe(interrupted)); assertEquals(1, tracker.getFailures()); assertFalse(tracker.isActive());
		}
	}
	@Test public void missingTicksRewindsAndFamilySwitchesNeedNewAnchor()
	{
		for (int reason = 0; reason < 3; reason++)
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
			tracker.observe(frame(1, HARPOON, false, false, false, true, false, 0, 0, 0));
			int tick = reason == 0 ? 1000 : reason == 1 ? 0 : 2;
			assertEquals(NONE, tracker.observe(frame(tick, reason == 2 ? ROD : HARPOON, true, true, false, false, false, 0, 0, 0)));
			assertEquals(0, tracker.getAttempts()); assertFalse(tracker.isAnchored());
		}
	}
	@Test public void freshFishingAndCombatCueInSameFrameDoNotAssumeTheirLogicalOrder()
	{
		for (int caught = 0; caught <= 1; caught++)
		{
			ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); tracker.restore(4, 6);
			anchor(tracker, 0, HARPOON);
			assertEquals(NONE, tracker.observe(frame(1, HARPOON, true, true, false, true, false, 0, 0, caught)));
			assertFalse(tracker.isAnchored()); assertFalse(tracker.isActive()); assertFalse(tracker.ownsFrame());
			assertEquals(4, tracker.getCatches()); assertEquals(6, tracker.getFailures());
		}
	}
	@Test public void restoredRangesPersistButTimerDoesNotResumeAndResetIsExplicit()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); tracker.restore(10, 2, 4);
		tracker.observe(frame(0, HARPOON, false, false, false, true, false, 0, 0, 0));
		tracker.observe(frame(1, HARPOON, true, true, false, false, false, 0, 0, 0));
		assertEquals(12, tracker.getMinimumAttempts()); assertEquals(14, tracker.getMaximumAttempts());
		anchor(tracker, 2, HARPOON); tracker.observe(frame(3, HARPOON, false, false, false, true, false, 0, 0, 0));
		tracker.observe(frame(4, HARPOON, true, true, false, false, false, 0, 0, 0)); tracker.discontinuity();
		assertEquals(10, tracker.getCatches()); assertEquals(3, tracker.getMinimumFailures()); assertEquals(5, tracker.getMaximumFailures());
		assertFalse(tracker.isAnchored()); assertFalse(tracker.ownsFrame()); tracker.reset(); assertEquals(0, tracker.getAttempts());
	}
	@Test public void duplicateFramesLongRunsAndCounterLimitDoNotOverflow()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); anchor(tracker, 0, HARPOON);
		for (int roll = 1; roll <= 10000; roll++)
		{
			tracker.observe(frame(roll * 2 - 1, HARPOON, false, false, false, true, false, 0, 0, 0));
			ManipulatedFishingTracker.Frame due = frame(roll * 2, HARPOON, true, true, false, false, false, 0, 0, 0);
			assertEquals(COUNTED, tracker.observe(due)); assertEquals(NONE, tracker.observe(due));
		}
		assertEquals(10000, tracker.getFailures()); tracker.restore(Long.MAX_VALUE - 1, 1);
		anchor(tracker, Integer.MAX_VALUE - 2, HARPOON);
		tracker.observe(frame(Integer.MAX_VALUE - 1, HARPOON, false, false, false, true, false, 0, 0, 0));
		assertEquals(NONE, tracker.observe(frame(Integer.MAX_VALUE, HARPOON, true, true, false, false, false, 0, 0, 1)));
		assertEquals(Long.MAX_VALUE, tracker.getAttempts()); assertFalse(tracker.isActive());
	}
	@Test public void invalidRestoredCountsLeaveExistingSampleUntouched()
	{
		ManipulatedFishingTracker tracker = new ManipulatedFishingTracker(); tracker.restore(1, 2, 3);
		for (long[] invalid : new long[][]{{-1, 0, 0}, {0, -1, 0}, {0, 2, 1}, {Long.MAX_VALUE, 0, 1}})
		{
			try { tracker.restore(invalid[0], invalid[1], invalid[2]); fail("Expected invalid sample"); }
			catch (IllegalArgumentException expected) { assertEquals(1, tracker.getCatches()); assertEquals(2, tracker.getMinimumFailures()); assertEquals(3, tracker.getMaximumFailures()); }
		}
	}
	private static void anchor(ManipulatedFishingTracker tracker, int tick, ManipulatedFishingTracker.Family family)
	{
		assertEquals(NONE, tracker.observe(frame(tick, family, family == HARPOON, true, false, false, false, 0, 0, 1)));
		assertEquals(0, tracker.getTimer());
	}
	private static ManipulatedFishingTracker.Frame frame(int tick, ManipulatedFishingTracker.Family family,
		boolean accepted, boolean continues, boolean cleared, boolean flinch, boolean production, int attack, int food, int caught)
	{
		return new ManipulatedFishingTracker.Frame(tick, family, true, accepted, continues, cleared, flinch, production, attack, food, false, caught);
	}
}
