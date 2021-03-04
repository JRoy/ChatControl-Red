package org.mineacademy.chatcontrol.command;

import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;

public final class CommandCaptcha extends ChatControlCommand {

	public CommandCaptcha() {
		super("captcha");

		setUsage("<answer/reset>");
		setDescription("Answer or regenerate captcha.");
		setMinArguments(1);
		setPermission(Permissions.Command.CAPTCHA);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length == 1);

		final String answer = args[0];
		final SenderCache cache = SenderCache.from(sender);

		checkBoolean(!cache.isCaptchaSolved(), "You have already solved the captcha.");

		if ("reset".equals(answer)) {
			cache.generateCaptcha();

			tellInfo(" &6Captcha has successfuly been regenerated to:");
		}

		else if (answer.replace(" ", "").equals(cache.getCaptcha().replace(" ", ""))) {
			cache.setCaptchaSolved(true);

			tellSuccess("&2You have successfully solved the captcha.");
			return;
		}

		else {
			Common.tellLater(0, sender, Messenger.getErrorPrefix() + "Your answer was invalid. Try again or type '/" + getLabel() + " reset' to generate a new captcha.");
		}

		tellNoPrefix(" ");

		tellNoPrefix("&8" + Common.chatLine());
		cache.showCaptcha(sender);
		tellNoPrefix("&8" + Common.chatLine());
	}

}