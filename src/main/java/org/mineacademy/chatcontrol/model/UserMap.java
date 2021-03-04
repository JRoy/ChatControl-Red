package org.mineacademy.chatcontrol.model;

import java.io.File;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.ServerCache;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.plugin.SimplePlugin;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An alternative to Essentials' usermap.csv file
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserMap {

	/**
	 * Represents the singleton of this class
	 */
	@Getter
	private static volatile UserMap instance = new UserMap();

	/**
	 * The player name to UUID/nick map
	 */
	private final StrictMap<UUID, Record> userMap = new StrictMap<>();

	/**
	 * The file that holds the map if local file is used
	 */
	private final File file = FileUtil.getFile("usermap.csv");

	/**
	 * Load all usermap data
	 */
	public void load() {
		this.userMap.clear();

		// Import Essentials file
		final ServerCache serverCache = ServerCache.getInstance();

		if (!serverCache.isEssentialsUserMapImported()) {
			this.importEssentialsFile();

			serverCache.setEssentialsUserMapImported();
		}

		// Now import our own values, overriding those from Essentials
		if (Settings.MySQL.ENABLED)
			for (final Record data : Database.getInstance().getDatas().keySet())
				this.cacheLocally(data);

		else
			this.loadLines(this.file);

		Common.runAsync(this::save);
	}

	/**
	 * Check and import essentials usermap.csv and save
	 */
	public void importEssentialsAndSave() {
		this.importEssentialsFile();

		Common.runAsync(this::save);
	}

	/*
	 * Check and import essentials usermap.csv
	 */
	private void importEssentialsFile() {
		try {
			final File essentialsFile = new File("plugins/Essentials/usermap.csv");

			this.loadLines(essentialsFile);
		} catch (final Throwable t) {
			// users having mess in their ess folder, ignore that
		}
	}

	/*
	 * Load CSV name,uuid,nick lines from the given file
	 */
	private void loadLines(File file) {
		if (file.exists())
			for (final String line : FileUtil.readLines(file))
				this.cacheLocally(this.parseLine(line));
	}

	/*
	 * Parse a line
	 */
	private Record parseLine(String line) {
		try {
			if (!line.contains(","))
				throw new IllegalArgumentException();

			final String[] split = line.split(",");

			if (split.length < 2)
				throw new IllegalArgumentException();

			final String name = split[0];
			final UUID uuid = UUID.fromString(split[1]);
			final String nick = split.length == 3 ? split[2] : null;

			return new Record(name, uuid, nick);

		} catch (final Exception ex) {
			throw new FoException("Invalid syntax in line! Line must be <name>,<uuid>[,<nick>] but got: " + line);
		}
	}

	/*
	 * Save the user map to the file or database
	 */
	private void save() {

		// If the plugin failed, save user data on the main thread to prevent data loss
		if (SimplePlugin.getInstance().isEnabled() && !SimplePlugin.isReloading())
			Valid.checkAsync("Saving usermap must be done async!");

		synchronized (this.userMap) {
			final Collection<Record> datas = this.userMap.values();

			if (Settings.MySQL.ENABLED)
				Database.getInstance().addUsersToMap(datas);

			else
				saveFile();
		}
	}

	/*
	 * Saves file data
	 */
	private void saveFile() {
		final List<String> lines = new ArrayList<>();

		for (final Record data : this.userMap.values())
			lines.add(data.getName() + "," + data.getUniqueId() + (data.getNick() != null ? "," + data.getNick() : ""));

		Collections.sort(lines);

		FileUtil.write(this.file, lines, StandardOpenOption.TRUNCATE_EXISTING);
	}

	/**
	 * Saves the player cache to db
	 * 
	 * @param cache
	 */
	public void save(PlayerCache cache) {
		synchronized (instance) {

			// Get the old record to support name changes but preserve nick
			final Record oldRecord = getRecord(cache.getUniqueId());

			if (oldRecord != null && !oldRecord.getName().isEmpty() && !oldRecord.getName().equals(cache.getPlayerName()))
				Common.log("Detected name change from " + oldRecord.getName() + " to " + cache.getPlayerName() + ". This is in beta. We recommend you check in with the player if his data were migrated!");

			this.save(cache.getPlayerName(), cache.getUniqueId(), oldRecord == null ? null : oldRecord.getNick());
		}
	}

	/**
	 * Updates the player data with the new nick, can be null
	 * 
	 * @param name, purely for performance reasons
	 * @param uniqueId
	 * @param nick
	 */
	public void save(String name, UUID uniqueId, @Nullable String nick) {
		synchronized (instance) {

			this.cacheLocally(new Record(name, uniqueId, nick));

			Common.runAsync(() -> {
				if (Settings.MySQL.ENABLED)
					Database.getInstance().addUserToMap(name, uniqueId, nick);
				else
					this.save();
			});
		}
	}

	/**
	 * Add or override user in the local map without saving it
	 *
	 * @param data
	 */
	public void cacheLocally(Record data) {
		synchronized (instance) {
			this.userMap.override(data.getUniqueId(), data);
		}
	}

	/**
	 * Import database entries to data.db
	 */
	public void importFromDb() {
		Valid.checkAsync("Importing from db must be called async!");

		synchronized (instance) {
			final Map<Record, SerializedMap> datas = Database.getInstance().getDatas();

			Common.runLater(() -> {
				for (final Map.Entry<Record, SerializedMap> entry : datas.entrySet()) {
					final Record record = entry.getKey();
					final SerializedMap data = entry.getValue();

					this.cacheLocally(record);
					PlayerCache.loadOrUpdateCache(record.getName(), record.getUniqueId(), data);
				}

				this.saveFile();
			});
		}
	}

	/**
	 * Export local data.db entries to database
	 *
	 * @return how many entries were in the usermap.csv that were exported?
	 */
	public int exportToDb() {
		Valid.checkAsync("Exporting to db must be called async!");

		synchronized (instance) {

			// Export user data
			final List<Record> datas = new ArrayList<>();

			for (final String line : FileUtil.readLines(file))
				datas.add(this.parseLine(line));

			Database.getInstance().addUsersToMap(datas);

			// Export mails
			final Set<Mail> mails = ServerCache.getInstance().getDiskMails();
			int count = 1;

			for (final Mail mail : mails) {
				Common.log("Exporting mail " + count++ + "/" + mails.size());

				Database.getInstance().addMail(mail);
			}

			return datas.size();
		}
	}

	/**
	 * Return the name from UUID, or null if not yet set
	 *
	 * @param uuid
	 * @return
	 */
	@Nullable
	public String getName(UUID uuid) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getUniqueId().equals(uuid))
					return data.getName();

			return null;
		}
	}

	/**
	 * Attempts to get the nick from the given name, or returns null if not set or not stored
	 *
	 * @param name
	 * @return
	 */
	@Nullable
	public String getNick(String name) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getName().equalsIgnoreCase(name))
					return data.getNick();

			return null;
		}
	}

	/**
	 * Return the name for a nick or null
	 *
	 * @param nick
	 * @return
	 */
	@Nullable
	public String getName(@NonNull String nick) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getNick() != null && data.getNick().equalsIgnoreCase(nick))
					return data.getName();

			return null;
		}
	}

	/**
	 * Return true if the given player name is stored in user map
	 * case insensitive.
	 *
	 * @param name
	 * @return
	 */
	public boolean isPlayerNameStored(@NonNull String name) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getName().equalsIgnoreCase(name))
					return true;

			return false;
		}
	}

	/**
	 * Return the record from name, or null if not yet set.
	 *
	 * @param nameOrNick
	 * @return
	 */
	@Nullable
	public Record getRecord(String nameOrNick) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getName().equalsIgnoreCase(nameOrNick) || (data.getNick() != null && data.getNick().equalsIgnoreCase(nameOrNick)))
					return data;

			return null;
		}
	}

	/**
	 * Return the record from uuid, or null if not yet set.
	 *
	 * @param uniqueId
	 * @return
	 */
	@Nullable
	public Record getRecord(UUID uniqueId) {
		synchronized (instance) {
			for (final Record data : this.userMap.values())
				if (data.getUniqueId().equals(uniqueId))
					return data;

			return null;
		}
	}

	/**
	 * Return all records we hold in memory
	 *
	 * @return
	 */
	public Collection<Record> getLocalData() {
		return this.userMap.values();
	}

	/* ------------------------------------------------------------------------------- */
	/* Classes */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Represents a single record in the user map
	 */
	@Data
	@RequiredArgsConstructor
	public static final class Record {
		private final String name;
		private final UUID uniqueId;
		private final String nick;
	}
}
