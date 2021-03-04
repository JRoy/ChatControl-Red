package org.mineacademy.chatcontrol.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Book;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.model.ChatPaginator;
import org.mineacademy.fo.model.SimpleComponent;

public final class ChatControlBook extends ChatControlSubCommand {

	public ChatControlBook() {
		super("book");

		setUsage(Lang.of("Commands.Book.Usage"));
		setDescription(Lang.of("Commands.Book.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.BOOK);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Book.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {

		final String param = args[0];
		final SenderCache senderCache = SenderCache.from(sender);

		if ("new".equals(param)) {
			checkConsole();
			checkBoolean(Settings.Mail.ENABLED, Lang.of("Commands.Book.Mail_Disabled"));

			Bukkit.dispatchCommand(sender, Settings.Mail.COMMAND_ALIASES.get(0) + " new");
			tellSuccess(Lang.of("Commands.Book.New"));
		}

		else if ("save".equals(param)) {
			final Book pendingBook = senderCache.getPendingMail();

			checkNotNull(pendingBook, Lang.of("Commands.Book.Save_No_Book"));
			checkBoolean(pendingBook.isSigned(), Lang.of("Commands.Book.Save_Not_Signed"));
			checkArgs(2, Lang.of("Commands.Book.Save_No_Name"));
			checkUsage(args.length == 2);

			try {
				final boolean override = pendingBook.save(args[1]);
				senderCache.setPendingMail(null);

				tellSuccess(Lang.ofScript("Commands.Book.Save", SerializedMap.of("override", override), args[1]));

			} catch (final IOException ex) {
				ex.printStackTrace();

				tellError(Lang.of("Commands.Book.Save_Error", args[1], ex.toString()));
			}

			return;
		}

		else if ("delete".equals(param)) {
			checkArgs(2, Lang.of("Commands.Book.No_Book", Common.join(Book.getBookNames())));

			final String bookName = args[1];
			final File bookFile = FileUtil.getFile("books/" + bookName + (bookName.endsWith(".yml") ? "" : ".yml"));
			checkBoolean(bookFile.exists(), Lang.of("Commands.Book.Invalid", bookName, Common.join(Book.getBookNames()) + "."));

			final boolean success = bookFile.delete();

			if (success)
				tellSuccess(Lang.of("Commands.Book.Delete", bookName));
			else
				tellError(Lang.of("Commands.Book.Delete_Fail", bookName));
		}

		else if ("open".equals(param)) {
			checkArgs(2, Lang.of("Commands.Book.No_Book", Common.join(Book.getBookNames())));

			final Book book;

			try {
				book = Book.fromFile(args[1]);

			} catch (final IllegalArgumentException ex) {
				returnTell(ex.getMessage());

				return;
			}

			final Player player = findPlayerOrSelf(args.length == 3 ? args[2] : null);
			book.open(player);

			if (!player.getName().equals(this.sender.getName()))
				Common.tell(sender, Lang.of("Commands.Book.Open", player.getName()));
		}

		else if ("list".equals(param)) {
			final List<SimpleComponent> pages = new ArrayList<>();

			for (final Book book : Book.getBooks())
				pages.add(SimpleComponent.empty()

						.append(Lang.of("Commands.Book.List_Open"))
						.onHover(Lang.of("Commands.Book.List_Open_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " open " + book.getFileName() + " " + sender.getName())

						.append(" ")

						.append(Lang.of("Commands.Book.List_Delete"))
						.onHover(Lang.of("Commands.Book.List_Delete_Tooltip"))
						.onClickRunCmd("/" + getLabel() + " " + getSublabel() + " delete " + book.getFileName())

						.append(Lang.of("Commands.Book.List")
								.replace("{title}", book.getTitle())
								.replace("{author}", book.getAuthor())
								.replace("{date}", TimeUtil.getFormattedDateMonth(book.getLastModified())))
						.onHover(Lang.of("Commands.Book.List_Tooltip", book.getFileName()))

				);

			checkBoolean(!pages.isEmpty(), Lang.of("Commands.Book.List_None"));

			new ChatPaginator()
					.setFoundationHeader(Lang.of("Commands.Book.List_Header"))
					.setPages(pages)
					.send(sender);

		} else
			returnInvalidArgs();
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("new", "save", "delete", "open", "list");

		if (args.length >= 2 && ("save".equals(args[0]) || "delete".equals(args[0]) || "open".equals(args[0])))
			if (args.length == 2)
				return completeLastWord(Book.getBookNames());

			else if (args.length == 3 && "open".equals(args[0]))
				return completeLastWordPlayerNames();

		return NO_COMPLETE;
	}
}
