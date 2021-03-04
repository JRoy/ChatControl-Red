package org.mineacademy.chatcontrol.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.PlayerCache;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.operator.Tag;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MathUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.database.SimpleDatabase;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.model.Tuple;
import org.mineacademy.fo.remain.Remain;

import lombok.Getter;

/**
 * The centralized database to download/upload entries
 */
public final class Database extends SimpleDatabase {

	/**
	 * The database instance
	 */
	@Getter
	private static final Database instance = new Database();

	/**
	 * How long did it take to update the database initially?
	 *
	 * Used to prevent issue when player switches servers faster
	 * than we can save/load his data.
	 */
	@Getter
	private int pingTicks = 0;

	/*
	 * Create a new database
	 */
	private Database() {
		addVariable("table", "ChatControl");
		addVariable("table_log", "ChatControl_Log");
		addVariable("table_mail", "ChatControl_Mail");
	}

	/**
	 * Create tables on init
	 */
	@Override
	protected void onConnected() {
		final long now = System.currentTimeMillis();

		try {

			update("CREATE TABLE IF NOT EXISTS `{table}` (" +
					" `UUID` varchar(255) NOT NULL," +
					" `Name` text," +
					" `Nick` text," +
					" `Data` longtext," +
					" `LastModified` bigint(20) DEFAULT NULL," +
					" PRIMARY KEY (`UUID`)" +
					") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;");

			update("CREATE TABLE IF NOT EXISTS `{table_log}` (" +
					" `Server` text," +
					" `Date` datetime DEFAULT NULL," +
					" `Type` text," +
					" `Sender` text," +
					" `Receiver` text," +
					" `Content` longtext," +
					" `ChannelName` text," +
					" `RuleName` text," +
					" `RuleGroupName` text" +
					") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;");

			update("CREATE TABLE IF NOT EXISTS `{table_mail}` (" +
					" `UUID` varchar(255) NOT NULL," +
					" `Data` longtext," +
					" PRIMARY KEY (`UUID`)" +
					") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;");

		} catch (final Throwable t) {
			if (t.toString().contains("Unknown collation")) {
				Common.log("You need to update your MySQL provider driver. We switched to support unicode using 4 bits length because the previous system only supported 3 bits.");
				Common.log("Some characters such as smiley or Chinese are stored in 4 bits so they would crash the 3-bit database leading to more problems. Most hosting providers have now widely adopted the utf8mb4_unicode_520_ci encoding you seem lacking. Disable MySQL connection or update your driver to fix this.");
			}

			else
				throw t;
		}

		this.updatePing(now);
	}

	/*
	 * Calculate how long it took to connect to the database, in ticks, adding 30% up as safety margin
	 */
	private void updatePing(long oldTime) {
		this.pingTicks = (int) MathUtil.ceiling((((System.currentTimeMillis() - oldTime) / 1000D) * 50D) * 1.3D);

		if (this.pingTicks > 200)
			Common.log("Warning: Database connection is slow (" + MathUtil.formatTwoDigits(this.pingTicks / 20D) + " seconds), game will not lag, but data will be delayed.");
	}

	/* ------------------------------------------------------------------------------- */
	/* Log */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Inserts the given SQL formatted values into the table_log
	 *
	 * @param values
	 */
	public void insertLogValues(String values) {
		if (isLoaded())
			update("INSERT INTO {table_log} VALUES(" + values + ")");
	}

	/**
	 * Remove old log entries as per settings
	 */
	public void purgeLogEntries() {
		if (isLoaded()) {
			final Timestamp timestamp = new Timestamp(System.currentTimeMillis() - (Settings.Log.CLEAN_AFTER.getTimeSeconds() * 1000));
			Debugger.debug("mysql", "Cleaning ChatControl logs before: " + timestamp);

			this.update(replaceVariables("DELETE FROM {table_log} WHERE Date < '" + timestamp.toString() + "'"));
		}
	}

	/**
	 * Remove old log entries as per settings
	 */
	public List<Log> getLogEntries() {
		Valid.checkAsync("Reading db logs must be done async!");

		final List<Log> entries = new ArrayList<>();

		if (!isLoaded())
			return entries;

		final ResultSet resultSet = query("SELECT * FROM {table_log}");

		try {
			while (resultSet.next()) {
				try {
					final long date = resultSet.getTimestamp("Date").getTime();
					final Log.Type type = Log.Type.fromKey(resultSet.getString("Type"));
					final String sender = resultSet.getString("Sender");

					final String receiversRaw = resultSet.getString("Receiver");

					final List<String> receivers = Common.getOrDefault(receiversRaw != null ? Remain.fromJsonList(receiversRaw.replace("\\\"", "\"")) : null, new ArrayList<>());
					final String content = resultSet.getString("Content").replace(",/ ", ", ").replace("\\\"", "\"");
					final String channelName = resultSet.getString("ChannelName");
					final String ruleName = resultSet.getString("RuleName");
					final String ruleGroupName = resultSet.getString("RuleGroupName");

					entries.add(new Log(date, type, sender, content, receivers, channelName, ruleName, ruleGroupName));

				} catch (final Throwable t) {
					Common.log("Error processing a row to clean up, aborting...");

					t.printStackTrace();
					break;
				}
			}

		} catch (final Throwable t) {
			Common.error(t, "Error getting log entries...");
		}

		return entries;
	}

