package org.mineacademy.chatcontrol.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Log;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Mute;
import org.mineacademy.chatcontrol.model.Spy;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.Type;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Messenger;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.event.SimpleListener;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompMetadata;

import lombok.Getter;

/**
 * Represents a simple listener for editing books
 */
public final class BookListener extends SimpleListener<PlayerEditBookEvent> {

	/**
	 * The instance of this class
	 */
	@Getter
	private static final BookListener instance = new BookListener();

	/*
	 * Create a new listener
	 */
	private BookListener() {
		super(PlayerEditBookEvent.class, EventPriority.HIGHEST, true);
	}

	/**
	 * @see org.mineacademy.fo.event.SimpleListener#execute(org.bukkit.event.Event)
	 */
	@Override
	protected void execute(PlayerEditBookEvent event) {
		final Player player = event.getPlayer();
		final ItemStack hand = player.getItemInHand().clone();
		final SenderCache senderCache = SenderCache.from(player);
		final BookMeta bookMeta = event.getNewBookMeta();

		// Check chat mute
		if (Mute.isPartMuted(Settings.Mute.PREVENT_BOOKS, player)) {
			Common.tell(player, Lang.of("Commands.Mute.Cannot_Create_Books"));

			event.setCancelled(true);
			return;
		}

		// Check title
		if (bookMeta.hasTitle()) {
			final String title = bookMeta.getTitle();
			final String newTitle = Rule.filter(Type.BOOK, player, title, null).getMessage();

			// If new title was completely removed, cancel
			if (newTitle.isEmpty())
				cancel();

			if (!title.equals(newTitle))
				bookMeta.setTitle(newTitle);
		}

		// Check pages
		if (bookMeta.hasPages()) {
			final List<String> pages = new ArrayList<>(bookMeta.getPages());
			final List<String> newPages = new ArrayList<>();

			for (final String page : pages)
				newPages.add(Rule.filter(Type.BOOK, player, page, null).getMessage());

			if (!Valid.listEquals(pages, newPages))
				bookMeta.setPages(newPages);
		}

		// Modify and inject our mail title incase we are replying to someone
		if (senderCache.getPendingMailReply() != null) {
			final String mailTitle = senderCache.getPendingMailReply().getTitle();

			bookMeta.setTitle((mailTitle.startsWith("Re: ") ? "" : "Re: ") + mailTitle);
		}

		// Update the book
		event.setNewBookMeta(bookMeta);

		// Register in cache
		final UUID uniqueId = UUID.randomUUID();
		final Book book = Book.fromEvent(event);

		// Handle books
		if (CompMetadata.hasMetadata(hand, Book.TAG)) {

			// Save it
			senderCache.setPendingMail(book);

			if (senderCache.getPendingMailReply() != null) {
				final Mail replyMail = senderCache.getPendingMailReply();

				book.setAuthor(player.getName());
				book.setSigned(true); // Ignore signing since we reuse old title

				player.chat("/" + Settings.Mail.COMMAND_ALIASES.get(0) + " send " + replyMail.getSenderName());

				// Disable the reply
				senderCache.setPendingMailReply(null);

			} else
				Messenger.info(player, Lang.of(event.isSigning() ? "Commands.Mail.Ready" : "Commands.Mail.Draft_Saved"));

			// Remove hand item
			Common.runLater(() -> {
				player.setItemInHand(new ItemStack(CompMaterial.AIR.getMaterial()));
				player.updateInventory();
			});

			return;
		}

		senderCache.getBooks().put(uniqueId, book);

		// Broadcast to spying players
		Spy.broadcastBook(player, book, uniqueId);

		// Log
		Log.logBook(player, book);
	}
}
