package org.mineacademy.chatcontrol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mineacademy.chatcontrol.model.Database;
import org.mineacademy.chatcontrol.model.Mail;
import org.mineacademy.chatcontrol.model.Mail.Recipient;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.chatcontrol.settings.Settings.MySQL;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.SerializedMap;
import org.mineacademy.fo.constants.FoConstants;
import org.mineacademy.fo.exception.InvalidWorldException;
import org.mineacademy.fo.model.SimpleTime;
import org.mineacademy.fo.settings.YamlConfig;
import org.mineacademy.fo.visual.VisualizedRegion;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents data.db portion used for server-wide information
 */
@Getter
public final class ServerCache extends YamlConfig {

	/**
	 * The singleton of this class
	 */
	@Getter
	private static final ServerCache instance = new ServerCache();

	/**
	 * If the server is muted, this is the unmute time in the future
	 * where it will no longer be muted
	 */
	private Long unmuteTime;

	/**
	 * Did we yet import usermap.csv from Essentials?
	 */
	private boolean essentialsUserMapImported;

	/**
	 * Has at least one administrator completed the plugin's tour?
	 */
	private boolean tourCompleted;

	/**
	 * Stores loaded region from the disk
	 */
	private List<VisualizedRegion> regions = new ArrayList<>();

	/**
	 * Stores all mail communication
	 */
	private Set<Mail> mails = new HashSet<>();

	/*
	 * Do not load data here because all classes are scanned in SimplePlugin
	 * and then data would be erased
	 */
	private ServerCache() {
	}

	/* ------------------------------------------------------------------------------- */
	/* Loading */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Manually load server data
	 */
	public void load() {
		loadConfiguration(NO_DEFAULT, FoConstants.File.DATA);

		this.unmuteTime = getLong("Unmute_Time");
		this.essentialsUserMapImported = getBoolean("Essentials_User_Map_Imported", false);
		this.tourCompleted = getBoolean("Tour_Completed", false);
		this.regions = this.loadRegions();

		cleanPlayers();

		if (MySQL.ENABLED)
			Database.getInstance().loadMails(loaded -> this.mails = loaded);

		else {
			this.mails = getSet("Mails", Mail.class);

			cleanMails();
		}
	}

	/*
	 * Load regions manually and turn into visualizable able able able ones (very able)
	 */
	private List<VisualizedRegion> loadRegions() {
		final List<VisualizedRegion> regions = new ArrayList<>();

		for (final Object map : getList("Regions")) {
			VisualizedRegion region;

			try {
				region = SerializeUtil.deserialize(VisualizedRegion.class, map);

			} catch (final Throwable ex) {
				Throwable t = ex;

				while (t.getCause() != null)
					t = t.getCause();

				// Get to the root cause and then ignore if the world has been unloaded since.
				if (t instanceof InvalidWorldException)
					continue;

				throw ex;
			}

			regions.add(region);
		}

		return regions;
	}

	/*
	 * Player player data over limit
	 */
	private void cleanPlayers() {
		final boolean cleanInactive = !Settings.CLEAR_DATA_IF_INACTIVE.getRaw().equals("0");
		final long threshold = System.currentTimeMillis() - (Settings.CLEAR_DATA_IF_INACTIVE.getTimeSeconds() * 1000);
		int cleanedCount = 0;

		for (final String rawUUID : getMap("Players").keySet()) {

			// Verify UUID validity
			UUID.fromString(rawUUID);

			// Empty / no data
			if (getMap("Players." + rawUUID).isEmpty()) {
				getConfig().set("Players." + rawUUID, null);

				cleanedCount++;
				continue;
			}

			// Inactive
			final long lastActive = getLong("Players." + rawUUID + ".Last_Active", -1L);

			if (cleanInactive && (lastActive == -1 || lastActive < threshold)) {
				getConfig().set("Players." + rawUUID, null);

				cleanedCount++;
			}
		}

		if (cleanedCount > 0) {
			Common.log("Cleaned data of " + Common.plural(cleanedCount, "inactive player") + ".");

			save();
		}
	}

	private void cleanMails() {
		final int originalEmailSize = this.mails.size();

		for (final Iterator<Mail> it = this.mails.iterator(); it.hasNext();) {
			final Mail mail = it.next();

			if (mail.canDelete())
				it.remove();
		}

		if (originalEmailSize != this.mails.size())
			save();
	}

	/**
	 * Return disk mails without loading/saving/altering
	 * s
	 * @return
	 */
	public Set<Mail> getDiskMails() {
		return getSet("Mails", Mail.class);
	}

	@Override
	public void save() {
		final SerializedMap map = new SerializedMap();

		map.putIf("Unmute_Time", this.unmuteTime);
		map.putIf("Essentials_User_Map_Imported", this.essentialsUserMapImported);
		map.putIf("Regions", this.regions);
		map.putIf("Tour_Completed", this.tourCompleted);

		if (MySQL.ENABLED)
			Common.runAsync(() -> Database.getInstance().saveMails(this.mails));
		else
			map.putIf("Mails", this.mails);

		for (final Map.Entry<String, Object> entry : map.entrySet())
			setNoSave(entry.getKey(), entry.getValue());

		super.save();
	}

