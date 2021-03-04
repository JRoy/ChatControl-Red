package org.mineacademy.chatcontrol.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;

import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.fo.ChatUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigItems;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.JavaScriptExecutor;
import org.mineacademy.fo.model.Replacer;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.model.Variable;
import org.mineacademy.fo.model.Variable.Type;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompChatColor;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.YamlConfig;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Represents a single chat format
 */
public final class Format extends YamlConfig {

	/**
	 * A list of all loaded formats
	 */
	private static final ConfigItems<Format> loadedFormats = ConfigItems.fromFolder("format", "formats", Format.class);

	/**
	 * The format name
	 */
	@Getter
	private final String name;

	/**
	 * The format parts
	 */
	private Map<String, FormatOption> options;

	/**
	 * Legacy formats dont have their place in any external file,
	 * instead, they are dumped in the option itself like:
	 * Format: "&c{player}: &f{message}" instead of specifying the format name
	 *
	 * Thus they cannot be saved
	 */
	private final boolean legacy;

	/*
	 * Construct a new format (called automatically)
	 */
	private Format(String name) {
		this(name, false);
	}

	/*
	 * Construct a new format
	 */
	private Format(String name, boolean legacy) {
		this.name = name;
		this.legacy = legacy;

		if (!this.legacy) {
			final String path = "formats/" + name + ".yml";
			final boolean hasInternal = FileUtil.getInternalResource(path) != null;

			this.loadConfiguration(hasInternal ? path : "prototype/format.yml", path);
		}
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#saveComments()
	 */
	@Override
	protected boolean saveComments() {
		return true;
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#getUncommentedSections()
	 */
	@Override
	protected List<String> getUncommentedSections() {
		return Arrays.asList("Parts");
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#onLoadFinish()
	 */
	@Override
	protected void onLoadFinish() {
		Valid.checkBoolean(!this.legacy, "Cannot load legacy format: " + this.name);

		this.options = new LinkedHashMap<>();

		// Convert [JSON] in parts into an option
		for (final Entry<String, FormatOption> entry : getMap("Parts", String.class, FormatOption.class).entrySet()) {
			final String name = entry.getKey();
			final FormatOption option = entry.getValue();

			final List<String> messages = new ArrayList<>();

			int messageCount = 1;

			for (final String message : option.getMessages()) {
				if (message.startsWith("[JSON]")) {
					int partCount = 1;

					for (final BaseComponent component : Remain.toComponent(message.replace("[JSON]", "").trim()))
						this.options.put("converted-" + messageCount + "-" + partCount++, FormatOption.fromComponent(component));

				} else {
					Valid.checkBoolean(!message.contains("\\n"), "Format part messages cannot contain \\\n, to use multi-line, simply "
							+ "place - '' on more lines in your Message key like this: https://i.imgur.com/KwuOaB7.png");

					messages.add(message);
				}

				messageCount++;
			}

			option.messages = messages;

			this.options.put(name, option);
		}
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#save()
	 */
	@Override
	public void save() {
		Valid.checkBoolean(!this.legacy, "Cannot save legacy format: " + this.name);

		final SerializedMap map = new SerializedMap();

		map.putIf("Parts", this.options);

		if (!map.isEmpty() && !"{}".equals(map.toString())) { // We serialize null values too but they are not saved
			for (final Map.Entry<String, Object> entry : map.entrySet())
				setNoSave(entry.getKey(), entry.getValue());

			super.save();
		}
	}

	/**
	 * Compile the format for the given message and his message
	 *
	 * @param sender
	 * @param message
	 * @return
	 */
	public SimpleComponent build(CommandSender sender, String message) {
		return this.build(sender, message, new SerializedMap());
	}

	/**
	 * Compile the format for the given message and his message, and insert given variables
	 *
	 * @param sender
	 * @param message
	 * @param variables
	 * @return
	 */
	public SimpleComponent build(CommandSender sender, String message, @NonNull SerializedMap variables) {
		final SimpleComponent component = SimpleComponent.empty();

		for (final FormatOption option : this.options.values())
			buildOption(option, component, sender, message, variables);

		// Add the secret remove code at the front of the message
		component.appendFirst(SimpleComponent.empty().onHover(replaceVariables(sender, variables, "flpm_{message_uuid}" + (sender instanceof Player ? " flps_" + ((Player) sender).getUniqueId() : ""))));

		return component;
	}

	/*
	 * A super long method to build a format part from the given option
	 */
	private void buildOption(FormatOption option, SimpleComponent component, CommandSender sender, String message, @NonNull SerializedMap variables) {
		if (option.getSenderPermission() != null && !PlayerUtil.hasPerm(sender, Replacer.replaceVariables(option.getSenderPermission(), variables)))
			return;

		if (option.getSenderCondition() != null) {
			final Object result = JavaScriptExecutor.run(Variables.replace(Replacer.replaceVariables(option.getSenderCondition(), variables), sender), sender);

			if (result != null) {
				Valid.checkBoolean(result instanceof Boolean, "JavaScript condition must return boolean not " + (result == null ? "null" : result.getClass()) + " for format " + getName());

				if ((boolean) result == false)
					return;
			}
		}

		if (option.getInherit() != null) {
			final Format inheritance = Format.findFormat(option.getInherit());
			Valid.checkBoolean(!inheritance.equals(this), "Cannot inherit the format itself!");

			component.append(inheritance.build(sender, message, variables));
		}

		if (option.getInheritPart() != null) {
			final FormatOption inheritOption = this.options.get(option.getInheritPart());

			if (inheritOption != null)
				buildOption(inheritOption, component, sender, message, variables);
		}

		final String MESSAGE_VARIABLE = "{message}";
		final String formatContent = String.join("\n", option.getMessages());

		// Special case:
		// Replacing {message} to add interactive variables into it
		if (formatContent.contains(MESSAGE_VARIABLE)) {
			final int messageIndex = formatContent.indexOf(MESSAGE_VARIABLE);

			// add what is before message
			final String before = formatContent.substring(0, messageIndex);

			component
					.append(generateGradient(replaceVariables(sender, variables, before), option))
					.viewPermission(option.getReceiverPermission());

			// We have to call this for each component part of the chat message
			addInterativeElements(component, sender, variables, option);

			int lastMatch = 0;
			BaseComponent toInheritFormattingFrom = null;

			// Variables
			final Matcher matcher = Variables.MESSAGE_PLACEHOLDER_PATTERN.matcher(message);

			while (matcher.find()) {
				// Get the non-variable part inside {message}
				final String nonVariablePart = message.substring(lastMatch, matcher.start());

				// Only place it if it exists. This prevents the formatting from being reset to white
				if (!nonVariablePart.isEmpty()) {
					component
							.append(nonVariablePart, toInheritFormattingFrom, false)
							.viewPermission(option.getReceiverPermission());

					addInterativeElements(component, sender, variables, option);
				}

				// Get the variable
				final String variableKey = matcher.group();

				// Special case used for sound notify, return to the formatting before the highlighted word
				if ("[#flpc-1]".equals(variableKey)) {

					// Do not inherit formatting but hide this variable instead. This is because we reuse the
					// formatting before the sound notify took place.

				} else {

					// Copy the message formatting BEFORE the variable that will be used the message that follows the variable itself
					toInheritFormattingFrom = component.getTextComponent();

					if (toInheritFormattingFrom != null && toInheritFormattingFrom.getExtra() != null && !toInheritFormattingFrom.getExtra().isEmpty())
						toInheritFormattingFrom = toInheritFormattingFrom.getExtra().get(toInheritFormattingFrom.getExtra().size() - 1);

					// Replace variables or print them as raw output only when this is not related to sound notify
					if (!"[#flpc-i]".equals(variableKey)) {

						final Variable variable = Variable.findVariable(variableKey.substring(1, variableKey.length() - 1));

						// Build if it exists
						if (variable != null && variable.getType() == Type.MESSAGE)
							variable
									.build(sender, component, variables.asMap())
									.viewPermission(option.getReceiverPermission());

						// Add as an unused key
						else {
							component
									.append(variableKey, false)
									.viewPermission(option.getReceiverPermission());

							addInterativeElements(component, sender, variables, option);
						}
					}
				}

				lastMatch = matcher.end();
			}

			// add what is remaining
			final String remainingFromMatch = message.substring(lastMatch);
			component
					.append(remainingFromMatch, toInheritFormattingFrom, false)
					.viewPermission(option.getReceiverPermission());

			addInterativeElements(component, sender, variables, option);

			// add the rest
			final String afterMessageVariable = formatContent.substring(messageIndex + MESSAGE_VARIABLE.length(), formatContent.length());
			component
					.append(generateGradient(replaceVariables(sender, variables, afterMessageVariable), option))
					.viewPermission(option.getReceiverPermission());

			addInterativeElements(component, sender, variables, option);
		}

		// Simply add the part of the format then
		else {
			component
					.append(generateGradient(replaceVariables(sender, variables, formatContent), option))
					.viewPermission(option.getReceiverPermission());

			addInterativeElements(component, sender, variables, option);
		}
	}

	/*
	 * Decorate the last component part according to the given format option
	 */
	private void addInterativeElements(SimpleComponent component, CommandSender sender, SerializedMap variables, FormatOption option) {
		if (option.getReceiverCondition() != null && !option.getReceiverCondition().isEmpty())
			component.viewCondition(Replacer.replaceVariables(option.getReceiverCondition(), variables));

		if (!Valid.isNullOrEmpty(option.getHoverText()))
			component.onHover(replaceVariables(sender, variables, option.getHoverText()));

		if (option.getHoverItem() != null && !option.getHoverItem().isEmpty()) {
			final Object result = JavaScriptExecutor.run(option.getHoverItem(), sender);

			if (result != null) {
				Valid.checkBoolean(result instanceof ItemStack, "Hover Item must return ItemStack not " + result.getClass() + " for format " + getName());

				component.onHover((ItemStack) result);
			}
		}

		if (option.getOpenUrl() != null && !option.getOpenUrl().isEmpty())
			component.onClickOpenUrl(option.getOpenUrl());

		if (option.getSuggestCommand() != null && !option.getSuggestCommand().isEmpty())
			component.onClickSuggestCmd(replaceVariables(sender, variables, option.getSuggestCommand()));

		if (option.getRunCommand() != null && !option.getRunCommand().isEmpty()) {
			final String runCommand = option.getRunCommand();

			component.onClickRunCmd(replaceVariables(sender, variables, runCommand));
		}

		if (option.getInsertion() != null && !option.getInsertion().isEmpty())
			component.onClickInsert(replaceVariables(sender, variables, option.getInsertion()));
	}

	/*
	 * Replace variables in the message
	 */
	private List<String> replaceVariables(CommandSender sender, SerializedMap variables, List<String> list) {
		// Create a new list instead of changing the given one
		final List<String> replaced = new ArrayList<>();

		for (final String item : list)
			replaced.add(replaceVariables(sender, variables, item));

		return replaced;
	}

	/*
	 * Replace variables in the message
	 */
	private String replaceVariables(CommandSender sender, SerializedMap variables, String message) {

		// Center
		message = message.startsWith("<center>") ? ChatUtil.center(message.substring(8)) : message;

		// Standard ones
		return Variables.replace(message, sender, variables.asMap());
	}

	/*
	 * Automatically add gradient coloring if the format option has it and
	 * Minecraft server supports it
	 */
	private String generateGradient(String string, FormatOption option) {
		if (MinecraftVersion.atLeast(V.v1_16) && option.getGradient() != null) {

			final Tuple<CompChatColor, CompChatColor> tuple = option.getGradient();

			final Color color1 = tuple.getKey().getColor();
			final Color color2 = tuple.getValue().getColor();

			final char[] letters = string.toCharArray();
			String gradient = "";

			ChatColor lastDecoration = null;

			for (int i = 0; i < letters.length; i++) {
				final char letter = letters[i];

				// Support color decoration and insert it manually after each character
				if (letter == ChatColor.COLOR_CHAR && i + 1 < letters.length) {
					final char decoration = letters[i + 1];

					if (decoration == 'k')
						lastDecoration = ChatColor.MAGIC;

					else if (decoration == 'l')
						lastDecoration = ChatColor.BOLD;

					else if (decoration == 'm')
						lastDecoration = ChatColor.STRIKETHROUGH;

					else if (decoration == 'n')
						lastDecoration = ChatColor.UNDERLINE;

					else if (decoration == 'o')
						lastDecoration = ChatColor.ITALIC;

					else if (decoration == 'r')
						lastDecoration = null;

					i++;
					continue;
				}

				final float ratio = (float) i / (float) letters.length;

				final int red = (int) (color2.getRed() * ratio + color1.getRed() * (1 - ratio));
				final int green = (int) (color2.getGreen() * ratio + color1.getGreen() * (1 - ratio));
				final int blue = (int) (color2.getBlue() * ratio + color1.getBlue() * (1 - ratio));

				final Color stepColor = new Color(red, green, blue);

				gradient += CompChatColor.of(stepColor).toString() + (lastDecoration == null ? "" : lastDecoration.toString()) + letters[i];
			}

			return gradient;
		}

		return string;
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Classes
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Represents a part of a chat format
	 */
	@Getter
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	public final static class FormatOption implements ConfigSerializable {

		/**
		 * The message this format prints to the chat
		 */
		private List<String> messages;

		/**
		 * The permission the sender must have to show the part
		 */
		private String senderPermission;

		/**
		 * The permission receiver must have to see the part
		 */
		private String receiverPermission;

		/**
		 * The JavaScript condition that must return true to show to part
		 */
		private String senderCondition;

		/**
		 * The JavaScript condition that must return true to show to part for receiver
		 */
		private String receiverCondition;

		/**
		 * The hover text or null if not set
		 */
		@Nullable
		private List<String> hoverText;

		/**
		 * The JavaScript pointing to a particular {@link ItemStack}
		 */
		@Nullable
		private String hoverItem;

		/**
		 * What URL should be opened on click? Null if none
		 */
		@Nullable
		private String openUrl;

		/**
		 * What command should be suggested on click? Null if none
		 */
		@Nullable
		private String suggestCommand;

		/**
		 * What command should be run on click? Null if none
		 */
		@Nullable
		private String runCommand;

		/**
		 * What text to insert into the chat? Null if none
		 */
		@Nullable
		private String insertion;

		/**
		 * Get what other format should this format part inherit?
		 */
		@Nullable
		private String inherit;

		/**
		 * Get what other format part should this format part inherit?
		 */
		@Nullable
		private String inheritPart;

		/**
		 * Gradient from-to colors
		 */
		@Nullable
		private Tuple<CompChatColor, CompChatColor> gradient;

		/**
		 * Turn this class into a saveable format to the file
		 *
		 * @see ConfigSerializable#serialize()
		 */
		@Override
		public SerializedMap serialize() {
			return SerializedMap.ofArray(
					"Message", this.messages.size() == 1 ? this.messages.get(0) : this.messages,
					"Sender_Permission", this.senderPermission,
					"Receiver_Permission", this.receiverPermission,
					"Sender_Condition", this.senderCondition,
					"Receiver_Condition", this.receiverCondition,
					"Hover", this.hoverText != null && !this.hoverText.isEmpty() ? this.hoverText : null,
					"Hover_Item", this.hoverItem,
					"Open_Url", this.openUrl,
					"Suggest_Command", this.suggestCommand,
					"Run_Command", this.runCommand,
					"Insertion", this.insertion,
					"Inherit", this.inherit,
					"Inherit_Part", this.inheritPart,
					"Gradient", this.gradient != null ? this.gradient.getKey().toSaveableString() + " - " + this.gradient.getValue().toSaveableString() : null);
		}

		public static FormatOption deserialize(SerializedMap map) {
			final FormatOption format = new FormatOption();

			// Use to check for invalid entries
			map.setRemoveOnGet(true);

			format.messages = map.getStringList("Message");

			Valid.checkBoolean(format.messages != null || format.inherit != null, "The key 'Message' or 'Inherit' must be set when creating a format!");
			Valid.checkBoolean(format.messages == null || format.inherit == null, "Either set 'Message' or 'Inherit', not both when creating a format!");

			format.senderPermission = map.getString("Sender_Permission");
			format.receiverPermission = map.getString("Receiver_Permission");
			format.senderCondition = map.getString("Sender_Condition");
			format.receiverCondition = map.getString("Receiver_Condition");
			format.hoverText = map.getStringList("Hover");
			format.hoverItem = map.getString("Hover_Item");
			format.openUrl = map.getString("Open_Url");
			format.suggestCommand = map.getString("Suggest_Command");

			// Educate people that Minecraft only supports running 1 command if they attempt to put a list there
			{
				Object runCommand = map.getObject("Run_Command");

				if (runCommand instanceof List) {
					final List<?> runCommands = (List<?>) runCommand;

					if (!runCommands.isEmpty()) {
						Valid.checkBoolean(runCommands.size() == 1, "Minecraft only supports running 1 command in Run_Command, got: " + runCommand);

						runCommand = runCommands.get(0);
					}
				}

				if (runCommand instanceof String)
					format.runCommand = runCommand.toString();
			}

			format.insertion = map.getString("Insertion");

			if (map.containsKey("Gradient")) {

				// we split {message} to support [item] placeholders so gradients are not possible
				if (format.messages != null)
					for (final String line : format.messages) {
						Valid.checkBoolean(!line.contains("{message}"), "Cannot use Gradient option in format part having '{message}' variable for " + format);
						//Valid.checkBoolean(!Common.hasColors(line), "Cannot use Gradient option in format part having & color codes for " + format);
					}

				final String line = map.getString("Gradient");
				Valid.checkBoolean(line.split(" - ").length == 2, "Invalid 'Gradient' syntax! Usage: <from color> - <to color> (can either be ChatColor or RGB). Got: " + line);

				format.gradient = Tuple.deserialize(line, CompChatColor.class, CompChatColor.class);
			}

			format.inherit = map.getString("Inherit");
			format.inheritPart = map.getString("Inherit_Part");

			Valid.checkBoolean(map.isEmpty(), "Found unrecognized format keys, please remove those: " + map);

			return format;
		}

		/**
		 * Return a FormatOption from the given component
		 *
		 * @param component
		 * @return
		 */
		public static FormatOption fromComponent(BaseComponent component) {
			final FormatOption option = new FormatOption();

			// Import standard options
			option.messages = Arrays.asList(Common.revertColorizing(component.toLegacyText()).split("\n"));
			option.insertion = component.getInsertion();

			// Import click event
			if (component.getClickEvent() != null) {
				final ClickEvent click = component.getClickEvent();
				final ClickEvent.Action action = click.getAction();
				final String value = click.getValue();

				if (action == ClickEvent.Action.OPEN_URL)
					option.openUrl = Common.revertColorizing(value);

				else if (action == ClickEvent.Action.RUN_COMMAND)
					option.runCommand = Common.revertColorizing(value);

				else if (action == ClickEvent.Action.SUGGEST_COMMAND)
					option.suggestCommand = Common.revertColorizing(value);
			}

			// Import hover event
			if (component.getHoverEvent() != null) {
				final HoverEvent hover = component.getHoverEvent();
				final HoverEvent.Action action = hover.getAction();
				final String value = TextComponent.toLegacyText(hover.getValue());

				if (action == HoverEvent.Action.SHOW_TEXT)
					option.hoverText = Arrays.asList(value.split("\n"));
			}

			return option;
		}

		/**
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return serialize().toStringFormatted();
		}
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return obj instanceof Format && ((Format) obj).getName().equals(this.getName());
	}

	/**
	 * @see org.mineacademy.fo.settings.YamlConfig#toString()
	 */
	@Override
	public String toString() {
		return "Format{" + this.name + ", options=" + this.options + "}";
	}

	// ------–------–------–------–------–------–------–------–------–------–------–------–
	// Static
	// ------–------–------–------–------–------–------–------–------–------–------–------–

	/**
	 * Attempt to parse a format or a legacy text if the format by the given name
	 * does not exist we count it as text itself.
	 *
	 * @param formatOrLegacy
	 * @return
	 */
	public static Format parse(@NonNull String formatOrLegacy) {
		if (formatOrLegacy.startsWith("[JSON]"))
			return fromJson(formatOrLegacy.replace("[JSON]", ""));

		final Format format = findFormat(formatOrLegacy);

		return Common.getOrDefault(format, legacy(formatOrLegacy));
	}

	/*
	 * Create a format on the fly from the given text
	 */
	private static Format legacy(String message) {
		final Format format = new Format(message, true);
		final FormatOption option = new FormatOption();

		option.messages = Arrays.asList(message);
		format.options = Common.newHashMap("legacy", option);

		return format;
	}

	/*
	 * Parses the format from the given raw json
	 */
	private static Format fromJson(String json) {
		final BaseComponent[] components = Remain.toComponent(json);

		return fromComponents(components);
	}

	/*
	 * Parses the format from the given components array
	 */
	private static Format fromComponents(BaseComponent[] components) {
		final Format format = new Format("json", true);
		final Map<String, FormatOption> options = new HashMap<>();

		for (int i = 0; i < components.length; i++) {
			final FormatOption option = FormatOption.fromComponent(components[i]);

			options.put("part-" + i, option);
		}

		format.options = options;

		return format;
	}

	/**
	 * @see ConfigItems#loadItems()
	 */
	public static void loadFormats() {
		loadedFormats.loadItems();
	}

	/**
	 * @see ConfigItems#removeItem(org.mineacademy.fo.settings.YamlConfig)
	 */
	public static void removeFormat(final Format format) {
		loadedFormats.removeItem(format);
	}

	/**
	 * @see ConfigItems#isItemLoaded(String)
	 */
	public static boolean isFormatLoaded(final String name) {
		return loadedFormats.isItemLoaded(name);
	}

	/**
	 * @see ConfigItems#findItem(String)
	 */
	public static Format findFormat(@NonNull final String name) {
		return loadedFormats.findItem(name);
	}

	/**
	 * @see ConfigItems#getItems()
	 */
	public static List<Format> getFormats() {
		return loadedFormats.getItems();
	}

	/**
	 * @see ConfigItems#getItemNames()
	 */
	public static List<String> getFormatNames() {
		return loadedFormats.getItemNames();
	}
}
