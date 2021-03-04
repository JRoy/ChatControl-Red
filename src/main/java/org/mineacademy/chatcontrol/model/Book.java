package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.menu.model.ItemCreator;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.ConfigSerializable;
import org.mineacademy.fo.model.SimpleComponent;
import org.mineacademy.fo.model.Variables;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a book handler that can show player books
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Book implements ConfigSerializable {

	/**
	 * The editable book nbt tag
	 */
	public final static String TAG = "FoEditableBook";

	/**
	 * The book title
	 */
	private final String title;

	/**
	 * The book author
	 */
	@Setter
	private String author;

	/**
	 * The book pages
	 */
	private final List<String> pages;

	/**
	 * Is d' book signed?
	 */
	@Setter
	private boolean signed;

	/**
	 * The time d' book d' modified'
	 */
	private final long lastModified;

	/**
	 * The file name d' book or null if 'd Buch not exists
	 */
	private String fileName;

	/**
	 * The identification of the "lastest" book version.. LASTEST...
	 */
	private final UUID uniqueId;

	/**
	 * Opens the book for the player, rendering pages in chat for MC 1.7.10 and older
	 *
	 * @param player
	 */
	public void open(Player player) {

		// Render as text, replacing variables
		if (MinecraftVersion.olderThan(V.v1_8)) {
			final List<SimpleComponent> pages = new ArrayList<>();
			int pageNumber = 1;

			for (final String page : this.pages) {
				pages.add(Lang.ofComponent("Commands.Book.Page", pageNumber++));

				for (final String line : page.split("\n"))
					pages.add(SimpleComponent.of(" &7- &r" + Variables.replace(line, player)));

				pages.add(SimpleComponent.empty());
			}

			new ChatPaginator()
					.setFoundationHeader(Lang.of("Commands.Book.Page_Header",
							Common.getOrDefault(this.title, Lang.of("Commands.Book.Unnamed")),
							Common.getOrDefault(this.author, Lang.of("Commands.Book.Unsigned"))))
					.setPages(pages)
					.send(player);

			return;
		}

		// Open a clone book for player so we can replace variable in it
		final ItemStack clone = new ItemStack(CompMaterial.WRITTEN_BOOK.getMaterial());
		final BookMeta bookMeta = (BookMeta) clone.getItemMeta();

		// Replace our variables
		final List<String> pagesClone = new ArrayList<>();

		for (final String page : this.pages)
			pagesClone.add(Variables.replace(page, player));

		bookMeta.setPages(pagesClone);
		bookMeta.setTitle(this.title == null ? "Blank" : this.title);
		bookMeta.setAuthor(this.author == null ? "Blank" : this.author);

		clone.setItemMeta(bookMeta);

		// calls player.openBook(book); on MC 1.16
		Remain.openBook(player, clone);
	}

	/**
	 * Save the book to file, return true if the old file was overriden
	 *
	 * @param fileName
	 * @return
	 */
	public boolean save(String fileName) throws IOException {
		final File target = FileUtil.getFile("books/" + fileName + ".yml");
		final boolean exists = target.exists();

		if (!exists)
			FileUtil.getOrMakeFile("books/" + fileName + ".yml");

		final FileConfiguration config = FileUtil.loadConfigurationStrict(target);

		// Update file name
		this.fileName = fileName;

		config.set("Data", SerializeUtil.serialize(this.serialize()));
		config.save(target);

		// If it exists, we return true since we had to override it
		return exists;
	}

	/**
	 * Return this as editable book
	 *
	 * @param title
	 * @return
	 */
	public ItemStack toEditableBook(String title, String... lore) {
		return ItemCreator
				.of(this.toBook(CompMaterial.WRITABLE_BOOK))
				.name(title)
				.tag(TAG, "true")
				.lores(Arrays.asList(lore))
				.hideTags(true)
				.build()
				.make();
	}

	/**
	 * Return this as written finished book
	 *
	 * @return
	 */
	public ItemStack toWrittenBook() {
		return this.toBook(CompMaterial.WRITTEN_BOOK);
	}

	/*
	 * Converts this class to an {@link ItemStack} with the given material
	 */
	private ItemStack toBook(CompMaterial material) {
		final ItemStack item = new ItemStack(material.getMaterial());
		final BookMeta meta = (BookMeta) Bukkit.getItemFactory().getItemMeta(material.getMaterial());

		meta.setTitle(this.title);
		meta.setAuthor(this.author);
		meta.setPages(this.pages);

		//Remain.setPages(meta, Common.convert(this.pages, component -> new BaseComponent[] { component.build(null) }));

		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Return true if das Buch is on disk
	 *
	 * @return
	 */
	public boolean isSaved() {
		return this.fileName != null;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Book " + this.serialize().toStringFormatted();
	}

	/* ------------------------------------------------------------------------------- */
	/* Serializing */
	/* ------------------------------------------------------------------------------- */

	/** 
	 * Converts the config map to a book.
	 *
	 * @param map
	 * @return
	 */
	public static Book deserialize(SerializedMap map) {
		Valid.checkBoolean(!map.isEmpty(), "Cannot deserialize empty map to book!");

		final String title = map.getString("Title");
		final String author = map.getString("Author");
		final List<String> pages = map.getStringList("Pages");
		final boolean signed = map.getBoolean("Signed", false);
		final long lastModified = map.getLong("Last_Modified", 0L);
		final UUID uniqueId = map.get("Unique_Id", UUID.class);

		return new Book(title, author, pages, signed, lastModified, null, uniqueId);
	}

	/**
	 * @see org.mineacademy.fo.model.ConfigSerializable#serialize()
	 */
	@Override
	public SerializedMap serialize() {
		final SerializedMap map = new SerializedMap();

		map.putIf("Title", this.title);
		map.putIf("Author", this.author);
		map.put("Pages", this.pages != null && this.pages.size() > 60 ? this.pages.subList(0, 60) : this.pages); // Trim to avoid packet overflow
		map.put("Signed", this.signed);
		map.put("Last_Modified", this.lastModified);
		map.put("Unique_Id", this.uniqueId);

		return map;
	}

	/* ------------------------------------------------------------------------------- */
	/* Static */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Create a new empty book
	 *
	 * @return
	 */
	public static Book newEmptyBook() {
		return new Book(null, null, Common.toList(""), false, System.currentTimeMillis(), null, UUID.randomUUID());
	}

	/**
	 * Return a clone of the given book with author changed
	 *
	 * @param book
	 * @param newAuthor
	 * @return
	 */
	public static Book clone(Book book, String newAuthor) {
		return new Book(book.getTitle(), newAuthor, book.getPages(), book.isSigned(), book.getLastModified(), book.getFileName(), book.getUniqueId());
	}

	/**
	 * Make a {@link Book} from the given book edit event
	 *
	 * @param event
	 * @return
	 */
	public static Book fromEvent(PlayerEditBookEvent event) {
		final BookMeta meta = event.getNewBookMeta();

		final String title = meta.getTitle();
		final String author = meta.getAuthor();
		final List<String> pages = meta.getPages();
		final boolean signed = event.isSigning();
		final long lastModified = System.currentTimeMillis();

		return new Book(title, author, pages, signed, lastModified, null, UUID.randomUUID());
	}

	/**
	 * Return a book from the book name given it is in books/ folder.
	 *
	 * @param fileName
	 * @return
	 */
	public static Book fromFile(String fileName) {
		final File file = FileUtil.getFile("books/" + fileName + (fileName.endsWith(".yml") ? "" : ".yml"));

		if (!file.exists())
			throw new IllegalArgumentException("No such book: '" + fileName + "'. Available: " + Common.join(Book.getBookNames()));

		final FileConfiguration config = FileUtil.loadConfigurationStrict(file);

		if (!config.isSet("Data"))
			throw new IllegalArgumentException("Book '" + fileName + "' has corrupted data.");

		final Book book = deserialize(SerializedMap.of(config.get("Data")));

		book.fileName = fileName;

		return book;
	}

	/**
	 * Copies the default books/ folder if it does not exist already
	 */
	public static void copyDefaults() {
		final File booksFolder = FileUtil.getFile("books");

		if (!booksFolder.exists())
			FileUtil.extractFolderFromJar("books/", "books");
	}

	/**
	 * Return all book names in books/ folder
	 *
	 * @return
	 */
	public static List<String> getBookNames() {
		return Common.convert(FileUtil.getFiles("books", ".yml"), FileUtil::getFileName);
	}

	/**
	 * Return all books in books/ folder
	 *
	 * @return
	 */
	public static List<Book> getBooks() {
		return Common.convert(FileUtil.getFiles("books", ".yml"), file -> Book.fromFile(file.getName()));
	}
}
