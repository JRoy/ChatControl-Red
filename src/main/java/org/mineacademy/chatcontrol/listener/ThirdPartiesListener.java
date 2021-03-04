package org.mineacademy.chatcontrol.listener;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.Remain;

import com.gmail.nossr50.chat.author.Author;
import com.gmail.nossr50.events.chat.McMMOPartyChatEvent;
import com.palmergames.bukkit.TownyChat.channels.Channel;
import com.palmergames.bukkit.TownyChat.events.AsyncChatHookEvent;

import fr.xephi.authme.events.LoginEvent;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.events.ChatEvent;

/**
 * A common listener for all third party plugin integration
 */
public final class ThirdPartiesListener {

	/**
	 * Register all compatible hooks
	 */
	public static void registerEvents() {

		if (Common.doesPluginExist("SimpleClans")) {
			Common.log("Note: Hooked into SimpleClans to filter ignored players");

			Common.registerEvents(new SimpleClansListener());
		}

		if (Common.doesPluginExist("TownyChat")) {
			Common.log("Note: Hooked into TownyChat to spy channels");

			Common.registerEvents(new TownyChatListener());
		}

		if (HookManager.isMcMMOLoaded()) {
			Common.log("Note: Hooked into mcMMO to spy channels");

			Common.registerEvents(new McMMOListener());
		}

		if (HookManager.isAuthMeLoaded()) {
			Common.log("Note: Hooked into mcMMO to delay join message until login");

			Common.registerEvents(new AuthMeListener());
		}
	}
}

/**
 * SimpleClans handle
 */
final class SimpleClansListener implements Listener {

	/**
	 * Listen to simple clans chat and remove receivers who ignore the sender
	 *
	 * @param event
	 */
	@EventHandler
	public void onPlayerClanChat(ChatEvent event) {
		try {
			final ClanPlayer sender = event.getSender();

			// Reach message to players who ignore the sender if sender has bypass reach permission
			if (PlayerUtil.hasPerm(sender.toPlayer(), Permissions.Bypass.REACH))
				return;

			for (final Iterator<ClanPlayer> it = event.getReceivers().iterator(); it.hasNext();) {
				final ClanPlayer receiver = it.next();
				final PlayerCache receiverCache = PlayerCache.from(receiver.toPlayer());

				if (receiverCache.isIgnoringPlayer(sender.getUniqueId()))
					it.remove();
			}

		} catch (final IncompatibleClassChangeError ex) {
			Common.log("&cWarning: Processing message from SimpleClans failed, if you have TownyChat latest version contact "
					+ SimplePlugin.getNamed() + " authors to update their hook. The error was: " + ex);
		}
	}
}

/**
 * TownyChat handle
 */
final class TownyChatListener implements Listener {

	/**
	 * Listen to chat in towny channels and broadcast spying
	 *
	 * @param event
	 */
	@EventHandler
	public void onChat(AsyncChatHookEvent event) {
		try {
			final Player player = event.getPlayer();
			final String message = event.getMessage();
			final Channel channel = event.getChannel();

			Spy.broadcastCustomChat(player, message, Settings.Spy.FORMAT_PARTY_CHAT, SerializedMap.of("channel", channel.getName()));

		} catch (final IncompatibleClassChangeError ex) {
			Common.log("&cWarning: Processing message from TownyChat channel failed, if you have TownyChat latest version contact "
					+ SimplePlugin.getNamed() + " authors to update their hook. The error was: " + ex);
		}
	}
}

/**
 * mcMMO handle
 */
final class McMMOListener implements Listener {

	/**
	 * Listen to party chat message and forward them to spying players
	 *
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPartyChat(McMMOPartyChatEvent event) {
		try {
			final String message = event.getMessage();
			final String party = event.getParty();

			CommandSender sender;

			try {
				final Author author = event.getAuthor();

				sender = author.isConsole() ? Bukkit.getConsoleSender() : Remain.getPlayerByUUID(author.uuid());

			} catch (final LinkageError ex) {
				sender = Bukkit.getPlayerExact(ReflectionUtil.invoke("getSender", event));
			}

			if (sender != null)
				Spy.broadcastCustomChat(sender, message, Settings.Spy.FORMAT_PARTY_CHAT, SerializedMap.of("channel", party));

		} catch (final IncompatibleClassChangeError ex) {
			Common.log("&cWarning: Processing party chat from mcMMO failed, if you have mcMMO latest version contact "
					+ SimplePlugin.getNamed() + " authors to update their hook. The error was: " + ex);
		}
	}
}

/**
 * AuthMe Reloaded handle
 */
final class AuthMeListener implements Listener {

	/*
	 * Notify about wrong setting combination
	 */
	AuthMeListener() {
		final boolean nativeDelayOption = Bukkit.getPluginManager().getPlugin("AuthMe").getConfig().getBoolean("settings.delayJoinMessage", false);

		if (nativeDelayOption)
			Common.log("&6Warning: Your AuthMe has settings.delayJoinMessage on true, which conflicts with Integration.AuthMe.Delay_Join_Message_Until_Logged option in ChatControl's settings.yml. Disable that and only use our option.");

	}

	/**
	 * Listen to logging in and show join message then instead
	 *
	 * @param event
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onLogin(LoginEvent event) {
		final Player player = event.getPlayer();

		if (Integration.AuthMe.DELAY_JOIN_MESSAGE_UNTIL_LOGGED && Settings.Messages.APPLY_ON.contains(PlayerMessage.Type.JOIN) && !Mute.isPartMuted(Settings.Mute.HIDE_JOINS, player) && !PlayerUtil.isVanished(player)) {
			final SenderCache senderCache = SenderCache.from(player);

			Common.runLater(Settings.Messages.DEFER_JOIN_MESSAGE_BY.getTimeTicks(), () -> PlayerMessages.broadcast(PlayerMessage.Type.JOIN, player, senderCache.getJoinMessage()));
		}
	}
}
