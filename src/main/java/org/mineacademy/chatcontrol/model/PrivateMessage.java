package org.mineacademy.chatcontrol.model;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * Class dealing with private messages
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrivateMessage {

	/**
	 * The message sender
	 */
	private final CommandSender sender;

	/**
	 * The message sender cache, null if console
	 */
	@Nullable
	private final PlayerCache senderCache;

	/**
	 * The message receiver
	 */
	private final SyncedCache receiverCache;

	/**
	 * The body copy
	 */
	private String message;

	/*
	 * Sends the message
	 */
	private void send() throws EventHandledException {
		final boolean senderPlayer = this.sender instanceof Player;
		final boolean bypassReach = PlayerUtil.hasPerm(this.sender, Permissions.Bypass.REACH);
		final SyncedCache syncedReceiver = SyncedCache.fromUUID(this.receiverCache.getUniqueId());

		@Nullable
		final Player receiver = Bukkit.getPlayerExact(this.receiverCache.getPlayerName());

		if (syncedReceiver == null || (!bypassReach && syncedReceiver.isVanished()))
			throw new EventHandledException(true, Lang.of("Player.Not_Online").replace("{player}", this.receiverCache.getPlayerName()));

		if (!bypassReach && senderPlayer) {
			if (Settings.Ignore.ENABLED && Settings.Ignore.STOP_PRIVATE_MESSAGES && this.receiverCache.isIgnoringPlayer(this.senderCache.getUniqueId()))
				throw new EventHandledException(true, Lang.of("Commands.Ignore.Cannot_PM", this.receiverCache.getPlayerName()));

			if (Settings.Toggle.APPLY_ON.contains(Toggle.PM) && !this.sender.getName().equals(this.receiverCache.getPlayerName()) && this.receiverCache.isIgnoringPMs())
				throw new EventHandledException(true, Lang.of("Commands.Toggle.Cannot_PM", this.receiverCache.getPlayerName()));
		}

		if (senderPlayer)
			SenderCache.from(this.sender).setReplyPlayer(this.receiverCache.getPlayerName(), this.receiverCache.getUniqueId());

		if (syncedReceiver.isAfk())
			Common.tellLater(1, this.sender, Variables.replace(Lang.of("Commands.Tell.Afk_Warning", this.receiverCache.getPlayerName()), this.sender));

		// Compile message and add colors
		this.message = Colors.addColorsForPerms(this.sender, this.message, Colors.Type.PRIVATE_MESSAGE);

		final SerializedMap variables = SerializedMap.ofArray("receiver", this.receiverCache.getPlayerName(), "sender", Common.resolveSenderName(this.sender));

		// Prepare the messages
		final SimpleComponent receiverMessage = Format.parse(Settings.PrivateMessages.FORMAT_RECEIVER).build(this.sender, this.message, variables
				.putArray(
						"player", this.receiverCache.getPlayerName(),
						"receiver_prefix", receiver != null ? org.mineacademy.fo.model.HookManager.getPlayerPrefix(receiver) : "{receiver_prefix}"));

		final SimpleComponent senderMessage = Format.parse(Settings.PrivateMessages.FORMAT_SENDER).build(this.sender, this.message, variables);
		final SimpleComponent consoleMessage = Format.parse(Settings.PrivateMessages.FORMAT_CONSOLE).build(this.sender, this.message, variables);

		// Fire
		senderMessage.send(this.sender);

		if (receiver != null)
			receiverMessage.send(receiver);

		else if (BungeeCord.ENABLED)
			BungeeUtil.tellBungee(BungeePacket.SIMPLECOMPONENT_MESSAGE, this.receiverCache.getUniqueId(), receiverMessage.serialize().toJson());

		// Play sound, if null then send via bungee
		playSound(receiver);

		Common.logNoPrefix(consoleMessage.getPlainMessage());

		// Toasts
		if (Settings.PrivateMessages.TOASTS) {
			final String[] toast = Replacer.replaceArray(Settings.PrivateMessages.FORMAT_TOAST.replace("\\n", "|"),
					"sender", Common.limit(this.sender.getName(), 21),
					"message", Common.limit(this.message, 41)).split("\\|");

			for (int i = 0; i < toast.length; i++)
				toast[i] = Common.limit(toast[i], 41);

			final String toastJoined = String.join("\n", toast);

			if (receiver != null)
				Remain.sendToast(receiver, toastJoined, CompMaterial.WRITABLE_BOOK);

			else if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.TOAST, this.receiverCache.getUniqueId(), Toggle.PM, toastJoined, CompMaterial.WRITABLE_BOOK.name());
		}

		final Player receiverPlayer = Remain.getPlayerByUUID(this.receiverCache.getUniqueId());

		if (receiverPlayer == null) {

			// Do not throw error if no bungee and null.. player might have just disconnected for example
			if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.REPLY_UPDATE, this.receiverCache.getUniqueId(), this.sender.getName(), senderPlayer ? this.senderCache.getUniqueId() : FoConstants.NULL_UUID);

		} else
			SenderCache.from(receiverPlayer).setReplyPlayer(this.sender.getName(), senderPlayer ? this.senderCache.getUniqueId() : FoConstants.NULL_UUID);

		// Log and ... spy!
		logAndSpy();
	}

	/*
	 * Plays a sound to the given receiver if available
	 */
	private void playSound(@Nullable Player receiver) {
		final SimpleSound sound = Settings.PrivateMessages.SOUND;

		if (sound.isEnabled())
			if (receiver != null)
				sound.play(receiver);

			else if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.SOUND, this.receiverCache.getUniqueId(), sound.toString());
	}

	/*
	 * Logs the message and send it to spying players
	 */
	private void logAndSpy() {
		Log.logPrivateMessage(this.sender, this.receiverCache.getPlayerName(), this.message);

		if (this.senderCache != null)
			Spy.broadcastPrivateMessage(this.sender, this.senderCache, this.receiverCache, this.message);
	}

	/**
	 * Fire a private message between two players
	 *
	 * @param sender, can be console
	 * @param receiver
	 * @param message
	 *
	 * @throws EventHandledException if message was not delivered
	 */
	public static void send(@NonNull CommandSender sender, @NonNull SyncedCache receiver, @NonNull String message) throws EventHandledException {
		final PrivateMessage privateMessage = new PrivateMessage(sender, sender instanceof Player ? PlayerCache.from((Player) sender) : null, receiver, message);

		privateMessage.send();
	}
}
