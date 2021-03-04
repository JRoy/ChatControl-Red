package org.mineacademy.chatcontrol.listener;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Database;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Mail.Recipient;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Newcomer;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.RuleCheck;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.AntiBot;
import org.mineacademy.chatcontrol.settings.Settings.Integration;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The general listener for player events
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerListener implements Listener {

	/**
	 * The singleton instance
	 */
	@Getter
	private static final PlayerListener instance = new PlayerListener();

	/**
	 * Listen for pre-login and handle antibot logic
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		final String playerName = event.getName();
		final UUID uniqueId = event.getUniqueId();
		final SenderCache cache = SenderCache.from(playerName);
		final OfflinePlayer offline = Remain.getOfflinePlayerByUUID(uniqueId);

		// Disallowed usernames
		if (AntiBot.DISALLOWED_USERNAMES.isInListRegex(playerName) && (offline == null || !HookManager.hasVaultPermission(offline, Permissions.Bypass.LOGIN_USERNAMES))) {
			event.setKickMessage(Common.colorize(Lang.of("Player.Kick_Disallowed_Nickname")));
			event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);

			return;
		}

		// Login delay
		if (offline == null || !HookManager.hasVaultPermission(offline, Permissions.Bypass.LOGIN_DELAY)) {
			final long now = System.currentTimeMillis();
			final long lastLoginPeriod = (now - cache.getLastLogin()) / 1000;
			final long delay = AntiBot.COOLDOWN_REJOIN.getForUUID(uniqueId).getTimeSeconds();

			if (cache.getLastLogin() != -1 && lastLoginPeriod < delay) {
				event.setKickMessage(Common.colorize(Lang.of("Player.Kick_Relogin", delay - lastLoginPeriod)));
				event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
			}

			// Allow login and cache the time
			else
				cache.setLastLogin(now);
		}
	}

	/**
	 * Listen for join events and perform plugin logic
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		final UUID uniqueId = player.getUniqueId();
		final ServerCache serverCache = ServerCache.getInstance();
		final SenderCache senderCache = SenderCache.from(player);

		senderCache.setPendingJoin(true);

		// Reset the flag back for antibot
		senderCache.setMovedFromJoin(false);

		// Disable Bukkit message if we handle that
		if (Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.JOIN))
			event.setJoinMessage(null);

		// Moves MySQL off of the main thread
		// Delays the execution so that, if player comes from another server,
		// his data is saved first in case database has slower connection than us
		Database.getInstance().loadCache(player, cache -> {

			// Update join location
			senderCache.setJoinLocation(player.getLocation());

			// Give permissions early so we can use them already below
			if (Newcomer.isNewcomer(player))
				Newcomer.givePermissions(player);

			// Update tablist name from nick
			Players.setTablistName(player);

			// Remove old channels over limit
			cache.checkLimits(player);

			// Auto join channels
			if (Settings.Channels.ENABLED) {
				final int limitRead = Settings.Channels.MAX_READ_CHANNELS.getFor(player);

				for (final Channel channel : Channel.getChannels()) {
					final Channel.Mode oldMode = channel.getChannelMode(player);

					if (cache.hasLeftChannel(channel) && Settings.Channels.IGNORE_AUTOJOIN_IF_LEFT) {
						Log.logTip("TIP: Not joining " + player.getName() + " to channel " + channel.getName() + " because he left it manually");

						continue;
					}

					for (final Channel.Mode mode : Channel.Mode.values()) {

						// Channel mode over limit
						if ((mode == Mode.WRITE && cache.getWriteChannel() != null) || (mode == Mode.READ && cache.getChannels(Mode.READ).size() >= limitRead))
							continue;

						// Permission to autojoin channels
						final String autoJoinPermission = Permissions.Channel.AUTO_JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey());
						final String joinPermission = Permissions.Channel.JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey());

						if (PlayerUtil.hasPerm(player, autoJoinPermission)) {

							if (mode == Mode.WRITE && oldMode == Mode.READ) {
								// Allow autojoin override from read to write
								cache.updateChannelMode(channel, null);

							} else if (oldMode != null)

								// Else disallow autojoin if player is already in channel
								continue;

							if (!PlayerUtil.hasPerm(player, joinPermission)) {
								Log.logTip("TIP Warning: Player " + player.getName() + " had " + autoJoinPermission + " but lacked " + joinPermission
										+ " so he won't be added to the '" + channel.getName() + "' channel!");

								continue;
							}

							Log.logTip("TIP: Joining " + player.getName() + " to channel " + channel.getName() + " in mode " + mode + " due to '"
									+ autoJoinPermission + "' permission." + (Settings.Channels.IGNORE_AUTOJOIN_IF_LEFT ? " We won't join him again when he leaves channel manually." : ""));

							channel.joinPlayer(player, mode);

							break;
						}
					}
				}
			}

			// Motd
			if (Settings.Motd.ENABLED)
				Players.showMotd(player, true);

			// Spying
			if (PlayerUtil.hasPerm(player, Permissions.Spy.AUTO_ENABLE)) {
				cache.setSpyingOn();

				SimpleComponent
						.of(Variables.replace(Lang.of("Commands.Spy.Auto_Enable_1"), player))
						.append(Variables.replace(Lang.of("Commands.Spy.Auto_Enable_2"), player))
						.onHover(Variables.replace(Lang.of("Commands.Spy.Auto_Enable_Tooltip", Permissions.Spy.AUTO_ENABLE), player))
						.send(player);

				Log.logOnce("spy-autojoin", "TIP: Automatically enabling spy mode for " + player.getName() + " because he had '" + Permissions.Spy.AUTO_ENABLE + "'"
						+ " permission. To stop automatically enabling spy mode for players, give them negative '" + Permissions.Spy.AUTO_ENABLE + "' permission"
						+ " (a value of false when using LuckPerms).");
			}

			// Unread mail notification
			if (Settings.Mail.ENABLED && PlayerUtil.hasPerm(player, Permissions.Command.MAIL)) {
				int unreadCount = 0;

				for (final Mail mail : serverCache.findMailsTo(uniqueId)) {
					final Recipient recipient = mail.findRecipient(uniqueId);

					if (!recipient.isMarkedDeleted() && !recipient.hasOpened())
						unreadCount++;
				}

				if (unreadCount > 0) {
					final int finalUnreadCount = unreadCount;

					Common.runLater(4, () -> Messenger.warn(player, Lang.of("Commands.Mail.Join_Notification", finalUnreadCount)));
				}
			}

			// Send join message
			if (Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.JOIN) && !Mute.isPartMuted(Settings.Mute.HIDE_JOINS, player) && !PlayerUtil.isVanished(player)) {

				// If we delay join message, then put it into cache to use later
				if (HookManager.isAuthMeLoaded() && Integration.AuthMe.DELAY_JOIN_MESSAGE_UNTIL_LOGGED)
					senderCache.setJoinMessage(event.getJoinMessage());

				else
					Common.runLater(Settings.Messages.DEFER_JOIN_MESSAGE_BY.getTimeTicks(), () -> PlayerMessages.broadcast(PlayerMessage.Type.JOIN, player, event.getJoinMessage()));
			}

			senderCache.setPendingJoin(false);
		});
	}

	/**
	 * Handle player being kicked
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onKick(PlayerKickEvent event) {
		final Player player = event.getPlayer();
		final SenderCache senderCache = SenderCache.from(player);
		final String reason = Common.stripColors(event.getReason()).toLowerCase();

		// Prevent disconnect spam if having permission
		if ((reason.equals("disconnect.spam") || reason.equals("kicked for spamming")) && !PlayerUtil.hasPerm(player, Permissions.Bypass.SPAM_KICK)) {
			event.setCancelled(true);

			Log.logOnce("spamkick", "TIP: " + player.getName() + " was kicked for chatting or running commands rapidly. " +
					" If you are getting kicked when removing messages with [X], give yourself " + Permissions.Bypass.SPAM_KICK + " permission.");
			return;
		}

		// Custom message
		if (!senderCache.isLoadingMySQL() && Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.KICK)) {
			if (!Mute.isPartMuted(Settings.Mute.HIDE_QUITS, player) && !PlayerUtil.isVanished(player))
				PlayerMessages.broadcast(PlayerMessage.Type.KICK, player, event.getLeaveMessage());

			event.setLeaveMessage(null);
		}
	}

	/**
	 * Handle player leave
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onQuit(PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final PlayerCache cache = PlayerCache.from(player);
		final SenderCache senderCache = SenderCache.from(player);

		// AuthMe
		if (Settings.Integration.AuthMe.HIDE_QUIT_MSG_IF_NOT_LOGGED && !HookManager.isLogged(player)) {
			event.setQuitMessage(null);

			return;
		}

		// If we are still loading the data, do not save the old data on this server
		if (!senderCache.isLoadingMySQL()) {

			// Custom message
			if (Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.QUIT)) {
				if (!Mute.isPartMuted(Settings.Mute.HIDE_QUITS, player) && !PlayerUtil.isVanished(player))
					PlayerMessages.broadcast(PlayerMessage.Type.QUIT, player, event.getQuitMessage());

				event.setQuitMessage(null);
			}

			// And save data async
			Common.runAsync(() -> Database.getInstance().saveCache(cache));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDeath(PlayerDeathEvent event) {
		final Player player = event.getEntity();
		final SenderCache senderCache = SenderCache.from(player);

		// Custom message
		if (Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.DEATH)) {
			event.setDeathMessage(null);

			if (!senderCache.isLoadingMySQL() && !Mute.isPartMuted(Settings.Mute.HIDE_DEATHS, player))
				PlayerMessages.broadcast(PlayerMessage.Type.DEATH, player, event.getDeathMessage());
		}
	}

	/**
	 * Handle editing signs
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onSign(SignChangeEvent event) {
		final Player player = event.getPlayer();
		final SenderCache cache = SenderCache.from(player);

		final Block block = event.getBlock();
		final Material material = block.getType();

		final String[] lines = event.getLines().clone();
		final String[] lastLines = Common.getOrDefault(cache.getLastSignText(), new String[] { "" });

		if (Valid.isNullOrEmpty(lines))
			return;

		// Check mute
		if (Mute.isPartMuted(Settings.Mute.PREVENT_SIGNS, player)) {
			Common.tell(player, Lang.of("Commands.Mute.Cannot_Place_Signs"));

			event.setCancelled(true);
			return;
		}

		// Prevent crashing the server with too long lines text
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];

			if (line.length() > 49) {
				line = line.substring(0, 49);

				lines[i] = line;
				event.setLine(i, line);
			}
		}

		if (Settings.AntiBot.BLOCK_SAME_TEXT_SIGNS && !PlayerUtil.hasPerm(player, Permissions.Bypass.SIGN_DUPLICATION)) {
			if (Valid.colorlessEquals(lines, lastLines)) {
				Messenger.error(player, Lang.of("Checker.Sign_Duplication"));

				event.setCancelled(true);
				return;
			}

			// Lines not equal, update
			else
				cache.setLastSignText(lines);
		}

		RuleCheck<Rule> check;
		boolean cancelSilently = false;

		try {
			boolean ruleMatched = false;

			// First, evaluate rules on a per line basis
			for (int i = 0; i < event.getLines().length; i++) {
				final String line = event.getLine(i);

				check = Rule.filter(Rule.Type.SIGN, player, line, null);
				final String filteredLine = check.getMessage();

				if (!cancelSilently && check.isCancelledSilently())
					cancelSilently = true;

				if (!line.equals(filteredLine)) {
					event.setLine(i, Common.limit(filteredLine, 15));

					ruleMatched = true;
				}
			}

			// If no rules are matched, try to join the lines without space to prevent player
			// bypassing rules by simply splitting the string over multiple lines
			if (!ruleMatched) {
				final String originalMessage = String.join("", lines);

				check = Rule.filter(Rule.Type.SIGN, player, originalMessage, null);
				final String filteredMessage = check.getMessage();

				if (!cancelSilently && check.isCancelledSilently())
					cancelSilently = true;

				if (!originalMessage.equals(filteredMessage)) {

					// In this case, we will have to rerender the line order
					// and simply merge everything together (spaces will be lost)
					final String[] split = Common.split(filteredMessage, 15);

					for (int i = 0; i < event.getLines().length; i++)
						event.setLine(i, i < split.length ? split[i] : "");
				}
			}

			// If rule is silent, send packet back as if the sign remained unchanged
			if (cancelSilently)
				Common.runLater(2, () -> {

					// Check for the rare chance that the block has been changed
					if (block.getLocation().getBlock().getType().equals(material))
						player.sendSignChange(block.getLocation(), lines);
				});

		} catch (final EventHandledException ex) {
			event.setCancelled(true);

			return;
		}

		// Send the final message to spying players and log if the block is still a valid sign
		if (block.getState() instanceof Sign) {
			Log.logSign(player, event.getLines());

			Spy.broadcastSign(player, event.getLines());
		}
	}

	/**
	 * Handler for inventory clicking
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		final Player player = (Player) event.getWhoClicked();
		final ItemStack currentItem = event.getCurrentItem();

		// No anvil on this version
		if (MinecraftVersion.olderThan(V.v1_4))
			return;

		// Check anvil rules
		if (event.getInventory().getType() == InventoryType.ANVIL && event.getSlotType() == InventoryType.SlotType.RESULT && currentItem.hasItemMeta() && currentItem.getItemMeta().hasDisplayName()) {
			final ItemMeta meta = currentItem.getItemMeta();
			final String name = meta.getDisplayName();

			try {
				final String newName = Rule.filter(Rule.Type.ANVIL, player, name, null).getMessage();

				if (newName.isEmpty())
					throw new EventHandledException(true);

				if (!name.equals(newName)) {
					meta.setDisplayName(newName);

					currentItem.setItemMeta(meta);
					event.setCurrentItem(currentItem);

					player.updateInventory();
				}

				// Send to spying players
				Spy.broadcastAnvil(player, currentItem);

				// Log
				Log.logAnvil(player, currentItem);

			} catch (final EventHandledException ex) {
				if (ex.isCancelled())
					event.setCancelled(true);
			}
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Mail */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Monitor player dropping the draft and remove it.
	 *
	 * @param event
	 */
	@EventHandler
	public void onItemDrop(PlayerDropItemEvent event) {
		final ItemStack item = event.getItemDrop().getItemStack();
		final Player player = event.getPlayer();

		if (CompMetadata.hasMetadata(item, Book.TAG)) {
			discardBook(player, event);

			Common.runLater(() -> player.setItemInHand(new ItemStack(CompMaterial.AIR.getMaterial())));
		}
	}

	/**
	 * Monitor player clicking anywhere holding the draft and remove it.
	 *
	 * @param event
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		final Player player = (Player) event.getWhoClicked();
		final ItemStack clicked = event.getCurrentItem();
		final ItemStack cursor = event.getCursor();

		if ((cursor != null && CompMetadata.hasMetadata(player, Book.TAG)) || (clicked != null && CompMetadata.hasMetadata(clicked, Book.TAG))) {
			event.setCursor(new ItemStack(CompMaterial.AIR.getMaterial()));
			event.setCurrentItem(new ItemStack(CompMaterial.AIR.getMaterial()));

			discardBook(player, event);
		}
	}

	/*
	 * Discards the pending mail if any
	 */
	private void discardBook(Player player, Cancellable event) {
		event.setCancelled(true);

		SenderCache.from(player).setPendingMail(null);
		Messenger.info(player, Lang.of("Commands.Mail.Draft_Discarded"));

		Common.runLater(() -> player.updateInventory());
	}
}
