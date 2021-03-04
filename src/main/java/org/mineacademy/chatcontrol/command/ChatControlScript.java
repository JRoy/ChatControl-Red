package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.Variables;

public final class ChatControlScript extends ChatControlSubCommand {

	public ChatControlScript() {
		super("script");

		setUsage(Lang.of("Commands.Script.Usage"));
		setDescription(Lang.of("Commands.Script.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.SCRIPT);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Script.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final String value = Common.joinRange(0, args);

		Object result;

		try {
			result = JavaScriptExecutor.run(Variables.replace(value, sender), sender);

		} catch (final Throwable ex) {
			tellError(Lang.of("Commands.Script.Error", ex.getCause() != null ? ex.getCause().toString() : ex.toString()));

			return;
		}

		tellSuccess(Lang.of("Commands.Script.Result", result == null ? Lang.of("None") : result.toString()));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return NO_COMPLETE;
	}
}
