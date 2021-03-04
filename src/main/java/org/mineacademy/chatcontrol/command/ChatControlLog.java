package org.mineacademy.chatcontrol.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang.WordUtils;
import org.bukkit.metadata.FixedMetadataValue;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Channel;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Log.Type;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Mail.Recipient;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.Players;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.plugin.SimplePlugin;

public final class ChatControlLog extends ChatControlSubCommand {

	public ChatControlLog() {
		super("log/l");

		setUsage(Lang.of("Commands.Log.Usage"));
		setDescription(Lang.of("Commands.Log.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.LOG);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {

		final List<String> lines = new ArrayList<>();

		lines.addAll(Lang.ofList("Commands.Log.Usages_1"));

		if (Settings.Channels.ENABLED && Settings.Log.APPLY_ON.contains(Log.Type.CHAT))
			lines.addAll(Lang.ofList("Commands.Log.Usages_Channel"));

		if ((Settings.PrivateMessages.ENABLED && Settings.Log.APPLY_ON.contains(Log.Type.PRIVATE_MESSAGE)) ||
				(Settings.Mail.ENABLED && Settings.Log.APPLY_ON.contains(Log.Type.MAIL)))
			lines.addAll(Lang.ofList("Commands.Log.Usages_To"));

		lines.addAll(Lang.ofList("Commands.Log.Usages_2"));

		if (!Settings.Rules.APPLY_ON.isEmpty())
			lines.addAll(Lang.ofList("Commands.Log.Usages_Rule"));

		lines.addAll(Lang.ofList("Commands.Log.Usages_3"));

		return Common.toArray(lines);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final Log.Type type = "all".equals(args[0]) ? null
				: findEnum(Log.Type.class, args[0], log -> Settings.Log.APPLY_ON.contains(log),
						Lang.of("Commands.Invalid_Type", "{enum}", Common.join(Settings.Log.APPLY_ON)));

		final String line = joinArgs(1);

		tellInfo(Lang.of("Commands.Compiling_Data"));

		// Read logs async, then send them to player on the main thread
		syncCallback(Log::readLogs, logs -> {

			// Remove those we do not want
			if (type != null)
				logs.removeIf(log -> log.getType() != type);

			checkBoolean(!logs.isEmpty(), Lang.ofScript("Commands.Log.No_Logs", SerializedMap.of("hasType", type != null), type != null ? type.getLocalized() : ""));

			final SerializedMap params = mapParams(type, line);
			final List<SimpleComponent> pages = new ArrayList<>();

			for (final Log log : logs) {

				if (params.containsKey("player") && !params.getString("player").equalsIgnoreCase(log.getSender()))
					continue;

				if (params.containsKey("before") && (System.currentTimeMillis() - params.getLong("before")) < log.getDate())
					continue;

				if (params.containsKey("in") && (System.currentTimeMillis() - params.getLong("in")) > log.getDate())
					continue;

				if (params.containsKey("channel") && !params.getString("channel").equalsIgnoreCase(log.getChannelName()))
					continue;

				if (params.containsKey("label") && !params.getString("label").equalsIgnoreCase(log.getContent().split(" ")[0]))
					continue;

				if (params.containsKey("to") && !Valid.isInList(params.getString("to"), log.getReceivers()))
					continue;

				if ((!params.containsKey("rule") && log.getRuleName() != null) || (!params.containsKey("group") && log.getRuleGroupName() != null))
					continue;

				if ((params.containsKey("rule") && log.getRuleName() == null) || (params.containsKey("group") && log.getRuleGroupName() == null))
					continue;

				if (params.containsKey("rule") && "*".equals(params.getString("rule"))) {
					// ok
				} else if (params.containsKey("rule") && !params.getString("rule").equalsIgnoreCase(log.getRuleName()))
					continue;

				if (params.containsKey("group") && "*".equals(params.getString("group"))) {
					// ok
				} else if (params.containsKey("group") && !params.getString("group").equalsIgnoreCase(log.getRuleGroupName()))
					continue;

				final SimpleComponent component = SimpleComponent.of("&7" + TimeUtil.getFormattedDateMonth(log.getDate()));

				component.append(" &f" + log.getSender());

				{ // Add hover
					final List<String> hover = new ArrayList<>();

					if (log.getChannelName() != null)
						hover.add(Lang.of("Commands.Log.Tooltip_Channel", log.getChannelName()));

					if (log.getRuleName() != null)
						hover.add(Lang.ofScript("Commands.Log.Tooltip_Rule", SerializedMap.of("isUnnamed", log.getRuleName().isEmpty()), log.getRuleName()));

					if (log.getRuleGroupName() != null)
						hover.add(Lang.of("Commands.Log.Tooltip_Group", log.getRuleGroupName()));

					if (!hover.isEmpty())
						component.onHover(hover);
				}

				final List<String> receivers = log.getReceivers();

				if (!receivers.isEmpty())
					if (receivers.size() == 1)
						component.append(" &6-> &f" + receivers.get(0));
					else
						component
								.append(" &6-> &f")
								.append(Common.plural(receivers.size(), "receiver"))
								.onHover(receivers);

				if (log.getType() == Type.MAIL) {
					final Mail mail = Mail.deserialize(SerializedMap.fromJson(log.getContent()));
					final List<String> hover = new ArrayList<>();

					// For console
					final List<String> recipientNames = new ArrayList<>();

					hover.add(Lang.of("Commands.Log.Tooltip_Recipients"));

					for (final Recipient recipient : mail.getRecipients()) {
						final String recipientName = UserMap.getInstance().getName(recipient.getUniqueId());

						if (recipientName != null) {
							hover.add("&7- &f" + recipientName);

							recipientNames.add(recipientName);
						}
					}

					hover.addAll(Lang.ofList("Commands.Log.Tooltip_Click_To_Read"));

					final UUID uniqueId = mail.getBody().getUniqueId();

					if (isPlayer()) {
						getPlayer().setMetadata("FoLogBook_" + uniqueId, new FixedMetadataValue(SimplePlugin.getInstance(), mail.getBody()));

						component
								.append(Lang.of("Commands.Log.Sent_Mail", mail.getTitle()))
								.onHover(hover)
								.onClickRunCmd("/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " internal log-book " + uniqueId);
					}

					else
						component.append(" sent mail '" + mail.getTitle() + "' to " + recipientNames + ": " + mail.getBody().getPages());
				}

				else if (log.getType() == Type.SIGN) {
					component
							.append(Lang.of("Commands.Log.Placed_Sign"))
							.append(" &6[hover]")
							.onHover(log.getContent().split("%FOLINES%"));
				}

				else if (log.getType() == Type.BOOK) {
					final String copy = log.getContent();

					// Avoiding parsing malformed JSON
					if (copy.contains("{") && copy.contains("}")) {
						final Book book = Book.deserialize(SerializedMap.fromJson(copy));

						if (isPlayer()) {
							getPlayer().setMetadata("FoLogBook_" + book.getUniqueId(), new FixedMetadataValue(SimplePlugin.getInstance(), book));

							component
									.append(Lang.ofScript("Commands.Log.Wrote_Book", SerializedMap.of("isSigned", book.isSigned())).replace("{title}", Common.getOrDefaultStrict(book.getTitle(), "")))
									.onHover(Lang.of("Commands.Log.Wrote_Book_Tooltip"))
									.onClickRunCmd("/" + Settings.MAIN_COMMAND_ALIASES.get(0) + " internal log-book " + book.getUniqueId());
						} else
							component.append(" wrote a book" + (book.getTitle() == null ? "" : " " + book.getTitle()) + ": " + book.getPages());
					}
				}

				else if (log.getType() == Type.ANVIL) {
					final SerializedMap map = SerializedMap.fromJson(log.getContent());
					final List<String> hover = Common.toList(map.getStringList("name"));
					final List<String> lore = map.getStringList("lore");

					if (!lore.isEmpty())
						hover.addAll(lore);

					component.append(Lang.of("Commands.Log.Renamed_Item"))
							.append(map.getString("type"))
							.onHover(hover);
				}

				else
					component.append("&7: &f" + log.getContent());

				pages.add(component);
			}

			checkBoolean(!pages.isEmpty(), Lang.of("Commands.Log.No_Logs_Matched"));

			new ChatPaginator()
					.setFoundationHeader(Lang.ofScript("Commands.Log.Listing_Header", SerializedMap.of("noType", type == null), type != null ? WordUtils.capitalizeFully(type.getLocalized()) : ""))
					.setPages(pages)
					.send(sender);
		});
	}

	/*
	 * Map chat key:value pairs parameters
	 */
	private SerializedMap mapParams(@Nullable Log.Type type, String line) {
		final SerializedMap params = new SerializedMap();
		final String[] words = line.split(" ");

		for (final String word : words) {
			if (word.isEmpty())
				continue;

			checkBoolean(word.contains(":"), Lang.of("Commands.Log.Invalid_Syntax", word));

			final String[] split = word.split("\\:");
			final String key = split[0];
			Object value = Common.joinRange(1, split);

			checkBoolean(!value.toString().isEmpty(), Lang.of("Commands.Log.Invalid_Value", key));

			if ("player".equals(key)) {
				// ok

			} else if ("before".equals(key) || "in".equals(key)) {

				try {
					value = TimeUtil.parseToken(value.toString());

				} catch (final IllegalArgumentException ex) {
					returnTell(Lang.of("Commands.Log.Invalid_Key", key, value));
				}

			} else if ("channel".equals(key)) {
				checkBoolean(type == null || type == Type.CHAT, Lang.of("Commands.Log.Cannot_Use_Channel"));

				final Channel channel = Channel.findChannel(value.toString());
				checkNotNull(channel, Lang.of("Commands.Invalid_Channel", value, Common.join(Channel.getChannelNames())));

				value = channel.getName();

			} else if ("label".equals(key)) {
				checkBoolean(type == null || type == Type.COMMAND, Lang.of("Commands.Log.Cannot_Use_Label"));
				value = value.toString().startsWith("/") ? value : "/" + value;

			} else if ("to".equals(key)) {
				checkBoolean(type == null || type == Type.PRIVATE_MESSAGE, Lang.of("Commands.Log.Cannot_Use_To"));

			} else if ("rule".equals(key) || "group".equals(key)) {
				// ok

			} else
				returnTell(Lang.of("Commands.Invalid_Param_Short", word));

			params.put(key, value);
		}

		return params;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(Common.joinArrays(Arrays.asList(Log.Type.values()), Arrays.asList("all")));

		if (args.length > 1) {
			final String word = args[args.length - 1];

			if (word.contains(":") && !word.equals(":")) {
				final String key = word.split("\\:")[0];
				List<String> tab = new ArrayList<>();

				if ("player".equals(key))
					tab = Players.getPlayerNames(sender);

				else if ("before".equals(key) || "in".equals(key))
					tab = Arrays.asList("15m", "1h");

				else if ("channel".equals(key))
					tab = Channel.getChannelNames();

				else if ("label".equals(key))
					tab = Arrays.asList("tell", "me");

				else if ("rule".equals(key))
					tab = Common.convert(Rules.getInstance().getRulesWithName(), org.mineacademy.chatcontrol.operator.Rule::getName);

				else if ("group".equals(key))
					tab = Groups.getInstance().getGroupNames();

				if (!tab.isEmpty())
					return completeLastWord(Common.convert(tab, completed -> key + ":" + completed));

			} else
				return completeLastWord("player:", "before:", "in:", "channel:", "label:", "to:", "rule:", "group:");
		}

		return NO_COMPLETE;
	}
}
