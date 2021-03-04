package org.mineacademy.chatcontrol.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.WordUtils;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.operator.Groups;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rules;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.conversation.SimpleConversation;
import org.mineacademy.fo.conversation.SimplePrefix;
import org.mineacademy.fo.conversation.SimplePrompt;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.CompSound;

/**
 * Represents command related to rules
 */
public final class ChatControlRule extends ChatControlSubCommand {

	public ChatControlRule() {
		super("rule/r");

		setUsage(Lang.of("Commands.Rule.Usage"));
		setDescription(Lang.of("Commands.Rule.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.RULE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Rule.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length <= 2);

		final String param = args[0];
		final String option = args.length >= 2 ? Common.joinRange(1, args) : null;

		if ("info".equals(param)) {
			final Rule rule = findRule(option);

			tellNoPrefix(Lang.of("Commands.Rule.Info_1", rule.getName()).replace("{chat_line}", Common.chatLine()));
			sender.sendMessage(rule.toDisplayableString());
			tellNoPrefix(Lang.of("Commands.Rule.Info_2").replace("{chat_line}", Common.chatLine()));
		}

		else if ("toggle".equals(param)) {
			final Rule rule = findRule(option);
			final boolean toggle = !rule.isDisabled();

			Rules.getInstance().toggleMessage(rule, toggle);
			tellSuccess(Lang.ofScript("Commands.Rule.Toggle", SerializedMap.of("toggle", toggle), rule.getName()));
		}

		else if ("create".equals(param)) {
			checkConsole();
			checkBoolean(!getPlayer().isConversing(), Lang.of("Player.Already_Conversing", sender.getName()));

			CreateRuleConversation.showTo(getPlayer());
		}

		else if ("import".equals(param)) {
			checkConsole();
			checkBoolean(!getPlayer().isConversing(), Lang.of("Player.Already_Conversing", sender.getName()));

			ImportRulesConversation.showTo(getPlayer());
		}

		else if ("list".equals(param)) {
			checkArgs(2, Lang.of("Commands.Rule.List_No_Type"));

			final Rule.Type type = findRuleType(args[1]);
			final ChatPaginator pages = new ChatPaginator(15);

			final List<Rule> rules = Rules.getInstance().getRules(type);
			checkBoolean(!rules.isEmpty(), Lang.of("Commands.Rule.List_No_Rules", type.getLocalized()));

			final List<SimpleComponent> lines = new ArrayList<>();

			for (final Rule rule : rules) {
				final SimpleComponent component = SimpleComponent.of(" &8- ");

				final String name = rule.getName();
				final String match = rule.getMatch();
				final String[] hover = rule.toDisplayableString().split("\n");

				if (!name.isEmpty()) {
					component.append(Lang.of("Commands.Rule.Tooltip_Name", name));
					component.onHover(hover);
				}

				component.append(Lang.of("Commands.Rule.Tooltip_Match"));
				component.onHover(hover);

				final int remainingSpace = MathUtil.range(70 - component.getPlainMessage().length(), 5, 70);

				component.append(match.length() > remainingSpace ? match.substring(0, remainingSpace) : match);
				component.onHover(hover);

				lines.add(component);
			}

			pages
					.setFoundationHeader(Lang.of("Commands.Rule.List_Header", rules.size(), WordUtils.capitalizeFully(type.getLocalized())))
					.setPages(lines)
					.send(sender);
		}

		else if ("reload".equals(param)) {
			Rules.getInstance().load();

			tellSuccess(Lang.of("Commands.Rule.Reloaded"));
		}

		else
			returnInvalidArgs();
	}

	/**
	 * Represents the data stored in the conversation below
	 */
	private enum Data {
		NAME, FILE, TYPE, ENCODE, MATCH, GROUP
	}

	/**
	 * Represens the creation wizard conversation
	 */
	private final static class CreateRuleConversation extends SimpleConversation {

		/**
		 * The naming question
		 */
		protected final Prompt namePrompt;

		/**
		 * The type question
		 */
		protected final Prompt typePrompt;

		/**
		 * The question whether we should attempt to encode the rule with regex
		 */
		protected final Prompt encodePrompt;

		/**
		 * The match question what should be matched
		 */
		protected final Prompt matchPrompt;

		/**
		 * What group the rule should have, none if null question
		 */
		protected final Prompt groupPrompt;

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getPrefix()
		 */
		@Override
		protected ConversationPrefix getPrefix() {
			return new SimplePrefix(Lang.of("Commands.Rule.Rule_Creator"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getTimeout()
		 */
		@Override
		protected int getTimeout() {
			return 2 * 60; // two minutes
		}

		/**
		 *
		 */
		private CreateRuleConversation() {
			this.namePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					final Player player = (Player) ctx.getForWhom();
					CompSound.SUCCESSFUL_HIT.play(player);

					return Lang.of("Commands.Rule.Rule_Creator_Welcome");
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return Rules.getInstance().findRule(input) == null;
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Rule_Creator_Already_Exists", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.NAME, input);

					return typePrompt;
				}
			};

			this.typePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Rule_Creator_Type", ctx.getSessionData(Data.NAME), Common.join(Rule.Type.values()));
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					try {
						Rule.Type.fromKey(input);

						return true;

					} catch (final Exception e) {
						return false;
					}
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Type", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.TYPE, Rule.Type.fromKey(input));

					return encodePrompt;
				}
			};

			this.encodePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Encode");
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return "yes".equals(input) || "no".equals(input);
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Encode");
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.ENCODE, "yes".equals(input));

					return matchPrompt;
				}
			};

