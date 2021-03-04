package org.mineacademy.chatcontrol.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Announce;
import org.mineacademy.chatcontrol.model.Announce.AnnounceType;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.CommandException;
import org.mineacademy.fo.remain.CompBarColor;
import org.mineacademy.fo.remain.CompBarStyle;
import org.mineacademy.fo.remain.CompMaterial;

public final class ChatControlAnnounce extends ChatControlSubCommand {

	public ChatControlAnnounce() {
		super("announce/a/broadcast/bc");

		setUsage(Lang.of("Commands.Announce.Usage"));
		setDescription(Lang.of("Commands.Announce.Description"));
		setMinArguments(1);
		setPermission("chatcontrol.command.announce");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		final List<String> help = new ArrayList<>();

		help.add(Lang.of("Commands.Announce.Usage_Chat"));
		help.add(Lang.of("Commands.Announce.Usage_Image"));

		if (MinecraftVersion.atLeast(V.v1_7))
			help.add(Lang.of("Commands.Announce.Usage_Title"));

		if (MinecraftVersion.atLeast(V.v1_8))
			help.add(Lang.of("Commands.Announce.Usage_Actionbar"));

		if (MinecraftVersion.atLeast(V.v1_9))
			help.add(Lang.of("Commands.Announce.Usage_Bossbar"));

		if (MinecraftVersion.atLeast(V.v1_12))
			help.add(Lang.of("Commands.Announce.Usage_Toast"));

		help.addAll(Lang.ofList("Commands.Announce.Usage_Footer"));

		return Common.toArray(help);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		final AnnounceType type = findParam(args[0]);
		checkPerm(Permissions.Command.ANNOUNCE.replace("{type}", type.getLabel()));

		if (type == AnnounceType.IMAGE)
			checkArgs(3, Lang.of("Commands.Announce.Usage_Image"));
		else
			checkArgs(2, Lang.of("Commands.Announce.No_Type"));

		final SerializedMap params = new SerializedMap();
		final String message = mapParams(type, params, joinArgs(1));

		if (type != AnnounceType.IMAGE)
			checkBoolean(!message.isEmpty(), Lang.of("Commands.Announce.Empty"));

		Announce.send(sender, type, message, params);
	}

	/*
	 * Map chat key:value pairs parameters
	 */
	private String mapParams(AnnounceType type, SerializedMap map, String line) {
		final Matcher matcher = Pattern.compile("\\b[a-zA-Z]+\\:[a-zA-Z0-9_]+\\b").matcher(line);

		if (type == AnnounceType.IMAGE) {
			final int height = findNumber(2, Lang.of("Commands.Announce.Image_Lines"));
			checkBoolean(height >= 2 && height <= 35, Lang.of("Commands.Announce.Invalid_Image_Height", 2, 35));

			final File imageFile = FileUtil.getFile("images/" + args[1]);
			checkBoolean(imageFile.exists(), Lang.of("Commands.Announce.Invalid_Image", imageFile.toPath().toString()));

			map.put("height", height);
			map.put("imageFile", imageFile.getName());

			return Common.joinRange(3, args);
		}

		while (matcher.find()) {
			final String word = matcher.group();
			final String[] split = word.split("\\:");

			if (split.length != 2)
				continue;

			final String key = split[0];
			Object value = Common.joinRange(1, split);

			if ("server".equals(key)) {
				// ok
			}

			else if (type == AnnounceType.CHAT) {
				if ("type".equals(key)) {
					checkBoolean("raw".equals(value), Lang.of("Commands.Announce.Invalid_Raw_Type"));

					// ok
				} else
					returnTell(Lang.of("Commands.Invalid_Param_Short", word));
			}

			else if (type == AnnounceType.TITLE && MinecraftVersion.atLeast(V.v1_7)) {
				if ("stay".equals(key) || "fadein".equals(key) || "fadeout".equals(key)) {
					try {
						value = Integer.parseInt(value.toString());

					} catch (final NumberFormatException ex) {
						returnTell(Lang.of("Commands.Announce.Invalid_Time_Ticks"));
					}

				} else
					returnTell(Lang.of("Commands.Invalid_Param_Short", word));
			}

			else if (type == AnnounceType.BOSSBAR && MinecraftVersion.atLeast(V.v1_9)) {

				if ("time".equals(key)) {
					try {
						value = Integer.parseInt(value.toString());

					} catch (final NumberFormatException ex) {
						returnTell(Lang.of("Commands.Announce.Invalid_Time_Seconds"));
					}

				} else if ("color".equals(key)) {
					try {
						value = CompBarColor.fromKey(value.toString());

					} catch (final IllegalArgumentException ex) {
						returnTell(Lang.of("Commands.Announce.Invalid_Key", key, value, Common.join(CompBarColor.values())));
					}

				} else if ("style".equals(key)) {
					try {
						value = CompBarStyle.fromKey(value.toString());

					} catch (final IllegalArgumentException ex) {
						returnTell(Lang.of("Commands.Announce.Invalid_Key", key, value, Common.join(Common.convert(CompBarStyle.values(), CompBarStyle::getShortKey))));
					}

				} else
					returnTell(Lang.of("Commands.Invalid_Param_Short", word));

			} else if (type == AnnounceType.TOAST && MinecraftVersion.atLeast(V.v1_12)) {

				if ("icon".equals(key)) {
					final CompMaterial material = CompMaterial.fromString(value.toString());
					checkNotNull(material, Lang.of("Commands.Invalid_Material", key));

					value = material;

				} else
					returnTell(Lang.of("Commands.Invalid_Param_Short", word));

			} else
				returnTell(Lang.of("Commands.Invalid_Param_Short", word));

			map.override(key, value);
			line = line.replace(word + (line.contains(word + " ") ? " " : ""), "").trim();
		}

		return line.trim();
	}

