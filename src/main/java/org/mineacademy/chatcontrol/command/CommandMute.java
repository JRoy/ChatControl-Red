package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.remain.Remain;

public final class CommandMute extends ChatControlCommand {

	public CommandMute() {
		super(Settings.Mute.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Mute.Usage"));
		setDescription(Lang.of("Commands.Mute.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.MUTE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Mute.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final ServerCache cache = ServerCache.getInstance();
		final String type = args[0];
		final boolean isServer = "server".equals(type);

		checkUsage("server".equals(type) || "player".equals(type) || "channel".equals(type));

		// Print status
		if (args.length == 1) {
			final List<SimpleComponent> messages = new ArrayList<>();

			if ("server".equals(type)) {
				final boolean muted = cache.isMuted();

				messages.add(SimpleComponent
						.of(Lang.ofScript("Commands.Mute.Server", SerializedMap.of("muted", muted)))
						.onHover(Lang.ofScript("Commands.Mute.Change_Status_Tooltip", SerializedMap.of("muted", muted)))
						.onClickRunCmd("/" + getLabel() + " server " + (muted ? "off" : "3m")));

			} else if ("channel".equals(type)) {
				for (final Channel channel : Channel.getChannels()) {
					final boolean muted = channel.isMuted();

					messages.add(SimpleComponent
							.of(Lang.ofScript("Commands.Mute.Player_Or_Channel", SerializedMap.of("muted", muted), channel.getName()))
							.onHover(Lang.ofScript("Commands.Mute.Change_Status_Tooltip", SerializedMap.of("muted", muted)))
							.onClickRunCmd("/" + getLabel() + " channel " + channel.getName() + " " + (muted ? "off" : "3m")));
				}
			}

			else {
				pollCaches(caches -> {
					for (final PlayerCache otherCache : caches) {
						final boolean muted = otherCache.isMuted();

						messages.add(SimpleComponent
								.of(Lang.ofScript("Commands.Mute.Player_Or_Channel", SerializedMap.of("muted", muted), otherCache.getPlayerName()))
								.onHover(Lang.ofScript("Commands.Mute.Change_Status_Tooltip", SerializedMap.of("muted", muted)))
								.onClickRunCmd("/" + getLabel() + " player " + otherCache.getPlayerName() + " " + (muted ? "off" : "3m")));
					}

					new ChatPaginator(7)
							.setFoundationHeader(Lang.of("Commands.Mute.Player_Status"))
							.setPages(messages)
							.send(sender);
				});

				return;
			}

			new ChatPaginator(7)
					.setFoundationHeader(Lang.of("Commands.Mute." + (isServer ? "Server_Status" : "Channel_Status")))
					.setPages(messages)
					.send(sender);
		}

		else if (args.length >= 2) {
			checkBoolean(args.length >= (isServer ? 2 : 3), Lang.of("Commands.Mute.No_Duration"));

			final String name = isServer ? "" : args[1];
			final String reason = Common.joinRange(isServer ? 2 : 3, args);
			final String rawDuration = args[isServer ? 1 : 2];
			final boolean isOff = "off".equals(rawDuration);

			SimpleTime duration = null;

			if (!isOff) {
				try {
					final long seconds = TimeUtil.parseToken(rawDuration) / 1000L;

					duration = SimpleTime.fromSeconds((int) seconds);

				} catch (final NumberFormatException ex) {
					returnTell(Lang.of("Commands.Mute.Invalid_Duration"));

				} catch (final IllegalArgumentException ex) {
					returnTell(ex.getMessage());
				}
			}

			final String muteMessage = Lang.ofScript("Commands.Mute.Mute_Success", SerializedMap.of("reason", reason))
					.replace("{type}", type + (isServer ? "" : " " + name))
					.replace("{duration}", rawDuration)
					.replace("{player}", Common.resolveSenderName(sender));

			final String unmuteMessage = Lang.of("Commands.Mute.Unmute_Success")
					.replace("{type}", type + (isServer ? "" : " " + name))
					.replace("{player}", Common.resolveSenderName(sender));

			final Set<CommandSender> recipients = new HashSet<>();

			if ("channel".equals(type)) {
				final Channel channel = findChannel(name);

				checkBoolean(isOff || !channel.isMuted(), Lang.of("Commands.Mute.Already_Muted_Server", name).replace("{type}", WordUtils.capitalizeFully(type)));
				checkBoolean(!isOff || channel.isMuted(), Lang.of("Commands.Mute.Not_Muted", name).replace("{type}", WordUtils.capitalizeFully(type)));

				channel.setMuted(duration);
				recipients.addAll(channel.getOnlinePlayers().keySet());

				if (BungeeCord.ENABLED)
					BungeeUtil.tellBungee(BungeePacket.MUTE, "channel", channel.getName(), isOff ? "off" : duration.toString(), Messenger.getAnnouncePrefix() + (isOff ? unmuteMessage : muteMessage));

			} else if ("player".equals(type)) {
				final SimpleTime finalDuration = duration;

				pollCache(name, playerCache -> {
					checkBoolean(isOff || !playerCache.isMuted(), Lang.of("Commands.Mute.Already_Muted_Server", name).replace("{type}", WordUtils.capitalizeFully(type)));
					checkBoolean(!isOff || playerCache.isMuted(), Lang.of("Commands.Mute.Not_Muted", name).replace("{type}", WordUtils.capitalizeFully(type)));

					final Player playerInstance = playerCache.toPlayer();

					if (playerInstance != null) {
						checkBoolean(!isPlayer() || !playerInstance.equals(sender), Lang.of("Commands.Mute.Cannot_Mute_Yourself"));

						recipients.add(playerInstance);

						if (isOff)
							HookManager.setLiteBansUnmute(playerInstance);
						else
							HookManager.setLiteBansMute(playerInstance, rawDuration, reason);
					}

					playerCache.setMuted(finalDuration);
					recipients.addAll(Remain.getOnlinePlayers());

					final String broadcastMessage = Messenger.getAnnouncePrefix() + (isOff ? unmuteMessage : muteMessage);

					Common.broadcastTo(recipients, broadcastMessage);
					BungeeUtil.tellBungee(BungeePacket.PLAIN_BROADCAST, broadcastMessage);

					if (!isPlayer())
						Common.tell(sender, broadcastMessage);

					updateBungeeData(playerCache);
				});

				return;

			} else {
				checkBoolean(isOff || !cache.isMuted(), Lang.of("Commands.Mute.Already_Muted_Server").replace("{type}", WordUtils.capitalizeFully(type)));
				checkBoolean(!isOff || cache.isMuted(), Lang.of("Commands.Mute.Not_Muted_Server").replace("{type}", WordUtils.capitalizeFully(type)));

				cache.setMuted(duration);
				recipients.addAll(Remain.getOnlinePlayers());

				if (BungeeCord.ENABLED)
					BungeeUtil.tellBungee(BungeePacket.MUTE, "server", "", isOff ? "off" : duration.toString(), Messenger.getAnnouncePrefix() + (isOff ? unmuteMessage : muteMessage));
			}

			recipients.add(sender);

			Common.broadcastTo(recipients, Messenger.getAnnouncePrefix() + (isOff ? unmuteMessage : muteMessage));
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		final List<String> timeSuggestions = Arrays.asList("off", "1m", "15m");

		if (args.length == 1)
			return completeLastWord("server", "channel", "player");

		if (args.length == 2)
			if ("server".equals(args[0]))
				return completeLastWord(timeSuggestions);

			else if ("channel".equals(args[0]))
				return completeLastWord(Channel.getChannelNames());

			else
				return completeLastWordPlayerNames();

		if (args.length == 3 && !"server".equals(args[0]))
			return completeLastWord(timeSuggestions);

		return NO_COMPLETE;
	}
}
