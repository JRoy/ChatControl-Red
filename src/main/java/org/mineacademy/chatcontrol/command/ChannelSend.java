package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.ChannelCommands.ChannelSubCommand;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;

public final class ChannelSend extends ChannelSubCommand {

	public ChannelSend() {
		super("send/s");

		setUsage(Lang.of("Channels.Send.Usage"));
		setDescription(Lang.of("Channels.Send.Description"));
		setMinArguments(2);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void onChannelCommand() {
		final Channel channel = findChannel(args[0]);
		final String message = joinArgs(1);

		checkPerm(Permissions.Channel.SEND.replace("{channel}", channel.getName()));
		channel.sendMessage(sender, message);
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
