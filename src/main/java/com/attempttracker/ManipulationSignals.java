package com.attempttracker;

import com.attempttracker.core.ManipulatedFishingTracker.Family;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemManager;

/** Public SDK evidence for shared-timer actions; input alone never confirms acceptance. */
final class ManipulationSignals
{
	private ManipulationSignals() { }

	/** Only call on a fresh server animation change, never on a looping frame. */
	static boolean productionResetAnimation(int animation)
	{
		return animation == AnimationID.HUMAN_SALAMANDER_TAR_GRIND
			|| animation == AnimationID.HUMAN_FLETCHING || animation == AnimationID.HUMAN_FLETCHING_SINGLE;
	}

	/** The known direct three-tick recipes. Making limbs, bows or darts is not inferred. */
	static boolean productionPair(int source, int target)
	{
		return tarPair(source, target) || stockPair(source, target);
	}

	static boolean productionResetAnimation(int animation, int source, int target)
	{
		if (animation == AnimationID.HUMAN_SALAMANDER_TAR_GRIND) { return tarPair(source, target); }
		return (animation == AnimationID.HUMAN_FLETCHING || animation == AnimationID.HUMAN_FLETCHING_SINGLE)
			&& stockPair(source, target);
	}

	private static boolean tarPair(int source, int target)
	{
		return source == ItemID.SWAMP_TAR && tarHerb(target) || target == ItemID.SWAMP_TAR && tarHerb(source);
	}

	private static boolean tarHerb(int id)
	{
		return id == ItemID.GUAM_LEAF || id == ItemID.MARENTILL || id == ItemID.TARROMIN || id == ItemID.HARRALANDER;
	}

	private static boolean stockPair(int source, int target)
	{
		return source == ItemID.KNIFE && stockLog(target) || target == ItemID.KNIFE && stockLog(source);
	}

	private static boolean stockLog(int id) { return id == ItemID.TEAK_LOGS || id == ItemID.MAHOGANY_LOGS; }

	/** Selected item ID captured during input dispatch, or -1 when unavailable/consumed. */
	static int selectedUseItem(Client client, MenuOptionClicked event)
	{
		if (client == null || !itemUse(event) || !client.isWidgetSelected()) { return -1; }
		Widget widget = client.getSelectedWidget();
		return widget == null || widget.getItemId() <= 0 ? -1 : widget.getItemId();
	}

	static int selectedUseTargetItem(MenuOptionClicked event)
	{
		if (!itemUse(event)) { return -1; }
		if (event.getItemId() > 0) { return event.getItemId(); }
		Widget widget = event.getWidget();
		return widget == null || widget.getItemId() <= 0 ? -1 : widget.getItemId();
	}

	private static boolean itemUse(MenuOptionClicked event)
	{
		return event != null && event.getMenuEntry() != null && !event.isConsumed()
			&& (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET || event.getMenuAction() == MenuAction.ITEM_USE_ON_ITEM);
	}

	/** Requires independently confirmed consumption; Eat input/animation alone is insufficient. */
	static int foodDelay(int itemId)
	{
		switch (itemId)
		{
			case ItemID.TBWT_COOKED_KARAMBWAN: case ItemID.BLIGHTED_KARAMBWAN: return 2;
			case ItemID.BRUT_ROE: case ItemID.BRUT_CAVIAR:
			case ItemID.SHRIMP: case ItemID.ANCHOVIES: case ItemID.SARDINE: case ItemID.HERRING:
			case ItemID.TROUT: case ItemID.SALMON: case ItemID.PIKE: case ItemID.COD: case ItemID.TUNA:
			case ItemID.BASS: case ItemID.LOBSTER: case ItemID.SWORDFISH: case ItemID.SHARK:
			case ItemID.MANTARAY: case ItemID.SEATURTLE: case ItemID.MONKFISH: case ItemID.ANGLERFISH:
			case ItemID.DARK_CRAB: case ItemID.BREAD: return 3;
			default: return 0;
		}
	}

	/** A freshly assigned real attack animation plus a combat target, never generic non-fishing. */
	static int outgoingAttackSpeed(Player player, Client client, ItemManager itemManager)
	{
		if (player == null || !attackAnimation(player.getAnimation()) || !combatTarget(player.getInteracting(), player)) { return 0; }
		return TwoTickFishingSignals.effectiveWeaponSpeed(client, itemManager);
	}

