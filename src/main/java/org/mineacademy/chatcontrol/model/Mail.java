package org.mineacademy.chatcontrol.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.model.Bungee.BungeePacket;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.Integration.BungeeCord;
import org.mineacademy.fo.BungeeUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an email that can be send to players
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Mail implements ConfigSerializable {

	/**
	 * The unique ID of this mail
	 */
	@Getter
	private UUID uniqueId;

	/**
	 * The sender name
	 */
	@Getter
	private UUID sender;

	/**
	 * Did the sender mark this email as deleted?
	 */
	@Getter
	@Setter
	private boolean senderDeleted;

	/**
	 * The recipients for the email then flag if they read it and the time when they did
	 */
	private List<Recipient> recipients;

	/**
	 * The book holding the mail copy
	 */
	@Getter
	private Book body;

	/**
	 * The date of which it has been fired!
	 */
	@Getter
	private long sentDate;

	/**
	 * Is the email an autoreply?
	 */
	@Getter
	private boolean autoReply;

	/**
	 * Reads this email for player (open book)
	 *
	 * @param player
	 */
	public void open(Player player) {
		this.body.open(player);
	}

	/*
	 * Register this email as sent and notify online recipients
	 */
	private void sendAndNotify() {
		final ServerCache serverCache = ServerCache.getInstance();
		final String warnMessage = Lang.of("Commands.Mail.New_Notification", this.getSenderName()).replace("{prefix_warn}", Messenger.getWarnPrefix());

		// Store in database
		serverCache.addMail(this);

		// Notify network
		Bungee.syncMail(this);

		// Notify recipients
		for (final Recipient recipient : this.recipients) {
			final Player onlineRecipient = Remain.getPlayerByUUID(recipient.getUniqueId());

			// Only notify those that actually can read their email
			if (onlineRecipient != null && PlayerUtil.hasPerm(onlineRecipient, Permissions.Command.MAIL))
				Common.tellNoPrefix(onlineRecipient, warnMessage);

			else if (BungeeCord.ENABLED)
				BungeeUtil.tellBungee(BungeePacket.PLAIN_MESSAGE, recipient.getUniqueId(), warnMessage);
		}
	}

	/**
	 * Return true if this mail can be deleted
	 *
	 * @return
	 */
	public boolean canDelete() {

		// Remove if sender and all recipients have removed it
		if (this.isSenderDeleted()) {
			boolean allRecipientsDeleted = true;

			for (final Recipient recipient : this.getRecipients())
				if (!recipient.isMarkedDeleted()) {
					allRecipientsDeleted = false;

					break;
				}

			if (allRecipientsDeleted)
				return true;
		}

		// Remove if too old
		if (this.getSentDate() < System.currentTimeMillis() - (Settings.CLEAR_DATA_IF_INACTIVE.getTimeSeconds() * 1000))
			return true;

		return false;
	}

	/**
	 * Return the title for this mail or null if not yet set
	 *
	 * @return
	 */
	public String getTitle() {
		return this.body.getTitle();
	}

	/**
	 * Get sender name
	 *
	 * @return
	 */
	public String getSenderName() {
		return this.body.getAuthor();
	}

	/**
	 * Return the senders this email is send to
	 *
	 * @return the recipients
	 */
	public List<Recipient> getRecipients() {
		return Collections.unmodifiableList(this.recipients);
	}

	/**
	 * Return if the given recipient read the mail
	 *
	 * @param recipientId
	 * @return
	 */
	public boolean isReadBy(UUID recipientId) {
		final Recipient recipient = findRecipient(recipientId);

		return recipient != null && recipient.hasOpened();
	}

	/**
	 * Find mails recipient by uid
	 *
	 * @param recipientId
	 * @return
	 */
	public Recipient findRecipient(UUID recipientId) {
		for (final Recipient recipient : this.recipients)
			if (recipient.getUniqueId().equals(recipientId))
				return recipient;

		return null;
	}

	/**
	 * @see org.mineacademy.fo.model.ConfigSerializable#serialize()
	 */
	@Override
	public SerializedMap serialize() {
		final SerializedMap map = new SerializedMap();

		map.put("UUID", this.uniqueId);
		map.put("Sender", this.sender);
		map.putIf("Sender_Deleted", this.senderDeleted);
		map.put("Recipients", this.recipients);
		map.put("Body", this.body);
		map.put("Send_Date", this.sentDate);
		map.putIf("Auto_Reply", this.autoReply);

		return map;
	}

	/**
	 * Return this mail as a JSON string
	 *
	 * @return
	 */
	public String toJson() {
		return serialize().toJson();
	}

	/**
	 * Turn a config section map into an email
	 *
	 * @param map
	 * @return
	 */
	public static Mail deserialize(SerializedMap map) {
		final Mail mail = new Mail();

		mail.uniqueId = map.get("UUID", UUID.class);
		mail.sender = map.get("Sender", UUID.class);
		mail.senderDeleted = map.getBoolean("Sender_Deleted", false);
		mail.recipients = map.getList("Recipients", Recipient.class);
		mail.body = map.get("Body", Book.class);
		mail.sentDate = map.getLong("Send_Date", 0L);
		mail.autoReply = map.getBoolean("Auto_Reply", false);

		return mail;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Mail " + this.serialize().toStringFormatted();
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Create, store mail in caches, send it n notify recipient if ze is online
	 *
	 * @param sender
	 * @param recipient
	 * @param body
	 */
	public static Mail sendAutoReply(UUID sender, UUID recipient, Book body) {
		return send(sender, new HashSet<>(Arrays.asList(recipient)), body, true);
	}

	/**
	 * Create, store mail in caches, send it n notify online recipients
	 *
	 * @param sender
	 * @param recipients
	 * @param body
	 *
	 * @return
	 */
	public static Mail send(UUID sender, Set<UUID> recipients, Book body) {
		return send(sender, recipients, body, false);
	}

	/*
	 * Create, store mail in caches, send it n notify online recipients
	 */
	private static Mail send(UUID sender, Set<UUID> recipients, Book body, boolean autoReply) {
		final Mail mail = new Mail();

		mail.uniqueId = UUID.randomUUID();
		mail.sender = sender;
		mail.recipients = Common.convert(recipients, Recipient::create);
		mail.body = body;
		mail.sentDate = System.currentTimeMillis();
		mail.autoReply = autoReply;

		mail.sendAndNotify();

		return mail;
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * A simple email recipient
	 */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Recipient implements ConfigSerializable {

		/**
		 * The recipient unique ID
		 */
		@Getter
		private final UUID uniqueId;

		/**
		 * Did the recipient open this email?
		 */
		private boolean opened;

		/**
		 * When the recipient opened the email
		 */
		@Getter
		private long openTime;

		/**
		 * Did the recipient mark this email as deleted?
		 */
		@Getter
		@Setter
		private boolean markedDeleted;

		/**
		 * Return true if the recipient has opened this mail
		 *
		 * @return
		 */
		public boolean hasOpened() {
			return this.opened;
		}

		/**
		 * Marks the recipient as having read this email
		 */
		public void markOpened() {
			this.opened = true;
			this.openTime = System.currentTimeMillis();
		}

		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			return obj instanceof Recipient && ((Recipient) obj).getUniqueId().equals(this.uniqueId);
		}

		/**
		 * @see org.mineacademy.fo.model.ConfigSerializable#serialize()
		 */
		@Override
		public SerializedMap serialize() {
			final SerializedMap map = new SerializedMap();

			map.put("UUID", this.uniqueId);
			map.putIf("Opened", this.opened);
			map.put("Open_Time", this.openTime);
			map.putIf("Deleted", this.markedDeleted);

			return map;
		}

		/**
		 * Turn a config section into a recipient
		 *
		 * @param map
		 * @return
		 */
		public static Recipient deserialize(SerializedMap map) {
			final UUID uniqueId = map.get("UUID", UUID.class);
			final boolean opened = map.getBoolean("Opened", false);
			final long openTime = map.getLong("Open_Time");
			final boolean deleted = map.getBoolean("Deleted", false);

			return new Recipient(uniqueId, opened, openTime, deleted);
		}

		/*
		 * Create a new recipient that has not yet opened the mail
		 */
		private static Recipient create(UUID uniqueId) {
			return new Recipient(uniqueId, false, -1, false);
		}
	}
}
