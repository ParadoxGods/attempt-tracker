package com.attempttracker;

import java.util.Locale;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.util.Text;

/** Actual fishing activity, independent of whether its roll schedule is known. */
final class FishingActivityTracker
{
	private NPC spot;
	private WorldPoint spotPosition;
	private WorldPoint playerPosition;
	private boolean active;
	private int startedTick = -1;

	static boolean isFishingOption(String option)
	{
		if (option == null) { return false; }
		switch (Text.removeTags(option).trim().toLowerCase(Locale.ROOT))
		{
			case "fish": case "net": case "small net": case "big net": case "large net":
			case "bait": case "lure": case "harpoon": case "cage": case "use-rod":
				return true;
			default: return false;
		}
	}
	static boolean isFishingSpot(NPC npc)
	{
		if (npc == null) { return false; }
		if (npc.getId() == NpcID._0_40_34_MEMBERFISH || FishingSpot.findSpot(npc.getId()) != null) { return true; }
		String name = npc.getName();
		if (name == null || !Text.removeTags(name).toLowerCase(Locale.ROOT).contains("fishing spot")) { return false; }
		NPCComposition definition = npc.getTransformedComposition();
		if (definition == null || definition.getActions() == null) { return false; }
		for (String action : definition.getActions()) { if (isFishingOption(action)) { return true; } }
		return false;
	}
	void select(NPC npc)
	{
		if (!isFishingSpot(npc)) { stop(); return; }
		if (spot == npc && spotPosition != null && spotPosition.equals(npc.getWorldLocation())) { return; }
		stop(); spot = npc; spotPosition = npc.getWorldLocation();
	}
	boolean update(Player player, int tick)
	{
		if (spot == null || spotPosition == null || player == null) { stop(); return false; }
		Actor interaction = player.getInteracting();
		WorldPoint position = player.getWorldLocation();
		if (!spotPosition.equals(spot.getWorldLocation()) || player.getWorldView() != spot.getWorldView()
			|| position == null || position.getPlane() != spotPosition.getPlane()
			|| (interaction != null && interaction != spot)
			|| (active && playerPosition != null && !playerPosition.equals(position))) { stop(); return false; }
		boolean moving = playerPosition != null && !playerPosition.equals(position);
		playerPosition = position;
		boolean fishing = !moving && AnimationCatalog.skillFor(player.getAnimation()) == Skill.FISHING;
		// Aerial fishing intentionally targets remote pools. Other spots require adjacency.
		if (FishingSpot.findSpot(spot.getId()) != FishingSpot.COMMON_TENCH && position.distanceTo(spotPosition) > 1) { fishing = false; }
		if (!fishing) { if (active) { stop(); } return false; }
		if (!active) { startedTick = tick; active = true; }
		return true;
	}
	boolean isActive() { return active; }
	int getStartedTick() { return startedTick; }
	boolean targets(NPC npc) { return spot == npc; }
	void stop() { spot = null; spotPosition = null; playerPosition = null; active = false; startedTick = -1; }
}
