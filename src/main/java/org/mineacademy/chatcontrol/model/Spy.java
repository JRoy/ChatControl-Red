package org.mineacademy.chatcontrol.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.chatcontrol.api.SpyEvent;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.ItemUtil;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Class holding methods for the /spy command
 */
public final class Spy {

	/**
	 * The type of spying what this is
	 */
	private final Type type;

	/**
	 * The initiator of the spy
	 */
	private final CommandSender initiator;

	/**
	 * The message associated with the spy
	 */
	private final String message;

	/**
	 * The variables to replace in {@link #message}
	 */
	private final SerializedMap variables = new SerializedMap();

	/**
	 * The players who are exempted from seeing {@link #message}
	 */
	private final Set<UUID> ignoredPlayers = new HashSet<>();

	/**
	 * The channel name associated with the spy
	 */
	@Nullable
	private String channelName;

	/**
	 * Does the {@link #channelName} (if any) has bungee option explicitly enabled?
	 */
	private boolean channelBungee = true;

	/**
	 * The custom format associated with this spy
	 * If null, {@link Type#getFormat()} is used
	 */
	@Nullable
	private String format;

	/*
	 * Create a new spy instance that sends a message to spying players
	 */
	private Spy(@NonNull Type type, @NonNull CommandSender initiator, @NonNull String message) {
		Valid.checkNotNull(type.getFormat(), "Type " + type + " has no format!");

		this.type = type;
		this.initiator = initiator;
		this.message = message;
	}

	/**
	 * Assign a channel with this spy
	 *
	 * @param channel
	 * @return
	 */
	public Spy channel(@NonNull Channel channel) {
		this.format = channel.getSpyFormat();

		return this.channel(channel.getName());
	}

	/**
	 * Assign a channel with this spy
	 *
	 * @param channelName
	 * @return
	 */
	public Spy channel(String channelName) {
		this.channelName = channelName != null && !channelName.isEmpty() ? channelName : null;

		return this;
	}

	/**
	 * Set a custom format associated with this spy
	 *
	 * @param format
	 * @return
	 */
	public Spy format(String format) {
		this.format = format;

		return this;
	}

	/**
	 * Add variables to the spy
	 *
	 * @param key
	 * @param value
	 * @return
	 */
	public Spy variables(Object... array) {
		this.variables.putArray(array);

		return this;
	}

	/**
	 * Add variables to the spy
	 *
	 * @param map
	 * @return
	 */
	public Spy variables(SerializedMap map) {
		this.variables.put(map);

		return this;
	}

	/**
	 * Add ignore player to the list
	 *
	 * @param uuid
	 * @return
	 */
	public Spy ignore(UUID uuid) {
		this.ignoredPlayers.add(uuid);

		return this;
	}

	/**
	 * Add the set to ignored receivers list
	 *
	 * @param uuids
	 * @return
	 */
	public Spy ignore(Set<UUID> uuids) {
		this.ignoredPlayers.addAll(uuids);

		return this;
	}

	/**
	 * Broadcast spying message to spying players
	 */
	public void broadcast() {

		if (!canBroadcast())
			return;

		// Place default variables
		if (this.initiator instanceof Player)
			this.variables.override("location", Common.shortLocation(((Player) this.initiator).getLocation()));

		if (this.channelName != null)
			this.variables.override("channel", channelName);

		// Add ignore self
		if (this.initiator instanceof Player)
			this.ignoredPlayers.add(((Player) this.initiator).getUniqueId());

		final String spyFormat = this.format != null ? this.format : this.type.getFormat();

		if ("none".equalsIgnoreCase(spyFormat) || spyFormat.isEmpty())
			return;

		// Build component
		final boolean noPrefix = spyFormat.startsWith("@noprefix ");

		final SimpleComponent main = Format.parse(noPrefix ? spyFormat.substring(9).trim() : spyFormat).build(this.initiator, this.message, this.variables);
		final SimpleComponent prefix = Format.parse(Settings.Spy.PREFIX).build(this.initiator, this.message, this.variables);
		final SimpleComponent compounded = noPrefix ? main : main.appendFirst(prefix);

		// Send
		final List<Player> spyingPlayers = this.channelName != null ? getOnlineSpyingChannelPlayers(this.channelName) : getOnlineSpyingPlayers(this.type);

		// Remove ignored
		spyingPlayers.removeIf(spyingPlayer -> this.ignoredPlayers.contains(spyingPlayer.getUniqueId()));

		// API call
		final SpyEvent event = new SpyEvent(this.type, this.initiator, this.message, new HashSet<>(spyingPlayers));

		if (Common.callEvent(event)) {

			// Update data from event
			spyingPlayers.clear();
			spyingPlayers.addAll(event.getRecipients());

			// Broadcast
			for (final Player spyingPlayer : spyingPlayers) {
				if (this.initiator instanceof Player && spyingPlayer.equals(this.initiator))
					continue;

				compounded.send(spyingPlayer);
				this.ignoredPlayers.add(spyingPlayer.getUniqueId());
			}

			// Send home to BungeeCord!
			if (BungeeCord.ENABLED && channelBungee)
				BungeeUtil.tellBungee(
						BungeePacket.SPY,
						this.type.getKey(),
						this.channelName == null ? "" : this.channelName,
						this.message,
						compounded.serialize().toJson(),
						Remain.toJson(Common.convert(this.ignoredPlayers, UUID::toString)));
		}
	}

