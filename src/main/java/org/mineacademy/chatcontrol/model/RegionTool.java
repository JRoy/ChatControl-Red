package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.menu.tool.Tool;
import org.mineacademy.fo.region.Region;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.visual.VisualTool;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents the tool used to create arena region for any arena
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RegionTool extends VisualTool {

	/**
	 * The singular tool instance
	 */
	@Getter
	private static final Tool instance = new RegionTool();

	/**
	 * @see org.mineacademy.fo.visual.VisualTool#getBlockName(org.bukkit.block.Block, org.bukkit.entity.Player)
	 */
	@Override
	protected String getBlockName(final Block block, final Player player) {
		return Lang.of("Commands.Region.Point_Name");
	}

	/**
	 * @see org.mineacademy.fo.visual.VisualTool#getBlockMask(org.bukkit.block.Block, org.bukkit.entity.Player)
	 */
	@Override
	protected CompMaterial getBlockMask(final Block block, final Player player) {
		return CompMaterial.EMERALD_BLOCK;
	}

	/**
	 * @see org.mineacademy.fo.menu.tool.Tool#getItem()
	 */
	@Override
	public ItemStack getItem() {
		return ItemCreator.of(
				CompMaterial.NETHERITE_AXE)
				.name(Lang.of("Commands.Region.Tool_Name"))
				.lores(Lang.ofList("Commands.Region.Tool_Lore"))
				.build().make();
	}

	/**
	 * @see org.mineacademy.fo.visual.VisualTool#handleBlockClick(org.bukkit.entity.Player, org.bukkit.event.inventory.ClickType, org.bukkit.block.Block)
	 */
	@Override
	protected void handleBlockClick(final Player player, final ClickType click, final Block block) {
		final SenderCache cache = SenderCache.from(player);
		final Location location = block.getLocation();
		final boolean primary = click == ClickType.LEFT;

		if (primary)
			cache.getCreatedRegion().setPrimary(location);
		else
			cache.getCreatedRegion().setSecondary(location);

		Messenger.success(player, Lang.ofScript("Commands.Region.Point_Set", SerializedMap.of("primary", primary)));
	}

	/**
	 * @see org.mineacademy.fo.visual.VisualTool#getVisualizedPoints(org.bukkit.entity.Player)
	 */
	@Override
	protected List<Location> getVisualizedPoints(Player player) {
		final List<Location> blocks = new ArrayList<>();
		final Region region = SenderCache.from(player).getCreatedRegion();

		if (region.getPrimary() != null)
			blocks.add(region.getPrimary());

		if (region.getSecondary() != null)
			blocks.add(region.getSecondary());

		return blocks;
	}

	/**
	 * @see org.mineacademy.fo.visual.VisualTool#getVisualizedRegion(org.bukkit.entity.Player)
	 */
	@Override
	protected VisualizedRegion getVisualizedRegion(Player player) {
		final VisualizedRegion region = SenderCache.from(player).getCreatedRegion();

		return region.isWhole() ? region : null;
	}

	/**
	 * Cancel the event so that we don't destroy blocks when selecting them
	 *
	 * @see org.mineacademy.fo.menu.tool.Tool#autoCancel()
	 */
	@Override
	protected boolean autoCancel() {
		return true;
	}
}
