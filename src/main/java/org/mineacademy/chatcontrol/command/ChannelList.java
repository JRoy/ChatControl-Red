package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.command.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class ChannelList extends ChannelSubCommand {

	public ChannelList() {
		super("list/ls");

		setDescription(Lang.of("Channels.List.Description"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Channels.List.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		checkUsage(args.length <= 1);

		final String selectedChannelName = args.length == 1 ? args[0] : null;
		final Channel selectedChannel = selectedChannelName != null ? Channel.findChannel(selectedChannelName) : null;
		final boolean canSeeOptions = hasPerm(Permissions.Channel.LIST_OPTIONS);

		if (selectedChannelName != null)
			checkNotNull(selectedChannel, Lang.of("Channels.No_Channel", selectedChannelName, Common.join(Channel.getChannelNames())));

		pollCaches(caches -> {
			final List<SimpleComponent> messages = new ArrayList<>();
			final Map<Channel, Map<String /*player*/, Channel.Mode>> allChannelPlayers = new HashMap<>();

			for (final PlayerCache cache : caches)
				if (!BungeeCord.ENABLED || SyncedCache.isPlayerConnected(cache.getUniqueId())) {
					for (final Map.Entry<Channel, Channel.Mode> entry : cache.getChannels().entrySet()) {
						final Channel channel = entry.getKey();
						final Channel.Mode mode = entry.getValue();

						if (BungeeCord.ENABLED || cache.toPlayer() != null) {
							final Map<String /*player*/, Channel.Mode> playersInChannel = allChannelPlayers.getOrDefault(channel, new HashMap<>());
							playersInChannel.put(cache.getPlayerName(), mode);

							allChannelPlayers.put(channel, playersInChannel);
						}
					}
				}

			for (final Channel channel : Channel.getChannels()) {

				// Filter channel if parameter is given
				if (selectedChannel != null && !selectedChannel.equals(channel))
					continue;

				// Allow one-click un/mute
				final boolean muted = channel.isMuted();
				final boolean joined = isPlayer() ? channel.isInChannel(getPlayer()) : false;

				final SimpleComponent channelNameComponent = SimpleComponent
						.of(" &f" + channel.getName());

				if (canSeeOptions && isPlayer()) {

					if (Settings.Mute.ENABLED)
						channelNameComponent
								.append(Lang.ofScript("Channels.List.Mute", SerializedMap.of("muted", muted)))
								.onHover(Lang.ofScript("Channels.List.Mute_Tooltip", SerializedMap.of("muted", muted)))
								.onClickRunCmd("/" + Settings.Mute.COMMAND_ALIASES.get(0) + " channel " + channel.getName() + " " + (muted ? "off" : "15m"));

					channelNameComponent
							.append(Lang.ofScript("Channels.List.Join", SerializedMap.of("joined", joined)))
							.onHover(Lang.ofScript("Channels.List.Join_Tooltip", SerializedMap.of("joined", joined)))
							.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " " + (joined ? "leave" : "join") + " " + channel.getName());
				}

				messages.add(channelNameComponent);

				final Map<String /*player*/, Channel.Mode> channelPlayers = allChannelPlayers.getOrDefault(channel, new HashMap<>());

				if (channelPlayers.isEmpty())
					messages.add(Lang.ofComponent("Channels.List.No_Players"));

				else
					for (final Entry<String, Channel.Mode> entry : channelPlayers.entrySet()) {
						final String playerName = entry.getKey();
						final Channel.Mode mode = entry.getValue();

						final SimpleComponent playerComponent = SimpleComponent.of(" &7- ");

						if (canSeeOptions && isPlayer())
							playerComponent
									.append(Lang.of("Channels.List.Remove"))
									.onHover(Lang.of("Channels.List.Remove_Tooltip", playerName))
									.onClickRunCmd("/" + Settings.Channels.COMMAND_ALIASES.get(0) + " leave " + channel.getName() + " " + playerName);

						playerComponent.append(Lang.of("Channels.List.Line")
								.replace("{mode_color}", mode.getColor().toString())
								.replace("{mode}", mode.getKey().toString())
								.replace("{player}", playerName));

						messages.add(playerComponent);
					}

				messages.add(SimpleComponent.of(" "));
			}

			new ChatPaginator()
					.setFoundationHeader(Lang.ofScript("Channels.List.Header", SerializedMap.of("bungee", BungeeCord.ENABLED)))
					.setPages(messages)
					.send(sender);

		});
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(Channel.getChannelNames());

		return NO_COMPLETE;
	}
}
