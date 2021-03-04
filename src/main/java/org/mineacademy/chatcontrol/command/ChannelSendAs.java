package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.exception.EventHandledException;

public final class ChannelSendAs extends ChannelSubCommand {

	public ChannelSendAs() {
		super("sendas/sa");

		setUsage(Lang.of("Channels.Send_As.Usage"));
		setDescription(Lang.of("Channels.Send_As.Description"));
		setMinArguments(3);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final Player player = findPlayer(args[0]);
		final Channel channel = findChannel(args[1]);
		final String message = joinArgs(2);

		checkPerm(Permissions.Channel.SEND.replace("{channel}", channel.getName()));

		try {
			channel.sendMessage(player, message);

		} catch (final EventHandledException ex) {
			tellError(Lang.of("Channels.Send_As.Error", player, String.join(", ", ex.getMessages())));
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)

			// Remove Bungee players, no support yet
			return completeLastWordPlayerNames()
					.stream()
					.filter(name -> Bukkit.getPlayerExact(name) != null)
					.collect(Collectors.toList());

		if (args.length == 2)
			return completeLastWord(Channel.getChannelNames());

		return NO_COMPLETE;
	}
}
