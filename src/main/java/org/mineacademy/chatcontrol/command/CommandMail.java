package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlCommand;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Bungee;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Mail.Recipient;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.model.Toggle;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.conversation.SimplePrompt;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class CommandMail extends ChatControlCommand implements Listener {

	public CommandMail() {
		super(Settings.Mail.COMMAND_ALIASES);

		setUsage(Lang.of("Commands.Mail.Usage"));
		setDescription(Lang.of("Commands.Mail.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.MAIL);

		Common.registerEvents(this);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Mail.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkBoolean(MinecraftVersion.atLeast(V.v1_8), Lang.of("Commands.Incompatible", "1.8.8"));
		checkConsole();

		final String param = args[0];
		final Player player = getPlayer();
		final UUID uuid = player.getUniqueId();

		final ServerCache serverCache = ServerCache.getInstance();
		final SenderCache senderCache = SenderCache.from(sender);
		final PlayerCache playerCache = PlayerCache.from(player);

		final Book pendingBody = Common.getOrDefault(senderCache.getPendingMail(), Book.newEmptyBook());

		if ("send".equals(param) || "s".equals(param)) {
			checkNotNull(senderCache.getPendingMail(), Lang.of("Commands.Mail.No_Pending"));
			checkBoolean(pendingBody.isSigned(), Lang.of("Commands.Mail.No_Subject"));
			checkArgs(2, Lang.of("Commands.Mail.No_Recipients"));
			checkUsage(args.length == 2);

			sendMail(args[1], pendingBody);

			return;
		}

		else if ("autor".equals(param) || "ar".equals(param)) {
			checkArgs(2, Lang.of("Commands.Mail.Autoresponder_Usage"));

			final String timeRaw = joinArgs(1);

			if ("off".equals(timeRaw) || "view".equals(timeRaw)) {
				checkBoolean(playerCache.isAutoResponderValid(), Lang.of("Commands.Mail.Autoresponder_Disabled"));

				final Tuple<Book, Long> autoResponder = playerCache.getAutoResponder();

				if ("off".equals(timeRaw)) {
					playerCache.removeAutoResponder();

					tellSuccess(Lang.of("Commands.Mail.Autoresponder_Removed"));
				} else {
					autoResponder.getKey().open(player);

					tellSuccess(Lang.of("Commands.Mail.Autoresponder").replace("{title}", autoResponder.getKey().getTitle()).replace("{date}", TimeUtil.getFormattedDateShort(autoResponder.getValue())));
				}

				return;
			}

			final long futureTime = System.currentTimeMillis() + (findTime(timeRaw).getTimeSeconds() * 1000);

			// If has no email, try updating current auto-responder's date
			if (senderCache.getPendingMail() == null) {
				checkBoolean(playerCache.hasAutoResponder(), Lang.of("Commands.Mail.Autoresponder_Missing"));

				playerCache.setAutoResponderDate(futureTime);

				tellSuccess(Lang.of("Commands.Mail.Autoresponder_Updated", TimeUtil.getFormattedDateShort(futureTime)));
				return;
			}

			checkBoolean(pendingBody.isSigned(), Lang.of("Commands.Mail.No_Subject"));

			// Save
			playerCache.setAutoResponder(senderCache.getPendingMail(), futureTime);

			// Remove draft from cache because it was finished
			senderCache.setPendingMail(null);

			tellSuccess(Lang.of("Commands.Mail.Autoresponder_Set", TimeUtil.getFormattedDateShort(futureTime)));
			return;
		}

		else if ("open".equals(param) || "forward".equals(param) || "reply".equals(param) || "delete-sender".equals(param) || "delete-recipient".equals(param)) {
			checkArgs(2, "Unique mail ID has not been provided. If this is a bug, please report it. If you are playing hard, play harder!");

			final UUID mailId = UUID.fromString(args[1]);
			final Mail mail = serverCache.findMail(mailId);
			checkNotNull(mail, Lang.of("Commands.Mail.Delete_Invalid"));

			if ("open".equals(param)) {
				mail.open(player);

				if (!String.join(" ", args).contains("-donotmarkasread"))
					serverCache.markOpen(mail, player);

				// Notify network that mail has been opened
				Bungee.syncMail(mail);

				return;

			} else if ("forward".equals(param)) {

				// No recipients
				if (args.length == 2)
					new PromptForwardRecipients(mailId).show(player);

				else if (args.length == 3)
					sendMail(args[2], Book.clone(mail.getBody(), player.getName()));

				else
					returnInvalidArgs();

				return;
			}

			else if ("reply".equals(param)) {
				checkConsole();

				senderCache.setPendingMailReply(mail);

				getPlayer().chat("/" + getLabel() + " new");
				return;
			}

			else if ("delete-sender".equals(param)) {
				checkBoolean(!mail.isSenderDeleted(), Lang.of("Commands.Mail.Delete_Invalid"));

				mail.setSenderDeleted(true);
				serverCache.save();

				// Notify network that mail has been removed
				Bungee.syncMail(mail);

				tellSuccess(Lang.of("Commands.Mail.Delete_Sender"));
				return;
			}

			else if ("delete-recipient".equals(param)) {
				checkArgs(3, Lang.of("Commands.Mail.Delete_No_Recipient"));

				final UUID recipientId = UUID.fromString(args[2]);
				final Recipient recipient = mail.findRecipient(recipientId);
				checkNotNull(recipient, Lang.of("Commands.Mail.Delete_Invalid_Recipient"));

				checkBoolean(!recipient.isMarkedDeleted(), Lang.of("Commands.Mail.Delete_Invalid"));

				recipient.setMarkedDeleted(true);
				serverCache.save();

				// Notify network that mail has been removed
				Bungee.syncMail(mail);

				tellSuccess(Lang.of("Commands.Mail.Delete_Recipient"));
				return;
			}
		}

		checkUsage(args.length == 1);

		if ("new".equals(param) || "n".equals(param)) {
			checkUsage(args.length == 1);
			checkBoolean(CompMaterial.isAir(player.getItemInHand().getType()), Lang.of("Commands.Mail.Hand_Full"));

			for (final ItemStack stack : player.getInventory().getContents())
				if (stack != null && CompMetadata.hasMetadata(stack, Book.TAG))
					returnTell(Lang.of("Commands.Mail.Already_Drafting"));

			final ItemStack bookItem = pendingBody.toEditableBook(Lang.of("Commands.Mail.Item_Title"), Lang.ofArray("Commands.Mail.Item_Tooltip"));

			player.setItemInHand(bookItem);

			if (senderCache.getPendingMailReply() != null)
				tellInfo(Lang.of("Commands.Mail.Reply_Usage", senderCache.getPendingMailReply().getSenderName()));
			else
				tellInfo(Lang.ofScript("Commands.Mail.New_Usage", SerializedMap.of("noPendingMail", senderCache.getPendingMail() == null)));

		} else if ("inbox".equals(param) || "i".equals(param) || "read".equals(param)) {

			final List<SimpleComponent> pages = new ArrayList<>();

			for (final Mail incoming : serverCache.findMailsTo(uuid)) {
				final Recipient recipient = incoming.findRecipient(uuid);
				final boolean read = recipient.hasOpened();

				// Hide deleted emails
				if (recipient.isMarkedDeleted())
					continue;

				final List<String> openHover = new ArrayList<>();

				openHover.add(Lang.of("Commands.Mail.Open_Tooltip"));
				openHover.add(Lang.ofScript("Commands.Mail.Open_Tooltip_Script", SerializedMap.of("read", read), TimeUtil.getFormattedDateShort(recipient.getOpenTime())));

				pages.add(SimpleComponent
						.of("&8[" + (read ? "&7" : "&2") + "O&8]")
						.onHover(openHover)
						.onClickRunCmd("/" + getLabel() + " open " + incoming.getUniqueId())

						.append(" ")

						.append("&8[&6R&8]")
						.onHover(Lang.of("Commands.Mail.Reply_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " reply " + incoming.getUniqueId())

						.append(" ")

						.append("&8[&3F&8]")
						.onHover(Lang.of("Commands.Mail.Forward_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " forward " + incoming.getUniqueId())

						.append(" ")

						.append("&8[&cX&8]")
						.onHover(Lang.of("Commands.Mail.Delete_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " delete-recipient " + incoming.getUniqueId() + " " + player.getUniqueId())

						.append(Lang.of("Commands.Mail.Inbox_Line")
								.replace("{subject}", incoming.getTitle())
								.replace("{sender}", incoming.getSenderName())
								.replace("{date}", TimeUtil.getFormattedDateMonth(incoming.getSentDate()))));
			}

			checkBoolean(!pages.isEmpty(), Lang.of("Commands.Mail.No_Incoming_Mail"));

			new ChatPaginator()
					.setFoundationHeader(Lang.of("Commands.Mail.Inbox_Header"))
					.setPages(pages)
					.send(player);

		} else if ("archive".equals(param) || "a".equals(param) || "sent".equals(param)) {
			final List<SimpleComponent> pages = new ArrayList<>();

			for (final Mail outgoing : serverCache.findMailsFrom(uuid)) {

				// Hide deleted emails
				if (outgoing.isSenderDeleted() || outgoing.isAutoReply())
					continue;

				final List<String> statusHover = new ArrayList<>();
				statusHover.add(Lang.of("Commands.Mail.Archive_Recipients_Tooltip"));

				for (final Recipient recipient : outgoing.getRecipients()) {
					final String recipientName = UserMap.getInstance().getName(recipient.getUniqueId());

					if (recipientName != null)
						statusHover.add(Lang.ofScript("Commands.Mail.Archive_Read_Tooltip", SerializedMap.of("hasOpened", recipient.hasOpened()), recipientName));
				}

				pages.add(SimpleComponent.empty()

						.append("&8[&6O&8]")
						.onHover(Lang.of("Commands.Mail.Open_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " open " + outgoing.getUniqueId() + " -donotmarkasread")

						.append(" ")

						.append("&8[&3F&8]")
						.onHover(Lang.of("Commands.Mail.Forward_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " forward " + outgoing.getUniqueId())

						.append(" ")

						.append("&8[&cX&8]")
						.onHover(Lang.of("Commands.Mail.Delete_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " delete-sender " + outgoing.getUniqueId())

						.append(Lang.of("Commands.Mail.Archive_Line")
								.replace("{subject}", outgoing.getTitle())
								.replace("{recipients}", outgoing.getRecipients().size() + " " + Lang.of("Commands.Mail.Archive_Recipient"))
								.replace("{date}", TimeUtil.getFormattedDateMonth(outgoing.getSentDate())))

						.onHover(statusHover));
			}

			checkBoolean(!pages.isEmpty(), Lang.of("Commands.Mail.Archive_No_Mail"));

			new ChatPaginator()
					.setFoundationHeader(Lang.of("Commands.Mail.Archive_Header"))
					.setPages(pages)
					.send(player);

		} else
			returnInvalidArgs();
	}

	/*
	 * Parses the recipients and sends the book email
	 */
	private void sendMail(String recipientsLine, Book body) {
		final Set<UUID> recipients = new HashSet<>();
		final Set<String> recipientNames = new HashSet<>();

		String[] split = recipientsLine.split("\\|");

		// Send to all online recipients
		if (split.length == 1) {
			final String param = split[0].toLowerCase();

			// Send to all online receivers
			if ("online".equals(param))
				split = Common.toArray(Common.getPlayerNames());

			// Send to all offline receivers also
			else if ("all".equals(param)) {

				pollCaches(caches -> {
					for (int i = 0; i < caches.size(); i++) {
						final boolean last = i + 1 == caches.size();

						sendMail0(caches.get(i), recipientNames, recipients, body, last);
					}
				});

				return;
			}
		}

		for (int i = 0; i < split.length; i++) {
			final String recipientName = split[i];

			if (!recipientName.isEmpty()) {
				final boolean last = i + 1 == split.length;

				pollCache(recipientName, recipientCache -> sendMail0(recipientCache, recipientNames, recipients, body, last));
			}
		}
	}

	private void sendMail0(PlayerCache recipientCache, Set<String> recipientNames, Set<UUID> recipients, Book body, boolean last) {
		final String recipientName = recipientCache.getPlayerName();
		final UUID recipientId = recipientCache.getUniqueId();

		checkBoolean(hasPerm(Permissions.Bypass.REACH) || !recipientCache.isIgnoringPlayer(getPlayer().getUniqueId()), Lang.of("Commands.Mail.Send_Fail_Ignore", recipientName));

		// Allow sending to self even if ignored
		if (Settings.Toggle.APPLY_ON.contains(Toggle.MAIL) && !getPlayer().getUniqueId().equals(recipientCache.getUniqueId()))
			checkBoolean(!recipientCache.isIgnoringPart(Toggle.MAIL), Lang.of("Commands.Mail.Send_Fail_Toggle", recipientName));

		recipients.add(recipientId);
		recipientNames.add(recipientName);

		// Auto responder
		if (recipientCache.isAutoResponderValid() && !recipientId.equals(getPlayer().getUniqueId()))
			Common.runLater(20, () -> Mail.sendAutoReply(getPlayer().getUniqueId(), recipientId, recipientCache.getAutoResponder().getKey()));

		if (last) {
			// Prepare mail
			final Mail mail = Mail.send(getPlayer().getUniqueId(), recipients, body);

			// Broadcast to spying players
			Spy.broadcastMail(getPlayer(), body.getTitle(), recipientNames, recipients, mail.getUniqueId());

			// Log
			Log.logMail(getPlayer(), mail);

			// Remove draft from cache because it was finished
			SenderCache.from(sender).setPendingMail(null);

			tellSuccess(Lang.of("Commands.Mail.Send_Success"));

			final Player player = getPlayer();
			final ItemStack[] content = player.getInventory().getContents();

			for (int itemIndex = 0; itemIndex < content.length; itemIndex++) {
				final ItemStack item = content[itemIndex];

				if (item != null && CompMetadata.hasMetadata(player, Book.TAG))
					content[itemIndex] = new ItemStack(CompMaterial.AIR.getMaterial());
			}

			player.getInventory().setContents(content);
			player.updateInventory();
		}
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("new", "n", "send", "s", "autor", "inbox", "i", "read", "archive", "a", "sent");

		if (args.length == 2)
			if ("autor".equals(args[0]))
				return completeLastWord("view", "off", "3 hours", "7 days");
			else if ("send".equals(args[0]))
				return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class PromptForwardRecipients extends SimplePrompt {

		/**
		 * The mail that is being forwarded
		 */
		private final UUID mailId;

		/**
		 * @see org.mineacademy.fo.conversation.SimplePrompt#getPrompt(org.bukkit.conversations.ConversationContext)
		 */
		@Override
		protected String getPrompt(ConversationContext ctx) {
			return Variables.replace(Lang.of("Commands.Mail.Forward_Recipients"), getPlayer(ctx));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimplePrompt#isInputValid(org.bukkit.conversations.ConversationContext, java.lang.String)
		 */
		@Override
		protected boolean isInputValid(ConversationContext context, String input) {

			if (input.isEmpty() || input.equals("|")) {
				tell(Variables.replace(Lang.of("Commands.Mail.Forward_Recipients_Invalid", input), getPlayer(context)));

				return false;
			}

			for (final String part : input.split("\\|")) {
				if (UserMap.getInstance().getRecord(part) == null) {
					tell(Messenger.getErrorPrefix() + Lang.of("Player.Not_Stored", part));

					return false;
				}
			}

			return true;
		}

		/**
		 * @see org.bukkit.conversations.ValidatingPrompt#acceptValidatedInput(org.bukkit.conversations.ConversationContext, java.lang.String)
		 */
		@Override
		protected Prompt acceptValidatedInput(ConversationContext context, String input) {

			// Send later so that the player is not longer counted as "conversing"
			Common.runLater(5, () -> {
				Common.dispatchCommandAsPlayer(getPlayer(context), Variables.replace("{label_mail} forward " + this.mailId + " " + input, getPlayer(context)));

				tell(context, Variables.replace(Lang.of("Commands.Mail.Forward_Success"), getPlayer(context)));
			});

			return null;
		}
	}
}
