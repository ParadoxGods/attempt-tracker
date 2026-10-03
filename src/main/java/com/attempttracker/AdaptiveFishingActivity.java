package com.attempttracker;

import java.util.ArrayDeque;
import java.util.Deque;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.game.FishingSpot;

/** Fishing engagement through short combat/item manipulation transitions. */
final class AdaptiveFishingActivity
{
	private NPC spot;
	private WorldPoint spotPosition, playerPosition;
	private int lastFishing = -100, lastManipulation = -100, lastAccepted = -1;
	private int latestRhythm;
	private boolean harpooning;
	private final Deque<Integer> rhythms = new ArrayDeque<>();

	void select(NPC npc, int tick)
	{
		if (!FishingActivityTracker.isFishingSpot(npc)) { return; }
		if (spot != npc || spotPosition == null || !spotPosition.equals(npc.getWorldLocation())) { stop(); spot = npc; spotPosition = npc.getWorldLocation(); }
	}
	void accepted(int tick)
	{
		if (lastAccepted == tick) { return; }
		if (lastAccepted >= 0 && tick > lastAccepted)
		{
			int gap = tick - lastAccepted;
			if (gap <= 12) { rhythms.addLast(gap); if (rhythms.size() > 8) { rhythms.removeFirst(); } }
			else { rhythms.clear(); }
			latestRhythm = 0;
			if (rhythms.size() >= 3)
			{
				int count = 0; for (int value : rhythms) { if (value == gap) { count++; } }
				if (count >= 3) { latestRhythm = gap; }
			}
		}
		lastAccepted = tick;
	}
	void manipulation(int tick) { lastManipulation = tick; }
	boolean observe(Player player, int tick)
	{
		if (player == null || spot == null || spotPosition == null || !spotPosition.equals(spot.getWorldLocation())
			|| player.getWorldView() != spot.getWorldView()) { stop(); return false; }
		WorldPoint position = player.getWorldLocation();
		if (position == null || position.getPlane() != spotPosition.getPlane()
			|| (lastFishing >= 0 && playerPosition != null && !playerPosition.equals(position))) { stop(); return false; }
		playerPosition = position;
		boolean remote = FishingSpot.findSpot(spot.getId()) == FishingSpot.COMMON_TENCH;
		if (!remote && position.distanceTo(spotPosition) > 1) { return false; }
		boolean fishing = AnimationCatalog.skillFor(player.getAnimation()) == Skill.FISHING
			&& (player.getInteracting() == null || player.getInteracting() == spot);
		if (fishing) { lastFishing = tick; harpooning = AnimationCatalog.isHarpoon(player.getAnimation()); return true; }
		// A cue can bridge a short transition, but cannot start fishing by itself
		// or extrapolate indefinitely from clicks, attacks, or an old animation.
		return tick >= lastFishing && tick - lastFishing <= 2
			&& tick >= lastManipulation && tick - lastManipulation <= 2;
	}
	boolean targets(NPC npc) { return spot == npc; }
	boolean isSharkMethod() { return spot != null && harpooning && (FishingSpot.findSpot(spot.getId()) == FishingSpot.SHARK || spot.getId() == NpcID._0_40_34_MEMBERFISH); }
	boolean canBridge(int tick) { return tick >= lastFishing && tick - lastFishing <= 2 && tick >= lastManipulation && tick - lastManipulation <= 2; }
	String rhythm() { return latestRhythm == 0 ? "Unverified rhythm" : latestRhythm + "t interactions (rolls unverified)"; }
	void stop()
	{
		spot = null; spotPosition = playerPosition = null; lastFishing = lastManipulation = -100;
		lastAccepted = -1; latestRhythm = 0; harpooning = false; rhythms.clear();
	}
}
