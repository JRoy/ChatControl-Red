package org.mineacademy.chatcontrol.api;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.mineacademy.chatcontrol.SyncedCache;
import org.mineacademy.fo.event.SimpleEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * An event that is when a player is mentioned in chat.
 */
@Getter
@Setter
public final class PlayerMentionEvent extends SimpleEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	/**
	 * The player who was mentioned, can be on another server
	 */
	private final SyncedCache cache;

	/**
	 * Players which receives the notification about mention
	 */
	private final Collection<Player> recipients;

	/**
	 * Is the event cancelled?
	 */
	private boolean cancelled;

	public PlayerMentionEvent(SyncedCache cache, Collection<Player> recipients) {
		this.cache = cache;
		this.recipients = recipients;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}