package org.mineacademy.chatcontrol.command;

import java.util.List;

import org.mineacademy.chatcontrol.command.ChatControlCommands.ChatControlSubCommand;
import org.mineacademy.chatcontrol.model.Migrator;
import org.mineacademy.chatcontrol.model.Permissions;
import org.mineacademy.chatcontrol.model.UserMap;
import org.mineacademy.chatcontrol.settings.Lang;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.plugin.SimplePlugin;

public final class ChatControlMigrate extends ChatControlSubCommand {

	/**
	 * A one-way boolean applicable globally for migrating from CHC8 to CHC10
	 */
	private boolean migrationConfirmed = false;

	public ChatControlMigrate() {
		super("migrate");

		setUsage(Lang.of("Commands.Migrate.Usage"));
		setDescription(Lang.of("Commands.Migrate.Description"));
		setMinArguments(1);
		setPermission(Permissions.Command.MIGRATE);
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#getMultilineUsageMessage()
	 */
	@Override
	protected String[] getMultilineUsageMessage() {
		return Lang.ofArray("Commands.Migrate.Usages");
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#onCommand()
	 */
	@Override
	protected void execute() {
		checkUsage(args.length == 1);

		final String param = args[0];

		if ("chc8".equals(param)) {
			checkBoolean(!isPlayer(), "This command can only be executed from the console.");

			// Print important footnotes before migrating
			if (!this.migrationConfirmed) {
				this.migrationConfirmed = true;

				tellWarn("**WARNING** DO NOT MIGRATE ON A PRODUCTION SERVER");
				tellNoPrefix("How to perform a migration from ChatControl Pro:");
				tellNoPrefix(" ");
				tellNoPrefix("1. Consider doing a clean install. You'll have your space cluttered and we'll migrate ineffective systems from the old version.");
				tellNoPrefix("   Also, you won't be able to see TONS of NEW EXAMPLES in the default files since we change those files and replace comments.");
				tellNoPrefix("   There are 34 pages on https://github.com/kangarko/ChatControl-red/wiki to read and most of the new files (including rules) are");
				tellNoPrefix("   very similar to what they used to be, so you will be able to copy paste some of it from your old settings and save time.");
				tellNoPrefix(" ");
				tellNoPrefix("   Or you could do the migration and then retain migrated messages/ and rules/ folder and remove everything else to let");
				tellNoPrefix("   ChatControl Red make those files anew. That way, you migrate only those files you spent the most time working on...");
				tellNoPrefix(" ");
				tellNoPrefix("2. Copy both ChatControl and ChatControl Red folder to a local test server and install ChatControl Red.");
				tellNoPrefix("3. Type this command again.");
				tellNoPrefix("4. Restart the server and STUDY migration.log to understand what we migrated and what action you need to take.");
				tellNoPrefix("   Successful migration doesn't mean the plugin will behave the same! You need to check and test this.");
				tellNoPrefix(" ");
				tellNoPrefix("   NB: We automatically convert [JSON] in your formats. Converter does not recognize multiple lines");
				tellNoPrefix("   so for such converted formats you'd have to make your Message key in your format/<format>.yml file so:");
				tellNoPrefix("   Message: ");
				tellNoPrefix("   - '<message>'");
				tellNoPrefix("   - ''");

				tellWarn("**WARNING** DO NOT MIGRATE ON A PRODUCTION SERVER");

				return;
			}

			// Perform migration
			Migrator.migrate();

			// Reload to apply comments formatting
			SimplePlugin.getInstance().reload();

			tellSuccess("Migration has been completed but unapplied. Please study your migration.log thoroughly "
					+ " (action may be required) and restart your server to apply changes.");

		} else if ("import".equals(param))
			Common.runAsync(() -> {
				checkBoolean(Settings.MySQL.ENABLED, Lang.of("Commands.No_MySQL"));

				tellInfo(Lang.of("Commands.Migrate.Import_Start"));
				UserMap.getInstance().importFromDb();

				tellSuccess(Lang.of("Commands.Migrate.Import_Finish"));
			});

		else if ("export".equals(param))
			Common.runAsync(() -> {
				checkBoolean(Settings.MySQL.ENABLED, Lang.of("Commands.No_MySQL"));

				tellInfo(Lang.of("Commands.Migrate.Export_Start"));
				final int exportedEntryCount = UserMap.getInstance().exportToDb();

				tellSuccess(Lang.of("Commands.Migrate.Export_Finish", exportedEntryCount));
			});

		else if ("essentials".equals(param)) {
			UserMap.getInstance().importEssentialsAndSave();

			tellSuccess(Lang.of("Commands.Migrate.Essentials"));
		}

		else
			returnInvalidArgs();
	}

	/**
	 * @see org.mineacademy.fo.command.SimpleCommand#tabComplete()
	 */
	@Override
	protected List<String> tabComplete() {

		if (args.length == 1)
			return completeLastWord("chc8", "import", "export", "essentials");

		return NO_COMPLETE;
	}
}