			this.matchPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Rule_Creator_Match");
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.MATCH, (boolean) context.getSessionData(Data.ENCODE) ? encodeWord(input) : input);

					return groupPrompt;
				}
			};

			this.groupPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Group_Name", Common.join(Groups.getInstance().getGroupNames()));
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return "none".equals(input) || Groups.getInstance().findGroup(input) != null;
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Group", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.GROUP, input);

					return Prompt.END_OF_CONVERSATION;
				}
			};
		}

		/**
		 *
		 * @see org.mineacademy.fo.conversation.SimpleConversation#onConversationEnd(org.bukkit.conversations.ConversationAbandonedEvent, boolean)
		 */
		@Override
		protected void onConversationEnd(ConversationAbandonedEvent event, boolean cancelledFromInactivity) {
			final ConversationContext context = event.getContext();
			final Conversable conversable = context.getForWhom();
			final Map<Object, Object> data = context.getAllSessionData();

			if (event.gracefulExit()) {
				final String name = (String) data.get(Data.NAME);
				final Rule.Type type = (Rule.Type) data.get(Data.TYPE);
				final String match = (String) data.get(Data.MATCH);
				final String group = (String) data.get(Data.GROUP);

				final Rule rule = Rules.getInstance().createRule(type, match, name, "none".equals(group) ? null : group);

				tell(conversable, Lang.of("Commands.Rule.Rule_Creator_Success"));
				tell(conversable, rule.toDisplayableString());
			}

			else {
				if (cancelledFromInactivity)
					tell(conversable, Lang.of("Commands.Rule.Conversation_Cancelled"));

				else
					tell(conversable, Lang.of("Commands.Rule.Conversation_Abandoned"));
			}
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getFirstPrompt()
		 */
		@Override
		protected Prompt getFirstPrompt() {
			return this.namePrompt;
		}

		/**
		 * Start the conversation for the given player
		 *
		 * @param player
		 */
		private static void showTo(Player player) {
			final CreateRuleConversation conversation = new CreateRuleConversation();

			conversation.start(player);
		}
	}

	/**
	 * Represens the import wizard conversation
	 */
	private final static class ImportRulesConversation extends SimpleConversation {

		/**
		 * What file we should import from?
		 */
		protected final Prompt filePrompt;

		/**
		 * What is the type of rules to import?
		 */
		protected final Prompt typePrompt;

		/**
		 * The question whether we should attempt to encode the rules with regex?
		 */
		protected final Prompt encodePrompt;

		/**
		 * What group the rule should have, none if null question
		 */
		protected final Prompt groupPrompt;

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getPrefix()
		 */
		@Override
		protected ConversationPrefix getPrefix() {
			return new SimplePrefix(Lang.of("Commands.Rule.Rule_Import"));
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getTimeout()
		 */
		@Override
		protected int getTimeout() {
			return 2 * 60; // two minutes
		}

		/**
		 *
		 */
		private ImportRulesConversation() {
			this.filePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					final Player player = (Player) ctx.getForWhom();
					CompSound.SUCCESSFUL_HIT.play(player);

					return Lang.of("Commands.Rule.Rule_Import_Welcome");
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return FileUtil.getFile(input).exists();
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Rule_Import_Invalid_File", FileUtil.getFile(invalidInput).toPath());
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.FILE, FileUtil.getFile(input));

					return typePrompt;
				}
			};

			this.typePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Rule_Import_Type", ctx.getSessionData(Data.FILE), Common.join(Rule.Type.values()));
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					try {
						Rule.Type.fromKey(input);

						return true;

					} catch (final Exception e) {
						return false;
					}
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Type", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.TYPE, Rule.Type.fromKey(input));

					return encodePrompt;
				}
			};

			this.encodePrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Encode");
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return "yes".equals(input) || "no".equals(input);
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Encode");
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.ENCODE, "yes".equals(input));

					return groupPrompt;
				}
			};

			this.groupPrompt = new SimplePrompt() {

				@Override
				protected String getPrompt(ConversationContext ctx) {
					return Lang.of("Commands.Rule.Group_Name", Common.join(Groups.getInstance().getGroupNames()));
				}

				@Override
				protected boolean isInputValid(ConversationContext context, String input) {
					return "none".equals(input) || Groups.getInstance().findGroup(input) != null;
				}

				@Override
				protected String getFailedValidationText(ConversationContext context, String invalidInput) {
					return Lang.of("Commands.Rule.Invalid_Group", invalidInput);
				}

				@Override
				protected Prompt acceptValidatedInput(ConversationContext context, String input) {
					context.setSessionData(Data.GROUP, input);

					return Prompt.END_OF_CONVERSATION;
				}
			};
		}

		/**
		 *
		 * @see org.mineacademy.fo.conversation.SimpleConversation#onConversationEnd(org.bukkit.conversations.ConversationAbandonedEvent, boolean)
		 */
		@Override
		protected void onConversationEnd(ConversationAbandonedEvent event, boolean cancelledFromInactivity) {
			final ConversationContext context = event.getContext();
			final Conversable conversable = context.getForWhom();
			final Map<Object, Object> data = context.getAllSessionData();

			if (event.gracefulExit()) {
				final File file = (File) data.get(Data.FILE);
				final Rule.Type type = (Rule.Type) data.get(Data.TYPE);
				final boolean encode = (boolean) data.get(Data.ENCODE);
				final String group = (String) data.get(Data.GROUP);

				int count = 0;

				for (final String line : FileUtil.readLines(file)) {
					final String match = encode ? encodeWord(line) : line;
					Rules.getInstance().createRule(type, match, null, "none".equals(group) ? null : group);

					count++;
				}

				tell(conversable, Lang.of("Commands.Rule.Rule_Import_Success", count));
			}

			else {
				if (cancelledFromInactivity)
					tell(conversable, Lang.of("Commands.Rule.Conversation_Cancelled"));

				else
					tell(conversable, Lang.of("Commands.Rule.Conversation_Abandoned"));
			}
		}

		/**
		 * @see org.mineacademy.fo.conversation.SimpleConversation#getFirstPrompt()
		 */
		@Override
		protected Prompt getFirstPrompt() {
			return this.filePrompt;
		}

		/**
		 * Start the conversation for the given player
		 *
		 * @param player
		 */
		private static void showTo(Player player) {
			final ImportRulesConversation conversation = new ImportRulesConversation();

			conversation.start(player);
		}
	}

	/*
	 * Utility method to convert plain word into a regex that is harder to bypass
	 */
	private static String encodeWord(String word) {
		String encoded = "\\b(";
		final String section = "{LETTER}+(\\W|\\d|_)*";

		for (final char letter : word.toCharArray())
			encoded += section.replace("{LETTER}", String.valueOf(letter));

		encoded += ")";

		return encoded;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("info", "toggle", "create", "import", "list", "reload");

		if (args.length == 2)
			if ("list".equals(args[0]))
				return completeLastWord(Rule.Type.values());
			else if ("info".equals(args[0]) || "toggle".equals(args[0]))
				return completeLastWord(Common.convert(Rules.getInstance().getRulesWithName(), rule -> rule.getName()));

		return NO_COMPLETE;
	}
}
