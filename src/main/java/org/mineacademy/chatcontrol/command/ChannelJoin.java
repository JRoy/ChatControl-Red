package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Channels;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.remain.Remain;

public final class ChannelJoin extends ChannelSubCommand {

	public ChannelJoin() {
		super("join/j");

		setUsage(Lang.of("Channels.Join.Usage"));
		setDescription(Lang.of("Channels.Join.Description"));
		setMinArguments(1);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Channels.Join.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		checkUsage(args.length < 4);

		final Channel channel = findChannel(args[0]);
		final Channel.Mode mode = args.length >= 2 ? findMode(args[1]) : Channel.Mode.WRITE;

		checkBoolean(isPlayer() || args.length == 3, Lang.of("Commands.Console_Missing_Player_Name"));

		pollCache(args.length == 3 ? args[2] : sender.getName(), cache -> {
			final boolean self = cache.getPlayerName().equals(sender.getName());
			final Channel.Mode previousMode = cache.getChannelMode(channel);
			final Channel previousWriteChannel = cache.getWriteChannel();
			final Player playerMaybe = Remain.getPlayerByUUID(cache.getUniqueId());

			checkPerm(Permissions.Channel.JOIN.replace("{channel}", channel.getName()).replace("{mode}", mode.getKey()));

			// Check if joining oneself
			if (!self)
				checkPerm(Permissions.Channel.JOIN_OTHERS);

			// Check if player connected
			if (BungeeCord.ENABLED) {
				checkBoolean(MySQL.ENABLED, Lang.of("Commands.No_MySQL_BungeeCord"));
				checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.of("Player.Not_Connected", cache.getPlayerName()));

			} else
				checkNotNull(playerMaybe, Lang.of("Player.Not_Online", cache.getPlayerName()).replace("{player}", cache.getPlayerName()));

			checkBoolean(previousMode != mode, Lang.ofScript("Channels.Join.Already_Connected", SerializedMap.of("self", self), previousMode));

			// Start compiling message for player
			String message = previousMode != null ? Lang.of("Channels.Join.Switch_Success", channel, previousMode, mode) : Lang.of("Channels.Join.Success", channel, mode);

			final int readLimit = Settings.Channels.MAX_READ_CHANNELS.getForUUID(cache.getUniqueId());
			checkBoolean(mode != Mode.READ || readLimit > 0, Lang.of("Channels.Join.Read_Disabled"));

			// Check limits
			if (mode == Mode.READ) {
				int readingChannelsAmount = 0;

				final List<String> channelsPlayerLeft = new ArrayList<>();

				for (final Channel otherReadChannel : cache.getChannels(Mode.READ))
					if (++readingChannelsAmount >= readLimit) {
						cache.updateChannelMode(otherReadChannel, null);

						channelsPlayerLeft.add(otherReadChannel.getName());
					}

				if (!channelsPlayerLeft.isEmpty())
					message += Lang.of("Channels.Join.Leave_Reading", String.join(", ", channelsPlayerLeft));
			}

			// If player has another mode for that channel, remove it first
			if (previousMode != null)
				cache.updateChannelMode(channel, null);

			// Remove the player from old write channel early to avoid errors
			if (previousWriteChannel != null)
				cache.updateChannelMode(previousWriteChannel, null);

			channel.joinPlayer(cache, mode);

			// If player was writing in another write channel, leave him or change mode if set
			if (previousWriteChannel != null && mode == Mode.WRITE) {

				if (Channels.JOIN_READ_OLD && cache.getChannels(Mode.READ).size() + 1 <= readLimit)
					cache.updateChannelMode(previousWriteChannel, Mode.READ);

				else if (previousMode == null)
					message += Lang.of("Channels.Join.Leave_Reading", previousWriteChannel.getName());
			}

			if (!BungeeCord.ENABLED)
				tellSuccess(message
						.replace("{person}", self ? Lang.of("You") : cache.getPlayerName())
						.replace("{pronoun}", Lang.of(self ? "Your" : "His").toLowerCase()));

			if (!self && playerMaybe != null)
				Messenger.success(playerMaybe, message
						.replace("{person}", Lang.of("You"))
						.replace("{pronoun}", Lang.of("Your").toLowerCase()));

			// Notify BungeeCord so that players connected on another server get their channel updated
			updateBungeeData(cache, message);
		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(isPlayer() ? Channel.getChannelsWithJoinPermission(getPlayer()) : Channel.getChannels(), Channel::getName);

		if (args.length == 2) {
			final List<String> modesPlayerHasPermissionFor = new ArrayList<>();

			for (final Channel.Mode mode : Channel.Mode.values())
				if (PlayerUtil.hasPerm(sender, Permissions.Channel.JOIN.replace("{channel}", args[0]).replace("{mode}", mode.getKey())))
					modesPlayerHasPermissionFor.add(mode.getKey());

			return completeLastWord(modesPlayerHasPermissionFor);
		}

		if (args.length == 3 && hasPerm(Permissions.Channel.JOIN_OTHERS))
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