	/*
	 * Lookup a parameter by its label and automatically return on error
	 */
	private AnnounceType findParam(String label) {

		for (final AnnounceType param : AnnounceType.values())
			if (param.getLabels().contains(label)) {
				checkBoolean(MinecraftVersion.atLeast(param.getMinimumVersion()), "Sending " + param.getLabel() + " messages requires Minecraft " + param.getMinimumVersion() + " or greater.");

				return param;
			}

		returnTell(Lang.of("Commands.Invalid_Type", label, Common.join(AnnounceType.getAvailableParams())));
		return null;
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord(AnnounceType.getAvailableParams());

		if (args.length >= 2) {
			final String lastWord = args[args.length - 1];

			AnnounceType param;

			try {
				param = findParam(args[0]);

			} catch (final CommandException ex) {
				// Do not send tab complete error message to player
				return NO_COMPLETE;
			}

			if (param == AnnounceType.TITLE && MinecraftVersion.atLeast(V.v1_7))
				return completeLastWord("stay:", "fadein:", "fadeout:");

			else if (param == AnnounceType.BOSSBAR && MinecraftVersion.atLeast(V.v1_9)) {
				if (lastWord.startsWith("color:"))
					return completeLastWord(Common.convert(CompBarColor.values(), color -> "color:" + color.toString()));

				else if (lastWord.startsWith("style:"))
					return completeLastWord(Common.convert(CompBarStyle.values(), color -> "style:" + color.toString()));

				return completeLastWord("time:", "color:", "style:");
			}

			else if (param == AnnounceType.TOAST && MinecraftVersion.atLeast(V.v1_12)) {
				if (lastWord.startsWith("icon:"))
					return completeLastWord(Common.convert(CompMaterial.values(), mat -> "icon:" + mat.toString()));

				return completeLastWord("icon:");
			}

			else if (param == AnnounceType.IMAGE) {
				if (args.length == 2) {
					File file = FileUtil.getFile("images/" + args[1]);
					String[] files = file.list();

					if (files == null) {
						final String path = file.toPath().toString();
						final int lastDir = path.lastIndexOf('/');

						file = new File(lastDir == -1 ? path : path.substring(0, lastDir));
						files = file.list();
					}

					if (file != null)
						return completeLastWord(file.list());
				}

				else if (args.length == 3)
					return completeLastWord("6", "10", "20");
			}
		}

		return NO_COMPLETE;
	}
}