	/*
	 * Return true/false whether or not to send this spy broadcast
	 */
	private boolean canBroadcast() {

		// Globally disabled
		if (!Settings.Spy.APPLY_ON.contains(this.type))
			return false;

		// Bypass permission
		if (PlayerUtil.hasPerm(this.initiator, Permissions.Bypass.SPY)) {
			Log.logOnce("spy-bypass", "Note: Not sending " + this.initiator.getName() + "'s " + type + " to spying players because he had '" + Permissions.Bypass.SPY + "' permission." +
					" Player messages with such permission are not spied on. To disable that, give him this permission as negative (a false value if using LuckPerms).");

			return false;
		}

		// Command exempted
		if (this.type == Type.COMMAND) {
			final String label = this.message.split(" ")[0];

			if (!Settings.Spy.COMMANDS.isInList(label))
				return false;
		}

		return true;
	}

	/* ------------------------------------------------------------------------------- */
	/* Players */
	/* ------------------------------------------------------------------------------- */

	/*
	 * Return list of online spying players in channel
	 */
	private static List<Player> getOnlineSpyingChannelPlayers(String channelName) {
		final List<Player> spying = new ArrayList<>();

		for (final Player online : Remain.getOnlinePlayers())
			if (PlayerCache.from(online).isSpyingChannel(channelName) && PlayerUtil.hasPerm(online, Permissions.Command.SPY))
				spying.add(online);

		return spying;
	}

	/*
	 * Return list of online spying players
	 */
	private static List<Player> getOnlineSpyingPlayers(Spy.Type type) {
		final List<Player> spying = new ArrayList<>();

		for (final Player online : Remain.getOnlinePlayers())
			if (PlayerCache.from(online).getSpyingSectors().contains(type) && PlayerUtil.hasPerm(online, Permissions.Command.SPY))
				spying.add(online);

		return spying;
	}

	/* ------------------------------------------------------------------------------- */
	/* Sending */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Broadcast a PM
	 *
	 * @param sender
	 * @param senderCache
	 * @param receiverCache
	 * @param message
	 */
	public static void broadcastPrivateMessage(CommandSender sender, PlayerCache senderCache, SyncedCache receiverCache, String message) {
		final Spy spy = from(Type.PRIVATE_MESSAGE, sender, message);

		spy.variables(
				"sender", senderCache.getPlayerName(),
				"receiver", receiverCache.getPlayerName());

		spy.ignore(receiverCache.getUniqueId());

		spy.broadcast();
	}

	/**
	 * Broadcast a mail
	 *
	 * @param initiator
	 * @param title
	 * @param receiverNames
	 * @param receivers
	 * @param mailId
	 */
	public static void broadcastMail(Player initiator, String title, Set<String> receiverNames, Set<UUID> receivers, UUID mailId) {
		final Spy spy = from(Type.MAIL, initiator, "");

		spy.variables(
				"sender", initiator,
				"receivers", Common.join(receiverNames),
				"mail_title", title,
				"mail_uuid", mailId.toString());

		spy.ignore(receivers);

		spy.broadcast();
	}

	/**
	 * Broadcast a sign being edited to all spying players
	 *
	 * @param initiator
	 * @param lines joined lines with \n character
	 */
	public static void broadcastSign(Player initiator, String[] lines) {
		final Spy spy = from(Type.SIGN, initiator, String.join("\n", lines).replace("\n", " "));

		spy.variables(
				"sign_lines", String.join("\n", lines),
				"line_1", lines[0],
				"line_2", lines.length > 1 ? lines[1] : "",
				"line_3", lines.length > 2 ? lines[2] : "",
				"line_4", lines.length > 3 ? lines[3] : "");

		spy.broadcast();
	}

	/**
	 * Broadcast a book being edited to all spying players
	 *
	 * @param initiator
	 * @param book
	 * @param uuid the unique ID of the book used for display
	 */
	public static void broadcastBook(Player initiator, Book book, UUID uuid) {
		final Spy spy = from(Type.BOOK, initiator, "");

		spy.variables(
				"book_uuid", uuid.toString(),
				"author", book.getAuthor(),
				"content", String.join("\n", book.getPages()),
				"title", Common.getOrDefaultStrict(book.getTitle(), Lang.of("Commands.Spy.Book_Untitled")));

		spy.broadcast();
	}

	/**
	 * Broadcast an item just renamed (not too long ago) on anvil to all spying players
	 *
	 * @param initiator
	 * @param item
	 */
	public static void broadcastAnvil(Player initiator, ItemStack item) {

		// Copy and use the hand item in Hover_Item script to show the item actually not in hands,
		// but on anvil
		final ItemStack handClone = initiator.getItemInHand();
		initiator.setItemInHand(item);

		try {
			final Spy spy = from(Type.ANVIL, initiator, "");

			spy.variables(
					"item_name", item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : Remain.getI18NDisplayName(item),
					"item_type", ItemUtil.bountify(item.getType()),
					"item_stack", item);

			spy.broadcast();

		} finally {
			initiator.setItemInHand(handClone);
		}
	}

