package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Channel.Mode;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.remain.Remain;

public final class ChannelLeave extends ChannelSubCommand {

	public ChannelLeave() {
		super("leave/l");

		setUsage(Lang.of("Channels.Leave.Usage"));
		setDescription(Lang.of("Channels.Leave.Description"));
		setMinArguments(1);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Channels.Leave.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		checkUsage(args.length < 3);

		final Channel channel = findChannel(args[0]);

		checkBoolean(isPlayer() || args.length == 2, Lang.of("Commands.Console_Missing_Player_Name"));

		pollCache(args.length == 2 ? args[1] : sender.getName(), cache -> {
			final boolean self = cache.getPlayerName().equals(sender.getName());
			final Set<Channel> oldChannels = cache.getChannels().keySet();
			final Player playerMaybe = Remain.getPlayerByUUID(cache.getUniqueId());

			checkPerm(Permissions.Channel.LEAVE.replace("{channel}", channel.getName()));

			// Check if joining oneself
			if (!self)
				checkPerm(Permissions.Channel.LEAVE_OTHERS);

			// Check if player connected
			if (BungeeCord.ENABLED) {
				checkBoolean(MySQL.ENABLED, Lang.of("Commands.No_MySQL_BungeeCord"));
				checkBoolean(SyncedCache.isPlayerConnected(cache.getUniqueId()), Lang.of("Player.Not_Connected", cache.getPlayerName()));

			} else
				checkNotNull(playerMaybe, Lang.of("Player.Not_Online", cache.getPlayerName()).replace("{player}", cache.getPlayerName()));

			if (oldChannels.isEmpty())
				returnTell(Lang.ofScript("Channels.Leave.No_Channels", SerializedMap.of("self", self)));

			final List<Channel> channelsPlayerCanLeave = Channel.filterChannelsPlayerCanLeave(cache.getChannels().keySet(), playerMaybe);

			checkBoolean(cache.isInChannel(channel.getName()),
					Lang.ofScript("Channels.Leave.Not_Joined",
							SerializedMap.ofArray("self", self, "channelsPlayerCanLeave", channelsPlayerCanLeave), cache.getPlayerName(), Common.join(channelsPlayerCanLeave, ", ", Channel::getName)));

			final int readLimit = Settings.Channels.MAX_READ_CHANNELS.getForUUID(cache.getUniqueId());

			final boolean hasJoinReadPerm = playerMaybe == null || PlayerUtil.hasPerm(playerMaybe, Replacer.replaceArray(Permissions.Channel.JOIN,
					"channel", channel.getName(),
					"mode", Mode.READ.getKey()));

			// If leaving channel player is not reading and he can read,
			// turn into reading channel first before leaving completely
			if (Settings.Channels.JOIN_READ_OLD && hasJoinReadPerm && cache.getChannels(Mode.READ).size() < readLimit && cache.getChannelMode(channel) != Mode.READ) {
				cache.updateChannelMode(channel, Mode.READ);

				final String message = Lang.of("Channels.Leave.Switch_To_Reading", channel.getName());

				tellSuccess(message.replace("{person}", self ? Lang.of("You") : cache.getPlayerName()));

				if (!self && playerMaybe != null)
					Messenger.success(playerMaybe, message.replace("{person}", Lang.of("You")));

				return;

			} else {
				channel.leavePlayer(cache);

				// Mark as manually left
				cache.markLeftChannel(channel);
			}

			final String message = Lang.of("Channels.Leave.Success", channel.getName());

			if (!BungeeCord.ENABLED)
				tellSuccess(message.replace("{person}", self ? Lang.of("You") : cache.getPlayerName()));

			if (!self && playerMaybe != null && !BungeeCord.ENABLED)
				Messenger.success(playerMaybe, message.replace("{person}", Lang.of("You")));

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
			return completeLastWord((isPlayer() ? Channel.getChannelsWithLeavePermission(getPlayer()) : Channel.getChannels())
					.stream()
					.filter(channel -> !(sender instanceof Player) || channel.isInChannel((Player) sender))
					.collect(Collectors.toList()), Channel::getName);

		if (args.length == 2 && hasPerm(Permissions.Channel.LEAVE_OTHERS))
			return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
