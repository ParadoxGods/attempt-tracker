package com.attempttracker;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.Text;

/** Public SDK observations used by the attack-assisted harpoon timing model. */
final class TwoTickFishingSignals
{
	private TwoTickFishingSignals() { }

	/**
	 * Combat-looking hitsplats only. The SDK does not expose a hit's cause or
	 * attacker, so ordinary red self-damage must also be excluded by action/context
	 * guards in the detector. A hitsplat by itself never proves a flinch or roll.
	 */
	static boolean combatHitsplat(Hitsplat hitsplat)
	{
		if (hitsplat == null || hitsplat.getAmount() < 0) { return false; }
		switch (hitsplat.getHitsplatType())
		{
			case HitsplatID.BLOCK_ME: case HitsplatID.BLOCK_OTHER:
			case HitsplatID.DAMAGE_ME: case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_ME_CYAN: case HitsplatID.DAMAGE_OTHER_CYAN:
			case HitsplatID.DAMAGE_ME_ORANGE: case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_ME_YELLOW: case HitsplatID.DAMAGE_OTHER_YELLOW:
			case HitsplatID.DAMAGE_ME_WHITE: case HitsplatID.DAMAGE_OTHER_WHITE:
			case HitsplatID.DAMAGE_ME_POISE: case HitsplatID.DAMAGE_OTHER_POISE:
			case HitsplatID.DAMAGE_MAX_ME: case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE: case HitsplatID.DAMAGE_MAX_ME_YELLOW:
			case HitsplatID.DAMAGE_MAX_ME_WHITE: case HitsplatID.DAMAGE_MAX_ME_POISE:
				return true;
			default: return false;
		}
	}

	static boolean autoRetaliateEnabled(Client client)
	{
		return client != null && client.getVarpValue(VarPlayerID.OPTION_NODEF) == 0;
	}

	/** A no-movement clearing input cue, never proof of server acceptance or a roll. */
	static boolean sameTileWalk(Player player, MenuOptionClicked event)
	{
		if (player == null || event == null || event.isConsumed() || event.getMenuAction() != MenuAction.WALK
			|| event.getMenuEntry() == null) { return false; }
		WorldView worldView = player.getWorldView();
		WorldPoint position = player.getWorldLocation();
		if (worldView == null || position == null || event.getMenuEntry().getWorldViewId() != worldView.getId()
			|| position.getPlane() != worldView.getPlane()) { return false; }
		int sceneX = event.getParam0(), sceneY = event.getParam1();
		if (sceneX < 0 || sceneY < 0 || sceneX >= worldView.getSizeX() || sceneY >= worldView.getSizeY()) { return false; }
		return position.equals(WorldPoint.fromScene(worldView, sceneX, sceneY, worldView.getPlane()));
	}

	/** Returns 0 when public item/style data is unavailable or unrecognized. */
	static int effectiveWeaponSpeed(Client client, ItemManager itemManager)
	{
		if (client == null || itemManager == null) { return 0; }
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null) { return 0; }
		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null || weapon.getId() < 0) { return 4; }
		ItemStats stats = itemManager.getItemStats(weapon.getId());
		ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
		if (equipment == null || equipment.getAspeed() <= 0) { return 0; }
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		int weaponCategory = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		EnumComposition categories = client.getEnum(EnumID.WEAPON_STYLES);
		if (categories == null) { return 0; }
		int stylesId = categories.getIntValue(weaponCategory);
		if (stylesId <= 0) { return 0; }
		EnumComposition styles = client.getEnum(stylesId);
		int[] structs = styles == null ? null : styles.getIntVals();
		if (structs == null || styleIndex < 0 || styleIndex >= structs.length || structs[styleIndex] <= 0) { return 0; }
		StructComposition style = client.getStructComposition(structs[styleIndex]);
		String trainingStyle = style == null ? null : style.getStringValue(ParamID.ATTACK_STYLE_NAME);
		if (trainingStyle == null) { return 0; }
		trainingStyle = Text.removeTags(trainingStyle).trim().toLowerCase(Locale.ROOT);
		int baseSpeed = equipment.getAspeed();
		switch (trainingStyle)
		{
			case "ranging":
				// ATTACK_STYLE_NAME denotes XP training, so Accurate and Rapid both
				// say Ranging. Only these standard ranged categories have Rapid at
				// COM_MODE 1; notably salamanders also have Ranging at 1 without Rapid.
				if (!standardRangedCategory(weaponCategory)) { return 0; }
				if (styleIndex == 0) { return baseSpeed; }
				return styleIndex == 1 && baseSpeed > 1 ? baseSpeed - 1 : 0;
			case "longrange":
				return standardRangedCategory(weaponCategory) && styleIndex == 3 ? baseSpeed : 0;
			case "accurate": case "aggressive": case "controlled": case "defensive":
				return standardRangedCategory(weaponCategory) ? 0 : baseSpeed;
			// Spell casting has its own speed rules; equipment speed is insufficient.
			default: return 0;
		}
	}

	private static boolean standardRangedCategory(int category)
	{
		return category == 3 || category == 5 || category == 7 || category == 19;
	}

	/** Harpoon methods whose first interaction may complete a shared-timer roll. */
	static boolean supportedSpot(NPC spot)
	{
		if (spot == null) { return false; }
		FishingSpot method = FishingSpot.findSpot(spot.getId());
		return spot.getId() == NpcID._0_40_34_MEMBERFISH || method == FishingSpot.SHARK || method == FishingSpot.LOBSTER;
	}

	static boolean supportedHarpoon(Player player, NPC spot)
	{
		if (player == null || !supportedSpot(spot) || !AnimationCatalog.isHarpoon(player.getAnimation())
			|| player.getWorldView() == null || player.getWorldView() != spot.getWorldView()) { return false; }
		WorldPoint playerPosition = player.getWorldLocation();
		WorldPoint spotPosition = spot.getWorldLocation();
		return playerPosition != null && spotPosition != null && playerPosition.getPlane() == spotPosition.getPlane()
			&& playerPosition.distanceTo(spotPosition) <= 1;
	}
}