	/* ------------------------------------------------------------------------------- */
	/* Getters and setters */
	/* ------------------------------------------------------------------------------- */

	/**
	 * Return true if the player is muted
	 *
	 * @return
	 */
	public boolean isMuted() {
		return this.unmuteTime != null && this.unmuteTime > System.currentTimeMillis();
	}

	/**
	 * Set the mute for this player
	 *
	 * @param duration how long, null to unmute
	 */
	public void setMuted(@Nullable SimpleTime duration) {
		this.unmuteTime = duration == null ? null : System.currentTimeMillis() + (duration.getTimeSeconds() * 1000);

		save();
	}

	/**
	 * Flag Essentials user map import as done (does not import anything, just marks the flag as true)
	 * or throw error if already flagged
	 */
	public void setEssentialsUserMapImported() {
		Valid.checkBoolean(!this.essentialsUserMapImported, "Essentials usermap.csv already imported!");
		this.essentialsUserMapImported = true;

		save();
	}

	/**
	 * Set that the tour has been completed at least once.
	 *
	 * @param tourCompleted the tourCompleted to set
	 */
	public void setTourCompleted(boolean tourCompleted) {
		this.tourCompleted = tourCompleted;

		save();
	}

	/**
	 * Return true if the given region is loaded
	 *
	 * @param name
	 * @return
	 */
	public boolean isRegionLoaded(String name) {
		return findRegion(name) != null;
	}

	/**
	 * Get region by name
	 *
	 * @param name
	 * @return
	 */
	public VisualizedRegion findRegion(@NonNull String name) {
		for (final VisualizedRegion region : this.regions)
			if (region.getName() != null && region.getName().equalsIgnoreCase(name))
				return region;

		return null;
	}

	/**
	 * Get list of regions that are in the given location
	 *
	 * @param location
	 * @return
	 */
	public List<VisualizedRegion> findRegions(@NonNull Location location) {
		final List<VisualizedRegion> regionsAtLocation = new ArrayList<>();

		for (final VisualizedRegion region : this.regions)
			if (region.isWithin(location))
				regionsAtLocation.add(region);

		return regionsAtLocation;
	}

	/**
	 * Add a new region
	 *
	 * @param region
	 */
	public void addRegion(@NonNull VisualizedRegion region) {
		Valid.checkBoolean(findRegion(region.getName()) == null, "Region " + region.getName() + " already exists!");

		this.regions.add(region);
		save();
	}

	/**
	 * Remove region by name
	 *
	 * @param name
	 */
	public void removeRegion(String name) {
		final VisualizedRegion region = findRegion(name);
		Valid.checkNotNull(region, "Region " + name + " does not exist!");

		this.regions.remove(region);
		save();
	}

	/**
	 * Add a new email to our database
	 *
	 * @param mail
	 */
	public void addMail(Mail mail) {
		this.mails.add(mail);

		if (MySQL.ENABLED)
			Common.runAsync(() -> Database.getInstance().addMail(mail));

		save();
	}

	/**
	 * Return all mails that the recipient got
	 *
	 * @param recipient
	 * @return
	 */
	public List<Mail> findMailsTo(UUID recipient) {
		final List<Mail> filtered = new ArrayList<>();

		for (final Mail mail : this.mails)
			if (mail.findRecipient(recipient) != null)
				filtered.add(mail);

		Collections.sort(filtered, (f, s) -> Long.compare(s.getSentDate(), f.getSentDate()));

		return filtered;
	}

	/**
	 * Return all mails the sender has sent
	 *
	 * @param sender
	 * @return
	 */
	public List<Mail> findMailsFrom(UUID sender) {
		final List<Mail> filtered = new ArrayList<>();

		for (final Mail mail : this.mails)
			if (mail.getSender().equals(sender))
				filtered.add(mail);

		Collections.sort(filtered, (f, s) -> Long.compare(s.getSentDate(), f.getSentDate()));

		return filtered;
	}

	/**
	 * Look up mail by its unique ID or return null if not found
	 *
	 * @param uniqueId
	 * @return
	 */
	public Mail findMail(UUID uniqueId) {
		for (final Mail mail : this.mails)
			if (mail.getUniqueId().equals(uniqueId))
				return mail;

		return null;
	}

	/**
	 * Marks the given email as opened by the given recipient
	 */
	public void markOpen(Mail mail, Player recipientPlayer) {
		final Recipient recipient = mail.findRecipient(recipientPlayer.getUniqueId());
		Valid.checkNotNull(recipient, recipientPlayer.getName() + " has never read mail: " + mail.getTitle() + " from " + mail.getSenderName());

		recipient.markOpened();

		save();
	}
}