	/* ------------------------------------------------------------------------------- */
	/* Mail */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Register mail to MySQl
	 *
	 * @param mail
	 */
	public void addMail(Mail mail) {
		Valid.checkAsync("Loading cache must be called async");

		final SerializedMap map = mail.serialize();
		final UUID uniqueId = (UUID) map.remove("UUID");

		this.insert("{table_mail}", SerializedMap.ofArray("UUID", uniqueId, "Data", map.toJson()));
	}

	/**
	 * Loads all mails, also cleaning those that can be removed
	 *
	 * @param syncCallback
	 */
	public void loadMails(Consumer<Set<Mail>> syncCallback) {
		Valid.checkSync("Loading mails must be called sync");

		if (!isConnected())
			return;

		Common.runAsync(() -> {
			try {
				final List<String> mailsToRemove = new ArrayList<>();

				final Set<Mail> loaded = new HashSet<>();
				final ResultSet resultSet = query("SELECT * FROM {table_mail}");

				while (resultSet.next()) {
					final UUID uuid = UUID.fromString(resultSet.getString("UUID"));
					final SerializedMap map = SerializedMap.fromJson(resultSet.getString("Data"));
					map.put("UUID", uuid);

					final Mail mail = Mail.deserialize(map);

					if (mail.canDelete())
						mailsToRemove.add(replaceVariables("DELETE FROM {table_mail} WHERE UUID='" + uuid + "'"));
					else
						loaded.add(mail);
				}

				this.batchUpdate(mailsToRemove);

				Common.runLater(() -> syncCallback.accept(loaded));

			} catch (final Throwable t) {
				Common.error(t, "Error loading mails...");
			}
		});
	}

	/**
	 * Save the given amount of mails
	 *
	 * @param mails
	 */
	public void saveMails(Set<Mail> mails) {
		Valid.checkAsync("Loading mails must be called sync");

		if (!isConnected())
			return;

		final List<SerializedMap> sqls = new ArrayList<>();

		for (final Mail mail : mails) {
			final SerializedMap serialized = mail.serialize();
			final UUID uniqueId = (UUID) serialized.remove("UUID");

			sqls.add(SerializedMap.ofArray("UUID", uniqueId, "Data", serialized.toJson()));
		}

		this.insertBatch("{table_mail}", sqls);
	}

	/* ------------------------------------------------------------------------------- */
	/* Loading and saving */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Loads the given player cache with data from MySQL
	 *
	 * @param cache
	 * @param syncCallback
	 */
	public void loadCache(Player player, Consumer<PlayerCache> syncCallback) {
		Valid.checkSync("Loading cache must be called sync");

		final SenderCache senderCache = SenderCache.from(player);

		senderCache.setLoadingMySQL(true);

		if (!isLoaded()) {
			try {
				syncCallback.accept(PlayerCache.from(player));

			} finally {
				senderCache.setLoadingMySQL(false);
			}

			return;
		}

		Common.runLaterAsync(this.pingTicks, () -> {
			try {
				final long now = System.currentTimeMillis();
				final ResultSet resultSet = query("SELECT * FROM {table} WHERE UUID='" + player.getUniqueId() + "'");

				// Always update ping according to last delay
				this.updatePing(now);

				final String playerName = player.getName();
				final UUID playerUUID = player.getUniqueId();

				final String jsonData = resultSet.next() ? resultSet.getString("Data") : null;
				final String nick = jsonData == null ? null : resultSet.getString("Nick");
				final String remotePlayerName = jsonData == null ? "" : resultSet.getString("Name");
				final long lastModified = jsonData == null ? 0 : resultSet.getLong("LastModified");
				final SerializedMap map = jsonData == null ? new SerializedMap() : SerializedMap.fromJson(jsonData);

				if (lastModified > 0)
					map.override("Last_Active", lastModified);

				Common.runLater(() -> {
					try {
						if (player.isOnline()) {

							if (!remotePlayerName.isEmpty() && !remotePlayerName.equals(playerName))
								Common.log("Detected name change from " + remotePlayerName + " to " + playerName + ". This is in beta. We recommend you check in with the player if his data were migrated!");

							final PlayerCache cache = PlayerCache.loadOrUpdateCache(playerName, playerUUID, map);

							// Only cache locally, do not update MySQL here
							UserMap.getInstance().cacheLocally(new UserMap.Record(playerName, playerUUID, nick));

							// Do the data loading
							syncCallback.accept(cache);

							// At the end, update database with the latest data
							Common.runAsync(() -> this.addUserToMap(playerName /* latest name - supports name change */, playerUUID, nick));
						}

					} finally {
						senderCache.setLoadingMySQL(false);
					}
				});

			} catch (final Throwable t) {
				Common.log("Unable to load MySQL data for " + player.getName() + ", using his data.db info (if any)");

				senderCache.setLoadingMySQL(false);
				t.printStackTrace();
			}
		});
	}

