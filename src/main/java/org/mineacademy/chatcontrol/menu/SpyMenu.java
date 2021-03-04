package org.mineacademy.chatcontrol.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Spy.Type;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.menu.Menu;
import org.mineacademy.fo.menu.MenuPagged;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.button.ButtonMenu;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;

/**
 * Menu for spying control panel
 */
public final class SpyMenu extends Menu {

	/**
	 * The player we are editing spying settings for
	 */
	private final PlayerCache cache;
	private final List<Button> spyButtons = new ArrayList<>();
	private final Button toggleButton;

	/*
	 * Initiate this menu for the given player cache,
	 * so that admins can change spying settings for
	 * other players, too.
	 */
	private SpyMenu(PlayerCache cache) {
		this.cache = cache;

		setTitle("Spying Menu");
		setSize(9 * 2);

		this.registerSpyButtons();

		this.toggleButton = Button.makeSimple(ItemCreator
				.of(this.cache.isSpyingSomething() ? CompMaterial.LAVA_BUCKET : CompMaterial.BUCKET)
				.name(Lang.ofScript("Menu.Spy.Toggle_Title", SerializedMap.of("spying", this.cache.isSpyingSomething())))
				.lores(Lang.ofList("Menu.Spy.Toggle_Lore")), player -> {

					if (this.cache.isSpyingSomething())
						this.cache.setSpyingOff();
					else
						this.cache.setSpyingOn();

					newInstance().displayTo(player);
				});
	}

	private void registerSpyButtons() {
		this.spyButtons.clear();

		if (canSpy(Type.CHAT)) {
			final List<String> lore = new ArrayList<>();

			lore.addAll(Lang.ofList("Menu.Spy.Button_Lore_1"));

			if (!this.cache.getSpyingChannels().isEmpty()) {
				lore.addAll(Lang.ofList("Menu.Spy.Button_Lore_Channels"));

				for (final String channelName : this.cache.getSpyingChannels())
					lore.add(" - " + channelName);
			}

			this.spyButtons.add(new ButtonMenu(ChannelsMenu::new, ItemCreator.of(
					CompMaterial.PLAYER_HEAD,
					Lang.of("Menu.Spy.Button_Title"))
					.lores(lore)));
		}

		addClickButton(Spy.Type.COMMAND, CompMaterial.COMMAND_BLOCK, Lang.of("Menu.Spy.Type_Commands"));
		addClickButton(Spy.Type.PRIVATE_MESSAGE, CompMaterial.PAPER, Lang.of("Menu.Spy.Type_Private_Messages"));
		addClickButton(Spy.Type.MAIL, CompMaterial.BOOK, Lang.of("Menu.Spy.Type_Mail"));
		addClickButton(Spy.Type.SIGN, CompMaterial.SIGN, Lang.of("Menu.Spy.Type_Sign"));
		addClickButton(Spy.Type.BOOK, CompMaterial.WRITABLE_BOOK, Lang.of("Menu.Spy.Type_Book"));
		addClickButton(Spy.Type.ANVIL, CompMaterial.ANVIL, Lang.of("Menu.Spy.Type_Anvil"));
	}

	private void addClickButton(Spy.Type type, CompMaterial material, String label) {
		if (!this.canSpy(type))
			return;

		this.spyButtons.add(Button.makeSimple(ItemCreator
				.of(material)
				.name(WordUtils.capitalizeFully(type.getLocalized()))
				.lores(Arrays.asList(Lang.of("Menu.Spy.Button_Toggle_Lore")
						.replace("{label}", label)
						.replace("{status}", Lang.ofScript("Menu.Spy.Status", SerializedMap.of("spying", this.cache.isSpying(type))))
						.split("\n")))
				.glow(this.cache.isSpying(type)),

				player -> {
					final boolean isSpying = this.cache.isSpying(type);

					this.cache.setSpying(type, !isSpying);
					this.registerSpyButtons();

					restartMenu(Lang
							.ofScript("Menu.Spy.Status_Toggle", SerializedMap.of("isSpying", isSpying))
							.replace("{type}", type.getLocalized()));
				}));

	}

	private boolean canSpy(Spy.Type type) {
		return Settings.Spy.APPLY_ON.contains(type);
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getButtonsToAutoRegister()
	 */
	@Override
	protected List<Button> getButtonsToAutoRegister() {
		return this.spyButtons;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getItemAt(int)
	 */
	@Override
	public ItemStack getItemAt(int slot) {

		if (this.spyButtons.size() > slot)
			return this.spyButtons.get(slot).getItem();

		if (slot == getSize() - 2)
			return this.toggleButton.getItem();

		return null;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfoButtonPosition()
	 */
	@Override
	protected int getInfoButtonPosition() {
		return getSize() - 1;
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#getInfo()
	 */
	@Override
	protected String[] getInfo() {
		return Lang.of("Menu.Spy.Help")
				.replace("{label}", Settings.MAIN_COMMAND_ALIASES.get(0))
				.split("\n");
	}

	/**
	 * @see org.mineacademy.fo.menu.Menu#newInstance()
	 */
	@Override
	public Menu newInstance() {
		return new SpyMenu(this.cache);
	}

	/**
	 * Show this menu to the given player with the given player data
	 *
	 * @param forWhom
	 * @param player
	 */
	public static void showTo(PlayerCache forWhom, Player player) {
		new SpyMenu(forWhom).displayTo(player);
	}

	/* ------------------------------------------------------------------------------- */
	/* Subclasses */
	/* ------------------------------------------------------------------------------- */

	private final class ChannelsMenu extends MenuPagged<Channel> {

		private int colorIndex = 0;

		public ChannelsMenu() {
			super(SpyMenu.this, Channel.getChannels(), true);
		}

		/**
		 * @see org.mineacademy.fo.menu.MenuPagged#convertToItemStack(java.lang.Object)
		 */
		@Override
		protected ItemStack convertToItemStack(Channel item) {
			final CompColor[] colors = CompColor.values();

			if (this.colorIndex-- <= 0)
				this.colorIndex = colors.length - 1;

			return ItemCreator
					.of(CompMaterial.WHITE_WOOL)
					.name(item.getName())
					.lores(Arrays.asList(Lang
							.of("Menu.Spy.Channels_Toggle_Lore")
							.replace("{status}", Lang
									.ofScript("Menu.Spy.Status",
											SerializedMap.of("spying", cache.isSpyingChannel(item))))
							.split("\n")))

					.glow(cache.isSpyingChannel(item))
					.color(colors[this.colorIndex]).build().make();

		}

		/**
		 * @see org.mineacademy.fo.menu.MenuPagged#onPageClick(org.bukkit.entity.Player, java.lang.Object, org.bukkit.event.inventory.ClickType)
		 */
		@Override
		protected void onPageClick(Player player, Channel item, ClickType click) {
			final boolean isSpying = cache.isSpyingChannel(item);

			cache.setSpyingChannel(item, !isSpying);
			animateTitle(Lang.ofScript("Menu.Spy.Channels_Toggle", SerializedMap.of("isSpying", isSpying)));
		}
	}
}
