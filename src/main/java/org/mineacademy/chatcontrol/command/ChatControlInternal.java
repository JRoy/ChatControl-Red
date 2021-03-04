package org.mineacademy.chatcontrol.command;

import java.util.List;
import java.util.UUID;

import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.model.Discord;
import org.mineacademy.chatcontrol.model.Packets;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.model.HookManager;

public final class ChatControlInternal extends ChatControlSubCommand {

	public ChatControlInternal() {
		super("internal");

		setUsage("<type> <id>");
		setDescription("Internal command (keep calm).");
		setMinArguments(2);

		// No permission, since this command can't be executed without knowing
		// the unique key, we give access to everyone (it is conditioned that they
		// have access to a command giving them the key passthrough)
		setPermission(null);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleSubCommand#showInHelp()
	 */
	@Override
	protected boolean showInHelp() {
		return false;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final String param = args[0];
		final UUID uuid = UUID.fromString(args[1]);

		if ("log-book".equals(param)) {
			checkConsole();

			final String metadataName = "FoLogBook_" + uuid;
			checkBoolean(getPlayer().hasMetadata(metadataName), "You do not have the key metadata!");

			final Book mail = (Book) getPlayer().getMetadata(metadataName).get(0).value();
			mail.open(getPlayer());
		}

		else if ("remove".equals(param)) {
			if (HookManager.isProtocolLibLoaded())
				Packets.getInstance().removeMessage(Packets.RemoveMode.SPECIFIC_MESSAGE, uuid);
			else
				// Every 3 hours broadcast this
				Common.logTimed(3600 * 3, "Warning: Attempted to remove a chat message but ProtocolLib plugin is missing, please install it. Ignoring...");

			if (HookManager.isDiscordSRVLoaded())
				Discord.getInstance().removeChannelMessage(uuid);

			if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.REMOVE_MESSAGE_BY_UUID, Packets.RemoveMode.SPECIFIC_MESSAGE.getKey(), uuid);
		}

		else if ("book".equals(param)) {
			checkConsole();

			for (final SenderCache cache : SenderCache.getCaches()) {
				final Book book = cache.getBooks().get(uuid);

				if (book != null) {
					book.open(getPlayer());

					break;
				}
			}
		}

		else
			returnTell(Lang.ofArray("Commands.Invalid_Argument"));
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {
		return NO_COMPLETE;
	}
}