	/**
	 * Save the player cache data to database
	 *
	 * @param cache
	 */
	public void saveCache(PlayerCache cache) {
		Valid.checkAsync("Saving db player data must be done async!");

		if (isLoaded())
			try {
				final SerializedMap map = this.serializeUserData(cache);

				map.put("LastModified", System.currentTimeMillis());

				this.insert(map);

			} catch (final Throwable t) {
				Common.log("Unable to save MySQL data for " + cache.getPlayerName());

				t.printStackTrace();
			}
	}

	/* ------------------------------------------------------------------------------- */
	/* Data manipulating */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return data for the given name
	 *
	 * @param nameOrNick
	 * @return
	 */
	public Tuple<UserMap.Record, SerializedMap> getData(String nameOrNick) {
		Valid.checkAsync("Getting record must be called async");

		if (!isLoaded())
			return null;

		try {
			final ResultSet resultSet = query("SELECT * FROM {table}");

			while (resultSet.next()) {
				final Tuple<UserMap.Record, SerializedMap> tuple = parseDataRow(resultSet);
				final String name = tuple.getKey().getName();
				final String nick = tuple.getKey().getNick();

				if (nameOrNick.equalsIgnoreCase(name) || (nick != null && Valid.colorlessEquals(nameOrNick, nick)))
					return tuple;
			}

		} catch (final Throwable t) {
			Common.error(t, "Error getting user record from MySQL. Returning incomplete data...");
		}

		return null;
	}

	/**
	 * Return the entire Player Name:Player UUID map from MySQL
	 *
	 * @return
	 */
	public Map<UserMap.Record, SerializedMap> getDatas() {
		final Map<UserMap.Record, SerializedMap> allData = new HashMap<>();

		if (isLoaded()) {
			final ResultSet resultSet = query("SELECT * FROM {table}");

			try {
				while (resultSet.next()) {
					final Tuple<UserMap.Record, SerializedMap> tuple = parseDataRow(resultSet);

					allData.put(tuple.getKey(), tuple.getValue());
				}

			} catch (final Throwable t) {
				Common.error(t, "Error getting user map from MySQL. Returning incomplete data...");
			}
		}

		return allData;
	}

	/*
	 * Turn a row into user data
	 */
	private Tuple<UserMap.Record, SerializedMap> parseDataRow(ResultSet resultSet) throws SQLException {
		final String name = resultSet.getString("Name");
		final UUID uniqueId = UUID.fromString(resultSet.getString("UUID"));
		final String nick = resultSet.getString("Nick");
		final String data = resultSet.getString("Data");
		final long lastModified = resultSet.getLong("LastModified");

		final SerializedMap map = data == null ? new SerializedMap() : SerializedMap.fromJson(data);

		if (lastModified > 0)
			map.override("Last_Active", lastModified);

		return new Tuple<>(new UserMap.Record(name, uniqueId, nick), map);
	}

	/**
	 * Add the given player name and player uuid to map, overriding old value
	 *
	 * @param name
	 * @param uniqueId
	 * @param nick
	 */
	public void addUserToMap(String name, UUID uniqueId, String nick) {
		if (isLoaded())
			this.insert(this.serializeUserData(name, uniqueId, nick));
	}

	/**
	 * Add the given map of users to map
	 *
	 * @param users
	 */
	public void addUsersToMap(Collection<UserMap.Record> datas) {
		if (isLoaded()) {
			final List<SerializedMap> batch = new ArrayList<>();

			for (final UserMap.Record data : datas)
				batch.add(this.serializeUserData(data.getName(), data.getUniqueId(), data.getNick()));

			this.insertBatch(batch);
		}
	}

	/*
	 * Serializes the data from player cache into column:value pairs
	 */
	private SerializedMap serializeUserData(PlayerCache cache) {
		final SerializedMap map = serializeUserData(cache.getPlayerName(), cache.getUniqueId(), cache.getTag(Tag.Type.NICK));
		final SerializedMap userMap = cache.serialize();

		userMap.removeWeak("Last_Active");

		map.put("Data", userMap.toJson().replace("\\", "\\\\"));

		return map;
	}

	/*
	 * Serialize user data to be put in the map
	 */
	private SerializedMap serializeUserData(String name, UUID uniqueId, @Nullable String nick) {
		Valid.checkAsync("Cannot connect to database on the main thread!");

		return SerializedMap.ofArray(
				"UUID", uniqueId.toString(),
				"Name", name,
				"Nick", nick == null ? "NULL" : nick);
	}
}
