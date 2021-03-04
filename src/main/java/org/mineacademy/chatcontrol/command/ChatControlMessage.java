
package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.PlayerMessage;
import org.mineacademy.chatcontrol.operator.PlayerMessages;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

/**
 * Represents command related to rules
 */
public final class ChatControlMessage extends ChatControlSubCommand {

	public ChatControlMessage() {
		super("message/m");

		setUsage(Lang.of("Commands.Message.Usage"));
		setDescription(Lang.of("Commands.Message.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.MESSAGE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Message.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 3);

		final String param = args[0];

		if ("reload".equals(param)) {
			PlayerMessages.getInstance().load();

			tellSuccess(Lang.of("Commands.Message.Reloaded"));
			return;
		}

		checkArgs(2, Lang.of("Commands.Message.No_Type", Common.join(Settings.Messages.APPLY_ON)));
		final PlayerMessage.Type type = findMessageType(args[1]);

		if ("list".equals(param)) {
			checkArgs(2, Lang.of("Commands.Message.No_Type", Common.join(Settings.Messages.APPLY_ON)));

			final ChatPaginator pages = new ChatPaginator(15);

			final List<PlayerMessage> messages = PlayerMessages.getInstance().getMessages(type);
			checkBoolean(!messages.isEmpty(), Lang.of("Commands.Message.No_Messages", type.getLocalized()));

			final List<SimpleComponent> lines = new ArrayList<>();

			for (final PlayerMessage message : messages)
				lines.add(SimpleComponent
						.of(Lang.of("Commands.Message.Group", message.getGroup()))
						.onHover(message.toDisplayableString().split("\n")));

			pages
					.setFoundationHeader(Lang.of("Commands.Message.List_Header", messages.size(), type.getLocalized()))
					.setPages(lines)
					.send(sender);
		}

		checkArgs(3, Lang.of("Commands.Message.No_Group"));
		final PlayerMessage message = findMessage(type, args[2]);

		if ("info".equals(param)) {
			tellNoPrefix(Lang.of("Commands.Message.Info", WordUtils.capitalizeFully(type.getLocalized()), message.getGroup()).replace("{chat_line}", Common.chatLine()));
			sender.sendMessage(message.toDisplayableString());

			tellNoPrefix(Lang.of("Commands.Message.Info_Footer").replace("{chat_line}", Common.chatLine()));
		}

		else if ("toggle".equals(param)) {
			final boolean toggle = !message.isDisabled();

			PlayerMessages.getInstance().toggleMessage(message, toggle);
			tellSuccess(Lang.ofScript("Commands.Message.Toggled", SerializedMap.of("toggle", toggle), WordUtils.capitalizeFully(type.getLocalized()), message.getGroup()));
		}

		else
			returnInvalidArgs();
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("info", "toggle", "list", "reload");

		if (args.length == 2 && !"reload".equals(args[0]))
			return completeLastWord(Settings.Messages.APPLY_ON);

		if (args.length == 3 && !"reload".equals(args[0]) && !"list".equals(args[1])) {

			PlayerMessage.Type type;

			try {
				type = PlayerMessage.Type.fromKey(args[1]);
			} catch (final IllegalArgumentException ex) {
				return NO_COMPLETE;
			}

			return completeLastWord(PlayerMessages.getInstance().getEnabledMessageNames(type));
		}

		return NO_COMPLETE;
	}
}