	/**
	 * Broadcast a message from a chat channel to all recipients
	 *
	 * @param sender
	 * @param message
	 * @param recipients
	 */
	public static void broadcastChannel(Channel channel, CommandSender sender, String message, Set<UUID> ignoredPlayers, SerializedMap variables) {
		final Spy spy = from(Type.CHAT, sender, message);

		spy.ignore(ignoredPlayers);
		spy.variables(variables);
		spy.format(channel.getSpyFormat());

		spy.channelBungee = channel.isBungee();

		spy.broadcast();
	}

	/**
	 * Broadcast a command to spying players
	 *
	 * @param sender
	 * @param command
	 */
	public static void broadcastCommand(CommandSender sender, String command) {
		final Spy spy = from(Type.COMMAND, sender, command);

		spy.broadcast();
	}

	/**
	 * Broadcasts a chat message with a custom format to spying players
	 *
	 * @param sender
	 * @param message
	 * @param format
	 * @param variables
	 */
	public static void broadcastCustomChat(CommandSender sender, String message, String format, SerializedMap variables) {
		final Spy spy = from(Type.CHAT, sender, message);

		spy.format(format);
		spy.variables(variables);

		spy.broadcast();
	}

	/**
	 * Broadcast a type message to all spying players
	 *
	 * @param type
	 * @param initiator
	 * @param message
	 */
	private static Spy from(Type type, CommandSender initiator, String message) {
		return new Spy(type, initiator, message);
	}

	/**
	 * Processes a spy message from BungeeCord
	 *
	 * @param type
	 * @param channelName
	 * @param message
	 * @param component
	 * @param ignoredPlayers
	 */
	public static void broadcastFromBungee(Type type, String channelName, String message, SimpleComponent component, Set<UUID> ignoredPlayers) {

		if (!Settings.Spy.APPLY_ON.contains(type))
			return;

		if (type == Type.COMMAND) {
			final String label = message.split(" ")[0];

			if (!Settings.Spy.COMMANDS.isInList(label))
				return;
		}

		// Send
		final Channel channel = !channelName.isEmpty() ? Channel.findChannel(channelName) : null;
		final List<Player> spyingPlayers = channel != null ? getOnlineSpyingChannelPlayers(channelName) : getOnlineSpyingPlayers(type);

		// Remove ignored
		spyingPlayers.removeIf(spyingPlayer -> {
			if (ignoredPlayers != null && ignoredPlayers.contains(spyingPlayer.getUniqueId()))
				return true;

			if (channel != null && channel.isInChannel(spyingPlayer))
				return true;

			return false;
		});

		final SpyEvent event = new SpyEvent(type, null, message, new HashSet<>(spyingPlayers));

		// API call
		if (Common.callEvent(event)) {

			// Update data from event
			message = event.getMessage();
			spyingPlayers.clear();
			spyingPlayers.addAll(event.getRecipients());

			// Broadcast
			component.send(spyingPlayers);
		}
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a rule type
	 */
	@RequiredArgsConstructor
	public enum Type {

		/**
		 * Spying channel messages
		 */
		CHAT("chat") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_CHAT;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Chat");
			}
		},

		/**
		 * Spying player commands
		 */
		COMMAND("command") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_COMMAND;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Command");
			}
		},

		/**
		 * Your mom n NSA spying private conversations yay!
		 */
		PRIVATE_MESSAGE("private_message") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_PRIVATE_MESSAGE;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Private_Message");
			}
		},

		/**
		 * Spying mails when sent
		 */
		MAIL("mail") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_MAIL;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Mail");
			}
		},

		/**
		 * Spying signs
		 */
		SIGN("sign") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_SIGN;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Sign");
			}
		},

		/**
		 * Spying writing to books
		 */
		BOOK("book") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_BOOK;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Book");
			}
		},

		/**
		 * Spying items when renamed
		 */
		ANVIL("anvil") {
			@Override
			public String getFormat() {
				return Settings.Spy.FORMAT_ANVIL;
			}

			@Override
			public String getLocalized() {
				return Lang.of("Commands.Spy.Type_Anvil");
			}
		},

		;

		/**
		 * The saveable non-obfuscated key
		 */
		@Getter
		private final String key;

		/**
		 * Return the format used for the given spy type
		 *
		 * @return
		 */
		public abstract String getFormat();

		/**
		 * The messages_en.yml yummy dummy key
		 *
		 * @return
		 */
		public abstract String getLocalized();

		/**
		 * Attempt to load this class from the given config key
		 *
		 * @param key
		 * @return
		 */
		public static Type fromKey(String key) {
			for (final Type mode : values())
				if (mode.key.equalsIgnoreCase(key))
					return mode;

			throw new IllegalArgumentException("No such spying type: " + key + ". Available: " + Common.join(values()));
		}

		/**
		 * Returns {@link #getKey()}
		 */
		@Override
		public String toString() {
			return this.key;
		}
	}
}
