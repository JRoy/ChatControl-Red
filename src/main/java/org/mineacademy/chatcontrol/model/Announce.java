package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.model.ChatImage;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompBarColor;
import org.mineacademy.fo.remain.CompBarStyle;
import org.mineacademy.fo.remain.CompColor;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;

/**
 * Handles announcements
 */
public final class Announce {

	/**
	 * Process an announcement message from BungeeCord
	 *
	 * @param type
	 * @param message
	 * @param params
	 */
	public static void sendFromBungee(AnnounceType type, String message, SerializedMap params) {
		send(null, type, message, params);
	}

	/**
	 * Send announcement message from the local sender
	 *
	 * @param sender
	 * @param type
	 * @param message
	 * @param params
	 */
	public static void send(@Nullable CommandSender sender, AnnounceType type, String message, SerializedMap params) {
		Consumer<Player> function = null;
		boolean broadcastOnThisServer = true;
		final String serverToBroadcastOn = params.getString("server");

		if (serverToBroadcastOn != null) {
			if (!Settings.Integration.BungeeCord.ENABLED) {
				Messenger.error(sender, Lang.of("Commands.No_BungeeCord"));

				return;
			}

			if (!SyncedCache.doesServerExist(serverToBroadcastOn)) {
				Messenger.error(sender, Lang.of("Commands.Invalid_Server", serverToBroadcastOn, String.join(", ", SyncedCache.getServers())));

				return;
			}

			if (!serverToBroadcastOn.equals(Remain.getServerName()))
				broadcastOnThisServer = false;
		}

		if (type == AnnounceType.CHAT) {
			String[] lines = message.split("\\|");

			if ("raw".equals(params.getString("type"))) {
				Common.broadcast(lines);

				return;

			} else {

				// Try load it up from a file
				if (lines.length == 1 && lines[0].endsWith(".txt")) {
					final File file = FileUtil.getFile(message);

					if (!file.exists()) {
						if (sender != null)
							Messenger.error(sender, Lang.of("Commands.Announce.Invalid_File", file.getPath()));

						return;
					}

					lines = FileUtil.readLines(file).stream().toArray(String[]::new);
				}

				// Add body variable
				String linesVariables = "";

				for (final String line : lines)
					linesVariables += " " + CompColor.fromName(Lang.of("Commands.Announce.Chat_Body_Line_Color")).getChatColor() + line + "\n";

				// Add space variables
				String spaceVariables = "";

				for (int i = lines.length; i < 6; i++)
					spaceVariables += "&r \n";

				// Compile the  message to announce
				final String fullMessage = Lang.of("Commands.Announce.Chat_Body")
						.replace("{type}", Lang.ofScript("Commands.Announce.Type", SerializedMap.of("bungee", BungeeCord.ENABLED)))
						.replace("{lines}", linesVariables)
						.replace("{remaining_space}", spaceVariables);

				// Broadcast
				function = player -> {
					for (final String line : fullMessage.split("\n"))
						Common.tellNoPrefix(player, Variables.replace(line, player));

					Settings.Announcer.CHAT_SOUND.play(player);
				};

				if (sender != null)
					BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, message, params);
			}
		}