	static boolean attackAnimation(int animation)
	{
		switch (animation)
		{
			case AnimationID.HUMAN_THROW: case AnimationID.II_HUMAN_DART_THROW: case AnimationID.II_HUMAN_DART_THROW_PVN:
			case AnimationID.HUMAN_BOW: case AnimationID.HUMAN_CROSSBOW:
			case AnimationID.HUMAN_UNARMEDPUNCH: case AnimationID.HUMAN_UNARMEDKICK:
			case AnimationID.HUMAN_SWORD_STAB: case AnimationID.HUMAN_SWORD_SLASH:
			case AnimationID.HUMAN_SWORD_TRANSSLASH: case AnimationID.HUMAN_SWORD_LUNGE:
			case AnimationID.HUMAN_AXE_CHOP: case AnimationID.HUMAN_TRANS_AXE_CHOP:
			case AnimationID.HUMAN_AXE_HACK: case AnimationID.HUMAN_AXE_SMASH:
			case AnimationID.HUMAN_BLUNT_SPIKE: case AnimationID.HUMAN_BLUNT_POUND: case AnimationID.HUMAN_BLUNT_PUMMEL:
			case AnimationID.HUMAN_DHSWORD_STAB: case AnimationID.HUMAN_DHSWORD_CHOP:
			case AnimationID.HUMAN_DHSWORD_SLASH: case AnimationID.HUMAN_DHSWORD_LUNGE:
			case AnimationID.HUMAN_STAFF_SPIKE: case AnimationID.HUMAN_STAFF_POUND: case AnimationID.HUMAN_STAFF_PUMMEL:
			case AnimationID.HUMAN_STAFFORB_SPIKE: case AnimationID.HUMAN_STAFFORB_POUND: case AnimationID.HUMAN_STAFFORB_PUMMEL:
			case AnimationID.HUMAN_DSPEAR_SLASH: case AnimationID.HUMAN_DSPEAR_STAB: case AnimationID.HUMAN_DSPEAR_LUNGE:
			case AnimationID.HUMAN_SPEAR_SPIKE: case AnimationID.HUMAN_SPEAR_LUNGE:
			case AnimationID.HUMAN_DDAGGER_LUNGE: case AnimationID.HUMAN_DDAGGER_HACK:
			case AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK: return true;
			default: return false;
		}
	}

	private static boolean combatTarget(Actor target, Player local)
	{
		if (target instanceof Player) { return target != local; }
		if (!(target instanceof NPC)) { return false; }
		NPC npc = (NPC) target;
		if (FishingSpot.findSpot(npc.getId()) != null || TwoTickFishingSignals.supportedSpot(npc)) { return false; }
		NPCComposition composition = npc.getTransformedComposition();
		String[] actions = composition == null ? null : composition.getActions();
		if (actions == null) { return false; }
		for (String action : actions) { if ("Attack".equalsIgnoreCase(action)) { return true; } }
		return false;
	}

	/** Only the documented shared-timer families; unknown rods/net/eel methods are excluded. */
	static Family family(NPC spot, int acceptedAnimation) { return fishingFamily(spot, acceptedAnimation); }

	static Family fishingFamily(NPC spot, int acceptedAnimation)
	{
		if (spot == null || AnimationCatalog.skillFor(acceptedAnimation) != Skill.FISHING)
		{
			return Family.UNSUPPORTED;
		}
		if (TwoTickFishingSignals.supportedSpot(spot) && AnimationCatalog.isHarpoon(acceptedAnimation))
		{
			return Family.HARPOON;
		}
		FishingSpot method = FishingSpot.findSpot(spot.getId());
		if ((method == FishingSpot.SALMON || method == FishingSpot.BARB_FISH) && rodAnimation(acceptedAnimation))
		{
			return Family.ROD;
		}
		return Family.UNSUPPORTED;
	}

	private static boolean rodAnimation(int animation)
	{
		switch (animation)
		{
			case AnimationID.HUMAN_FISHING_CASTING: case AnimationID.HUMAN_FISH_ONSPOT:
			case AnimationID.HUMAN_FISHING_CASTING_BRUT: case AnimationID.HUMAN_FISHING_ONSPOT_BRUT:
			case AnimationID.HUMAN_FISHING_CASTING_PEARL: case AnimationID.HUMAN_FISH_ONSPOT_PEARL:
			case AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY: case AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY:
			case AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT: case AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT: return true;
			default: return false;
		}
	}
}
