package org.mineacademy.chatcontrol.model;

import java.lang.Character.UnicodeBlock;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.mineacademy.chatcontrol.SenderCache;
import org.mineacademy.chatcontrol.operator.Rule;
import org.mineacademy.chatcontrol.operator.Rule.Type;
import org.mineacademy.chatcontrol.settings.Settings;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.TabUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.exception.EventHandledException;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.exception.RegexTimeoutException;
import org.mineacademy.fo.model.HookManager;
import org.mineacademy.fo.model.LimitedQueue;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.Remain;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers.ChatType;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Represents packet handling using ProtocolLib
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Packets {

	/**
	 * The singleton for this class
	 */
	@Getter
	private static final Packets instance = new Packets();

	/**
	 * Players being processed RIGHT NOW inside the method. Prevents dead loop.
	 */
	private final Set<String> processedPlayers = new HashSet<>();

	/**
	 * Connects tab-complete sending and receiving packet
	 */
	private final Map<String, String> buffers = new HashMap<>();

	/**
	 * Register and initiate packet listening
	 */
	public void load() {
		Valid.checkBoolean(HookManager.isVaultLoaded(), "ProtocolLib integration requires Vault to be installed. Please install that plugin before continuing.");

		if (!Settings.Integration.ProtocolLib.LISTEN_FOR_PACKETS)
			return;

		if (!HookManager.isProtocolLibLoaded()) {
			Common.log("&cWarning: &fNo ProtocolLib found, some features will not be available.");

			return;
		}

		//
		// Process tab-completions for legacy Minecraft versions
		//
		if (MinecraftVersion.olderThan(V.v1_13)) {

			// Receiving tab complete request
			HookManager.addPacketListener(new PacketAdapter(SimplePlugin.getInstance(), ListenerPriority.HIGHEST, PacketType.Play.Client.TAB_COMPLETE) {
				@Override
				public void onPacketReceiving(PacketEvent event) {
					if (event.getPlayer() == null)
						return;

					String buffer = event.getPacket().getStrings().read(0);

					// Save for sending later, see below
					buffers.put(event.getPlayer().getName(), buffer);

					if (Settings.TabComplete.ENABLED && !HookManager.hasProtocolLibPermission(event.getPlayer(), Permissions.Bypass.TAB_COMPLETE)) {
						buffer = buffer.trim();

						if (buffer.startsWith("/")) {
							final String[] split = buffer.split(" ");

							final String label = split.length > 1 ? split[0] : "";
							final String lastWord = split.length > 1 ? split[split.length - 1] : "";

							if ((lastWord.length() < Settings.TabComplete.PREVENT_IF_BELOW_LENGTH) || (!"".equals(label) && !Settings.TabComplete.WHITELIST.isInListRegex(label)))
								event.setCancelled(true);
						}
					}
				}
			});

			// Sending tab complete
			HookManager.addPacketListener(new PacketAdapter(SimplePlugin.getInstance(), ListenerPriority.HIGHEST, PacketType.Play.Server.TAB_COMPLETE) {
				@Override
				public void onPacketSending(PacketEvent event) {
					if (event.getPlayer() == null)
						return;

					final String buffer = buffers.remove(event.getPlayer().getName());

					if (buffer == null)
						return;

					final boolean hasBypass = HookManager.hasProtocolLibPermission(event.getPlayer(), Permissions.Bypass.REACH);
					final PacketContainer packet = event.getPacket();
					final List<String> suggestions = Common.toList(packet.getStringArrays().read(0));
					final Set<String> nicks = new HashSet<>();

					for (final Iterator<String> it = suggestions.iterator(); it.hasNext();) {
						final String suggestion = it.next();
						final Player player = Players.getPlayer(suggestion);

						if (player != null) {
							if (hasBypass || !PlayerUtil.isVanished(player))
								nicks.add(Players.getNickColorless(player));

							it.remove();
						}
					}

					// Add all nicknames matching the word, ignoring commands
					if (!buffer.startsWith("/")) {
						final String word = buffer.endsWith(" ") ? "" : Common.last(buffer.split(" "));

						nicks.addAll(TabUtil.complete(word, Players.getPlayerNames(hasBypass)));
					}

					// Merge together and sort
					final List<String> allTogether = Common.joinArrays(suggestions, nicks);
					Collections.sort(allTogether);

					packet.getStringArrays().write(0, Common.toArray(allTogether));
				}
			});
		}

		//
		// Process chat messages
		//
		HookManager.addPacketListener(new PacketAdapter(SimplePlugin.getInstance(), ListenerPriority.HIGHEST, PacketType.Play.Server.CHAT) {

			@Override
			public void onPacketSending(PacketEvent event) {
				if (event.getPlayer() == null)
					return;

				final Player player = event.getPlayer();
				final String playerName = event.getPlayer().getName();

				// Ignore dummy instances and rare reload case
				if (!player.isOnline() || SimplePlugin.isReloading())
					return;

				// Prevent deadlock
				if (processedPlayers.contains(playerName))
					return;

				// Lock processing to one instance only to prevent another packet filtering
				// in a filtering
				try {
					processedPlayers.add(player.getName());

					final StructureModifier<Object> packet = event.getPacket().getModifier();
					final StructureModifier<WrappedChatComponent> chat = event.getPacket().getChatComponents();
					final WrappedChatComponent component = chat.read(0);

					try {
						final ChatType chatType = event.getPacket().getChatTypes().readSafely(0);

						if (chatType == ChatType.GAME_INFO)
							return;

					} catch (final NoSuchMethodError t) {
						// Silence on legacy MC
					}

					boolean isBaseComponent = false;
					String jsonMessage = null;

					if (component != null)
						jsonMessage = component.getJson();

					// Md_5 way of dealing with packets
					else if (packet.size() > 1) {
						final Object secondField = packet.readSafely(1);

						if (secondField instanceof BaseComponent[]) {
							jsonMessage = Remain.toJson((BaseComponent[]) secondField);

							isBaseComponent = true;
						}
					}

					if (jsonMessage != null && !jsonMessage.isEmpty()) {

						// Only check valid messages, skipping those over 50k since it would cause rules
						// to take too long and overflow. 99% packets are below this size, it may even be
						// that such oversized packets are maliciously sent so we protect the server from freeze
						if (jsonMessage.length() < 50_000) {
							final String legacyText;

							// Catch errors from other plugins and silence them
							try {
								legacyText = Remain.toLegacyText(jsonMessage, false);

							} catch (final Throwable t) {
								return;
							}

							String parsedText = legacyText;

							try {
								Debugger.debug("rules", "Packet rules parse: '" + Common.stripColors(parsedText) + "'");

								parsedText = Rule.filter(Type.PACKET, player, parsedText, null).getMessage();

							} catch (final RegexTimeoutException ex) {
								// Such errors mean the parsed message took too long to process.
								// Only show such errors every 30 minutes to prevent console spam
								Common.logTimed(1800, "&cWarning: Packet message '" + Common.limit(jsonMessage, 500)
										+ "' (possibly longer) took too long time to check for packet rules and was ignored."
										+ " This message only shows once per 30 minutes when that happens. For most cases, this can be ignored.");

								return;

							} catch (final EventHandledException ex) {
								event.setCancelled(true);

								return;
							}

							if (!Common.stripColors(legacyText).equals(Common.stripColors(parsedText))) {
								jsonMessage = Remain.toJson(parsedText);

								if (isBaseComponent)
									packet.writeSafely(1, Remain.toComponent(jsonMessage));
								else
									chat.writeSafely(0, WrappedChatComponent.fromJson(jsonMessage));
							}
						}

						final LimitedQueue<String> playerMessageLog = SenderCache.from(playerName).getLastChatPackets();

						playerMessageLog.add(jsonMessage);
					}

				} finally {
					processedPlayers.remove(player.getName());
				}
			}
		});

		//
		// Process book editing
		//
		if (MinecraftVersion.atLeast(V.v1_13)) {
			PacketType bookEditPacket = null;

			try {
				bookEditPacket = PacketType.fromName("BOOK_EDIT").iterator().next();
			} catch (final Throwable t) {
			}

			if (bookEditPacket == null)
				try {
					bookEditPacket = PacketType.fromName("B_EDIT").iterator().next();
				} catch (final Throwable tt) {
					// Fail through
				}

			if (bookEditPacket != null && Settings.Integration.ProtocolLib.BOOK_ANTI_CRASH) {
				HookManager.addPacketListener(new PacketAdapter(SimplePlugin.getInstance(), ListenerPriority.LOW, bookEditPacket) {

					@Override
					public void onPacketReceiving(PacketEvent event) {
						final StructureModifier<ItemStack> itemModifier = event.getPacket().getItemModifier();
						final ItemStack itemStack = itemModifier.read(0);

						if (!(itemStack.getItemMeta() instanceof BookMeta))
							return;

						final BookMeta meta = (BookMeta) itemStack.getItemMeta();
						final List<String> originalPages = meta.getPages();
						final List<String> pages = new ArrayList<>();

						for (int i = 0; i < originalPages.size(); i++) {

							if (i > 100)
								break;

							// This will strip all diacritical marks from books
							final String page = Normalizer.normalize(originalPages.get(i), Normalizer.Form.NFD);

							// Start rebuilding the page letter by letter
							String replacedPage = "";

							// Check for max letter size
							int letterCount = 0;

							for (final char letter : page.toCharArray()) {
								final UnicodeBlock unicode = Character.UnicodeBlock.of(letter);

								if (letterCount > 500)
									break;

								// Only tolerate basic the most common characters but nothing else
								if (Character.isIdeographic(letter)
										|| unicode == Character.UnicodeBlock.BASIC_LATIN
										|| unicode == Character.UnicodeBlock.CYRILLIC
										|| unicode == Character.UnicodeBlock.ARABIC
										|| unicode == Character.UnicodeBlock.GREEK
										|| letter == ChatColor.COLOR_CHAR) {

									replacedPage += letter;
									letterCount++;
								}
							}

							pages.add(replacedPage);
						}

						// Write back
						meta.setPages(pages);
						itemStack.setItemMeta(meta);

						itemModifier.write(0, itemStack);
					}
				});
			}
		}
	}

	/**
	 * Remove the given message containing the given unique ID for all players,
	 * sending them their last 100 messages without it, or blank if not enough data
	 *
	 * Removal is depending on the given remove mode
	 *
	 * @param uniqueId
	 */
	public void removeMessage(RemoveMode mode, UUID uniqueId) {
		final String stringId = uniqueId.toString();

		Common.runAsync(() -> {
			for (final Iterator<SenderCache> cacheIt = SenderCache.getCaches().iterator(); cacheIt.hasNext();) {
				final SenderCache cache = cacheIt.next();
				final Player player = Bukkit.getPlayerExact(cache.getSenderName());
				final LimitedQueue<String> messages = cache.getLastChatPackets();

				if (player == null || !player.isOnline())
					continue;

				boolean found = false;

				for (final Iterator<String> it = messages.iterator(); it.hasNext();) {
					final String message = it.next();

					if (message.contains(mode.getPrefix() + "_" + stringId)) {
						it.remove();

						found = true;
					}
				}

				if (found && !this.processedPlayers.contains(player.getName()))
					try {
						this.processedPlayers.add(player.getName());

						// Fill in the blank if no data
						for (int i = 0; i < 100 - messages.size(); i++)
							player.sendMessage(" ");

						for (final String json : messages)
							try {
								Remain.sendJson(player, json);
							} catch (final RuntimeException ex) {
								// Hide malformed third party JSONs
							}

					} finally {
						this.processedPlayers.remove(player.getName());
					}
			}
		});
	}

	/**
	 * How we should remove sent messages?
	 */
	@RequiredArgsConstructor
	public enum RemoveMode {

		/**
		 * Only remove the message matching the UUID
		 */
		SPECIFIC_MESSAGE("SPECIFIC_MESSAGE", "flpm"),

		/**
		 * Remove all messages from the UUID of the sender
		 */
		ALL_MESSAGES_FROM_SENDER("ALL_MESSAGES_FROM_SENDER", "flps");

		/**
		 * The unobfuscatable key
		 */
		@Getter
		private final String key;

		/**
		 * The prefix used for matching in the method
		 */
		@Getter
		private final String prefix;

		/**
		 * @see java.lang.Enum#toString()
		 */
		@Override
		public String toString() {
			return this.key;
		}

		/**
		 * Parse from {@link #getKey()}
		 *
		 * @param key
		 * @return
		 */
		public static RemoveMode fromKey(String key) {
			for (final RemoveMode mode : values())
				if (mode.getKey().equalsIgnoreCase(key))
					return mode;

			throw new FoException("Invalid RemoveMode." + key);
		}
	}
}
