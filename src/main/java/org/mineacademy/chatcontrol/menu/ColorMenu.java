package org.mineacademy.chatcontrol.menu;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.model.Colors;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.MenuPagged;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompChatColor;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;

/**
 * Represents a way for players to self-manage their own colors
 */
public final class ColorMenu extends MenuPagged<CompChatColor> {

	private final PlayerCache cache;
	private final List<Button> decorationButtons = new ArrayList<>();
	private final Button resetColorButton;
	private final Button resetDecorationButton;
	private final Button emptyButton;

	/*
	 * Create a new color menu
	 */
	private ColorMenu(Player player) {
		super(9 * 3, Colors.getGuiColorsForPermission(player));

		this.cache = PlayerCache.from(player);
		this.loadDecorations(player);

		if (super.getPages().get(0).isEmpty() && this.decorationButtons.isEmpty()) {
			this.resetColorButton = Button.makeEmpty();
			this.resetDecorationButton = Button.makeEmpty();

			this.emptyButton = Button.makeDummy(ItemCreator.of(
					CompMaterial.BUCKET)
					.name(Lang.of("Menu.Color.Button_Empty_Title"))
					.lores(Lang.ofList("Menu.Color.Button_Empty_Lore")));

			return;
		}

		this.resetColorButton = Button.makeSimple(ItemCreator.of(CompMaterial.GLASS, Lang.of("Menu.Color.Button_Reset_Color_Title")), clicker -> {
			this.cache.setChatColor(null);

			restartMenu(Lang.of("Menu.Color.Color_Reset"));
		});

		this.resetDecorationButton = Button.makeSimple(ItemCreator.of(CompMaterial.BEACON, Lang.of("Menu.Color.Button_Reset_Decoration")), clicker -> {
			this.cache.setChatDecoration(null);
			this.loadDecorations(player);

			restartMenu(Lang.of("Menu.Color.Decoration_Reset"));
		});

		this.emptyButton = Button.makeEmpty();
	}

	/*
	 * Load decorations the player can obtain
	 */
	private void loadDecorations(Player player) {
		this.decorationButtons.clear();

		// Fill in random colors for decoration
		int index = 0;

		for (final CompChatColor decoration : Colors.getGuiDecorationsForPermission(player)) {
			this.decorationButtons.add(Button.makeSimple(ItemCreator.of(
					CompMaterial.WHITE_CARPET)
					.name(ItemUtil.bountifyCapitalized(decoration) + Lang.of("Menu.Color.Button_Decoration_Title"))
					.lores(Lang.ofList("Menu.Color.Button_Decoration_Lore", decoration.toString()))
					.color(CompColor.values()[index++])
					.glow(this.cache.getChatDecoration() == decoration),
					clicker -> {
						this.cache.setChatDecoration(decoration);
						this.loadDecorations(player);

						restartMenu(Lang.of("Menu.Color.Decoration_Set", ItemUtil.bountifyCapitalized(decoration)));
					}));
		}
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPagged#convertToItemStack(java.lang.Object)
	 */
	@Override
	protected ItemStack convertToItemStack(CompChatColor color) {
		return ItemCreator
				.of(CompMaterial.WHITE_WOOL)
				.name(ItemUtil.bountifyCapitalized(color) + Lang.of("Menu.Color.Button_Color_Title"))
				.lores(Lang.ofList("Menu.Color.Button_Color_Lore", color.toString()))
				.color(CompColor.fromChatColor(color))
				.glow(this.cache.getChatColor() == color)
				.build().make();
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getButtonsToAutoRegister()
	 */
	@Override
	protected List<Button> getButtonsToAutoRegister() {
		return this.decorationButtons;
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPagged#getItemAt(int)
	 */
	@Override
	public ItemStack getItemAt(int slot) {

		if (slot == getCenterSlot() && !CompMaterial.isAir(this.emptyButton.getItem().getType()))
			return this.emptyButton.getItem();

		if (slot >= 9 * 3 && slot - 9 * 3 < this.decorationButtons.size())
			return this.decorationButtons.get(slot - 9 * 3).getItem();

		if (slot == getSize() - 2)
			return this.resetDecorationButton.getItem();

		if (slot == getSize() - 1)
			return this.resetColorButton.getItem();

		return super.getItemAt(slot);
	}

	/**
	 * @see org.mineacademy.fo.menu.MenuPagged#onPageClick(org.bukkit.entity.Player, java.lang.Object, org.bukkit.event.inventory.ClickType)
	 */
	@Override
	protected void onPageClick(Player player, CompChatColor item, ClickType click) {
		this.cache.setChatColor(item);

		restartMenu(Lang.of("Menu.Color.Color_Set", ItemUtil.bountifyCapitalized(item)));
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfoButtonPosition()
	 */
	@Override
	protected int getInfoButtonPosition() {
		return 9 * 3 - 1;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return Lang.of("Menu.Color.Help")
				.replace("{color}", this.cache.hasChatColor() ? this.cache.getChatColor().toReadableString() : Lang.of("None"))
				.replace("{decoration}", this.cache.hasChatDecoration() ? this.cache.getChatDecoration() + ItemUtil.bountifyCapitalized(this.cache.getChatDecoration()) : Lang.of("None"))
				.split("\n");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#newInstance()
	 */
	@Override
	public Menu newInstance() {
		return new ColorMenu(this.getViewer());
	}

	/**
	 * Show this menu to the given player
	 *
	 * @param player
	 */
	public static void showTo(Player player) {
		new ColorMenu(player).displayTo(player);
	}
}
