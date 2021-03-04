package org.mineacademy.chatcontrol.model;

import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Toggle {

	/**
	 * Player has disabled receiving all mail.
	 */
	MAIL("mail") {
		/**
		 * @see org.mineacademy.chatcontrol.model.Toggle#getDescription()
		 */
		@Override
		public String getDescription() {
			return Lang.of("Commands.Spy.Type_Mail");
		}
	},

	/**
	 * Player has disabled seeing "/chc announce" announcements.
	 */
	ANNOUNCEMENT("announcement") {
		/**
		 * @see org.mineacademy.chatcontrol.model.Toggle#getDescription()
		 */
		@Override
		public String getDescription() {
			return Lang.of("Commands.Spy.Type_Announcement");
		}
	},

	/**
	 * Player has disabled receiving /me sent by others.
	 */
	ME("me") {
		/**
		 * @see org.mineacademy.chatcontrol.model.Toggle#getDescription()
		 */
		@Override
		public String getDescription() {
			return Lang.of("Commands.Spy.Type_Me");
		}
	},

	/**
	 * Player has disabled receiving private messages from any player.
	 */
	PM("pm") {
		/**
		 * @see org.mineacademy.chatcontrol.model.Toggle#getDescription()
		 */
		@Override
		public String getDescription() {
			return Lang.of("Commands.Spy.Type_Private_Message");
		}
	},

	/**
	 * Player has disabled seeing all chat messages
	 */
	CHAT("chat") {
		/**
		 * @see org.mineacademy.chatcontrol.model.Toggle#getDescription()
		 */
		@Override
		public String getDescription() {
			return Lang.of("Commands.Spy.Type_Chat");
		}
	}

	;

	/**
	 * The localized key
	 */
	@Getter
	private final String key;

	/**
	 * Get the localized description
	 *
	 * @return
	 */
	public abstract String getDescription();

	/**
	 * Return this enum key from the given config key
	 *
	 * @param key
	 * @return
	 */
	public static Toggle fromKey(String key) {
		for (final Toggle party : values())
			if (party.key.equalsIgnoreCase(key))
				return party;

		throw new IllegalArgumentException("No such channel party: " + key + ". Available: " + Common.join(values()));
	}

	/**
	 * Returns {@link #getKey()}
	 *
	 * @return
	 */
	@Override
	public String toString() {
		return this.key;
	}
}