		else if (type == AnnounceType.IMAGE) {

			Valid.checkBoolean(params.containsKey("imageFile") || params.containsKey("imageLines"),
					"Announcement type IMAGE lacked both image file and image lines! Got: " + params);

			final File imageFile = params.containsKey("imageFile") ? FileUtil.getFile("images/" + params.getString("imageFile")) : null;
			final String[] imageLines = params.containsKey("imageLines") ? Common.toArray((List<String>) params.getObject("imageLines")) : null;
			final int height = params.getInteger("height");

			try {
				final ChatImage image;

				// Load the image. When sending from BungeeCord we send the lines right away
				// because the image file may not exist on another server
				if (imageFile != null) {
					image = ChatImage.fromFile(imageFile, height, ChatImage.Type.DARK_SHADE);

					image.appendText(message.split("\\|"));

				} else
					image = ChatImage.fromLines(imageLines);

				// Send to all receivers
				function = player -> image.sendToPlayer(player);

				if (sender != null && imageFile != null)
					BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, message, SerializedMap.ofArray(
							"height", height,
							"imageLines", Arrays.asList(image.getLines())));

			} catch (final Exception ex) {
				ex.printStackTrace();

				Messenger.error(sender, Lang.of("Commands.Announce.Image_Error", imageFile.toPath().toString(), ex.toString()));
				return;
			}
		}

		else if (type == AnnounceType.TITLE) {
			final String[] parts = message.split("\\|");
			final String title = parts[0];
			final String subtitle = parts.length > 1 ? Common.joinRange(1, parts) : null;

			final int stay = params.getInteger("stay", 2 * 20);
			final int fadeIn = params.getInteger("fadein", 20);
			final int fadeOut = params.getInteger("fadeout", 20);

			function = player -> Remain.sendTitle(player, fadeIn, stay, fadeOut, Variables.replace(title, player),
					subtitle == null || subtitle.isEmpty() || "null".equals(subtitle) ? null : Variables.replace(subtitle, player));

			if (sender != null)
				BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, title + "|" + subtitle, SerializedMap.ofArray("stay", stay, "fadein", fadeIn, "fadeout", fadeOut));
		}

		else if (type == AnnounceType.ACTIONBAR) {
			function = player -> Remain.sendActionBar(player, Variables.replace(message, player));

			if (sender != null)
				BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, message, new SerializedMap());

		} else if (type == AnnounceType.BOSSBAR) {
			final int time = params.getInteger("time", 5);
			final CompBarColor color = params.get("color", CompBarColor.class, CompBarColor.WHITE);
			final CompBarStyle style = params.get("style", CompBarStyle.class, CompBarStyle.SOLID);

			function = player -> Remain.sendBossbarTimed(player, Variables.replace(message, player), time, color, style);

			if (sender != null)
				BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, message, SerializedMap.ofArray("time", time, "color", color, "style", style));
		}

		else if (type == AnnounceType.TOAST) {
			final CompMaterial icon = params.getMaterial("icon", CompMaterial.BOOK);

			function = player -> Remain.sendToast(player, Variables.replace(message, player), icon);

			if (sender != null)
				BungeeUtil.tellBungee(BungeePacket.ANNOUNCEMENT, type, message, SerializedMap.ofArray("icon", icon));
		}

		if (function == null) {
			if (sender != null) {
				Messenger.error(sender, Lang.of("Commands.Announce.Invalid_Type", type));

				return;
			}

			throw new FoException("Does not know how to broadcast '" + type + "'.");
		}

		else if (sender != null && !(sender instanceof Player))
			Common.tell(sender, Lang.of("Commands.Announce.Success"));

		// Iterate and broadcast to players who enabled it
		if (broadcastOnThisServer)
			for (final Player player : Remain.getOnlinePlayers()) {
				final PlayerCache cache = PlayerCache.from(player);

				if (Settings.Toggle.APPLY_ON.contains(Toggle.ANNOUNCEMENT) && cache.isIgnoringPart(Toggle.ANNOUNCEMENT)) {
					Log.logOnce("timed-no-perm", "Not showing timed message broadcast to " + player + " because he has toggled announcements off with /chc toggle.");

					continue;
				}

				if (!PlayerUtil.hasPerm(player, Permissions.Receive.ANNOUNCER)) {
					Log.logOnce("timed-no-perm", "Not showing timed message broadcast to " + player + " because he lacks " + Permissions.Receive.ANNOUNCER + " permission.");

					continue;
				}

				function.accept(player);
			}
		else
			Messenger.info(sender, Lang.of("Commands.Announce.Success_Network", serverToBroadcastOn));
	}

	/**
	 * For convenience sake, this models the possible parameters this command can have.
	 */
	@Getter
	public enum AnnounceType {

		/**
		 * Broadcast a simple chat message
		 */
		CHAT(V.v1_3_AND_BELOW, "chat", "c"),

		/**
		 * Broadcast an image!
		 */
		IMAGE(V.v1_3_AND_BELOW, "image", "img"),

		/**
		 * Broadcast a title
		 */
		TITLE(V.v1_7, "title", "t"),

		/**
		 * Broadcast action bar
		 */
		ACTIONBAR(V.v1_8, "actionbar", "ab"),

		/**
		 * Broadcast boss bar
		 */
		BOSSBAR(V.v1_9, "bossbar", "bb"),

		/**
		 * Broadcast a toast message
		 */
		TOAST(V.v1_12, "toast", "tt"),;

		/**
		 * The minimum required MC version
		 */
		private final V minimumVersion;

		/**
		 * All possible labels to use
		 */
		private final List<String> labels;

		/*
		 * Create a new instance with vararg array -- cannot use lombok for that
		 */
		AnnounceType(V minimumVersion, String... labels) {
			this.minimumVersion = minimumVersion;
			this.labels = Arrays.asList(labels);
		}

		/**
		 * Get the param label
		 *
		 * @return the label
		 */
		public String getLabel() {
			return labels.get(0);
		}

		/**
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return this.getLabel();
		}

		/**
		 * Return a list of all available params for the current MC version
		 *
		 * @return
		 */
		public static List<AnnounceType> getAvailableParams() {
			return Arrays.asList(values()).stream().filter(param -> MinecraftVersion.atLeast(param.getMinimumVersion())).collect(Collectors.toList());
		}
	}
}
