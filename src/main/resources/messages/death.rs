# ---------------------------------------------------------------------------------
# Death messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl-Red/wiki/messages
# ---------------------------------------------------------------------------------

# They are read like a newspaper and each player only sees one message,
# that is the first one we can send to him.

# An example of not sending any message when player dies having creative gamemode
group silent_on_creative
require sender gamemode CREATIVE
message: 
- none

# Require the player to by killed by an arrow.
group playerArrow
require projectile arrow
require killer player
message:
- {player} has been murdered by {killer}'s [killer_item]

# Require the player to by killed by a trident.
#group playerTrident
#require projectile trident
#require killer player
#message:
#- {player} has thrown [item] at {killer}

# Require the player to be killed by himself throwing an enderpearl.
group enderPearl
require self
require cause fall
require projectile ender_pearl
message:
- {player} has died by ender pearl

# Support MythicMobs or Boss plugin and send a special death message.
group boss
require boss *
message:
- {player} has died by a Boss {boss_name}

#
# Below you will find TONS of preconfigured messages, you can edit all of those.
# Scroll to the end for the default message that shows when none of these conditions match.
#

group contact
require cause CONTACT
message: 
- &e{player} &7tried to hug a cactus
- &e{player} &7tried to fist bump with a cactus
- &7That there is what we call a prickly pear, yeehaw and ouch &e{player}

group entity-attack-by-blaze
require cause ENTITY_ATTACK
require killer BLAZE
message: 
- &e{player} &7was burnt to a crisp by a blaze

group entity-attack-by-cave-spider
require cause ENTITY_ATTACK
require killer CAVE_SPIDER
message: 
- &e{player} &7couldn't find the cave spider andidote in time

group entity-attack-by-dragon-fireball
require cause ENTITY_ATTACK
require killer DRAGON_FIREBALL
message: 
- &e{player} &7was hit by a dragon fireball

#group entity-attack-by-drowned
#require cause ENTITY_ATTACK
#require killer DROWNED
#message: 
#- &e{player} &7was murdered by drowned

#group entity-attack-by-elder-guardian
#require cause ENTITY_ATTACK
#require killer ELDER_GUARDIAN
#message: 
#- &e{player} &7was zapped by an elder guardian

group entity-attack-by-ender-dragon
require cause ENTITY_ATTACK
require killer ENDER_DRAGON
message: 
- &e{player} &7failed to train the dragon

group entity-attack-by-enderman
require cause ENTITY_ATTACK
require killer ENDERMAN
message: 
- &e{player} &7lost a staring contest with an enderman

group entity-attack-by-endermite
require cause ENTITY_ATTACK
require killer ENDERMITE
message: 
- &e{player} &7tried to feed an endermite and failed

#group entity-attack-by-evoker-fangs
#require cause ENTITY_ATTACK
#require killer EVOKER_FANGS
#message: 
#- &e{player} &7could not evoke a win from that evoker

group entity-attack-by-giant
require cause ENTITY_ATTACK
require killer GIANT
message: 
- &e{player} &7got squished under a giant's foot

#group entity-attack-by-guardian
#require cause ENTITY_ATTACK
#require killer GUARDIAN
#message: 
#- &e{player} &7was zapped by a guardian

#group entity-attack-by-husk
#require cause ENTITY_ATTACK
#require killer HUSK
#message: 
#- &e{player} &7was zombified by a husk

group entity-attack-by-iron-golem
require cause ENTITY_ATTACK
require killer IRON_GOLEM
message: 
- &e{player} &7upset an iron golem and paid the price

group entity-attack-by-lightning
require cause ENTITY_ATTACK
require killer LIGHTNING
message: 
- &e{player} &7was hugging a metal pole in a thunderstorm

group entity-attack-by-magma-cube
require cause ENTITY_ATTACK
require killer MAGMA_CUBE
message: 
- &e{player} &7was burnt to a crisp by a magma cube

#group entity-attack-by-phantom
#require cause ENTITY_ATTACK
#require killer PHANTOM
#message: 
#- &e{player} &7was taken away by phantom

group entity-attack-by-pig-zombie
require cause ENTITY_ATTACK
require killer ZOMBIFIED_PIGLIN
message: 
- &e{player} &7angered a zombie pigman

group entity-attack-by-primed-tnt
require cause ENTITY_ATTACK
require killer PRIMED_TNT
message: 
- &e{player} &7died from TNT

group entity-attack-by-silverfish
require cause ENTITY_ATTACK
require killer SILVERFISH
message: 
- &e{player} &7was infested by a silverfish

group entity-attack-by-slime
require cause ENTITY_ATTACK
require killer SLIME
message: 
- &e{player} &7ate too much jello

group entity-attack-by-spider
require cause ENTITY_ATTACK
require killer SPIDER
message: 
- &e{player} &7suffered fatally from arachnaphobia

#group entity-attack-by-vex
#require cause ENTITY_ATTACK
#require killer VEX
#message: 
#- &e{player} &7couldn't use bug spray fast enough on that vex

#group entity-attack-by-vindicator
#require cause ENTITY_ATTACK
#require killer VINDICATOR
#message: 
#- &e{player} &7tried to axe the vindicator a question

#group entity-attack-by-wither-skeleton
#require cause ENTITY_ATTACK
#require killer WITHER_SKELETON
#message: 
#- &e{player} &7bothered a very grumpy wither skeleton

group entity-attack-by-wolf
require cause ENTITY_ATTACK
require killer WOLF
message: 
- &7Poor &e{player}&7, that wasn't a doggie, that was a wolf

group entity-attack-by-zombie
require cause ENTITY_ATTACK
require killer ZOMBIE
message: 
- &e{player} &7found out the hard way that zombies don't like hugs

group entity-attack-by-zombie-villager
require cause ENTITY_ATTACK
require killer ZOMBIE_VILLAGER
message: 
- &e{player} &7failed to cure the zombie villager

group entity-attack-by-pvp-diamond-sword
require cause ENTITY_ATTACK
require killer PLAYER
require killer item DIAMOND_SWORD
message: 
- &c{player} &7was distracted by {killer}&7's shiny [killer_item]!

group entity-attack-by-pvp-raw-fish
require cause ENTITY_ATTACK
require killer PLAYER
require killer item RAW_FISH
message: 
- &c{player} &7was trout slapped by {killer}&7's [killer_item]!

group entity-attack-by-pvp-wood-sword
require cause ENTITY_ATTACK
require killer PLAYER
require killer item *_SWORD
message: 
- &c{player} &7got a splinter and died from &a{killer}&7's [killer_item]!

group entity-attack-by-pvp-wood-axe
require cause ENTITY_ATTACK
require killer PLAYER
require killer item *_AXE
message: 
- &c{player} &7asked {killer}&7's [killer_item] a terrible knock knock joke

group entity-attack-by-pvp-x
require cause ENTITY_ATTACK
require killer PLAYER
message: 
- &c{player} &7was annoyed to death by &a{killer}&7!
- &a{killer} &7has slain &c{player}&7!
- &a{killer} &7is victorious over &c{player}&7!
- &a{killer} &7has overpowered &c{player} &7in combat!
- &a{killer} &7killed &c{player}&7!
- &a{killer} &7has ended poor &c{player}&7!
- &a{killer} &7overcame &c{player} &7in battle!
- &a{killer} &7defeated &c{player} &7in mighty struggle!
- &a{killer} &7ended all dreams for &c{player}&7!

group entity-attack
require cause ENTITY_ATTACK
message: 
- &e{player} &7wrongly assumed {killer} liked hugs

group projectile-by-fireball
require cause PROJECTILE
require killer FIREBALL
message: 
- &e{player} &7failed to dodge a fireball

group projectile-by-skeleton
require cause PROJECTILE
require killer SKELETON
message: 
- &e{player} &7couldn't outrun that skeleton

#group projectile-by-stray
#require cause PROJECTILE
#require killer STRAY
#message: 
#- &e{player} &7didn't stray away long enough

group projectile-by-wither-skull
require cause PROJECTILE
require killer WITHER_SKULL
message: 
- &e{player} &7got up close and personal with a wither skull

group projectile-by-firework
require cause PROJECTILE
require killer FIREWORK
message: 
- &e{player} &7forgot to run away after lighting a firework

group projectile-by-ghast
require cause PROJECTILE
require killer GHAST
message: 
- &e{player} &7couldn't deflect the ghast's fireball in time

group projectile
require cause PROJECTILE
message: 
- &e{player} &7made a great porcupine impression

group suffocation
require cause SUFFOCATION
message: 
- &e{player} &7had their head in a block for too long

group fall
require cause FALL
message: 
- &e{player} &7got up close and personal with the ground
- &e{player} &7believed they could fly
- &e{player} &7thought they were a bird
- &e{player} &7tripped over a rock
- &e{player} &7was texting while driving
- &e{player} &7forgot their feather falling boots
- &e{player} &7slipped on a banana peel
- &e{player} &7tried to take the high way
- &e{player} &7tried to fly like Superman and failed
- &e{player} &7forgot to look before they leaped
- &e{player} &7face planted
- &e{player} &7went skydiving and forgot their parachute
- &e{player} &7tried to fly like an eagle, but it was more like a brick
- &e{player} &7isn't a bird, but congrats on the SPLAT sound, it was perfect!
- &e{player} &7performed a successful test of the laws of gravity

group fire
require cause FIRE
message: 
- &e{player} &7was turned into a well cooked steak
- &e{player} &7forgot to stop, drop, and roll
- &e{player} &7forgot to drink a fire resistance potion
- &e{player} &7wanted to be the next Human Torch
- &e{player} &7forgot to put on sunscreen

group fire-tick
require cause FIRE_TICK
message: 
- &e{player} &7couldn't find the fire extinguisher
- &e{player} &7ate one too many bowls of extra spicy chili
- &e{player} &7was playing with matches
- &e{player} &7thought yelling would stop the fire

group lava
require cause LAVA
message: 
- &e{player} &7was no match for a pool of lava
- &e{player} &7was trying to craft a lava lamp with real lava
- &e{player} &7mistook lava for a hot tub
- &e{player} &7couldn't take the heat
- &e{player} &7tried dancing on lava.  Burn, baby, burn!
- &7Silly &e{player}&7... That isn't Kool Aid, that's lava

group drowning
require cause DROWNING
message: 
- &e{player} &7needs to learn to swim
- &e{player} &7lost a breath holding contest with a fish
- &e{player} &7thought for sure they were a fish
- &e{player} &7decided to go swimming with the dolphins
- &e{player} &7forgot they didn't have gills
- &e{player} &7took a nap during swim class
- &e{player} &7tried a little too hard to find Atlantis
- &e{player} &7realized their requirements for oxygen way too late
- &e{player}&7, swimming with a pumpkin on your head is not SCUBA diving
- &e{player}&7, you are not a fish! Stop breathing water! I said stop!...Too late
- &7Dog paddling wasn't enough for &e{player}

group block-explosion
require cause BLOCK_EXPLOSION
message: 
- &e{player} &7thought TNT stood for Totally Not TNT
- &e{player} &7forgot TNT goes ka-boom
- &e{player} &7learned the hard way what the Big Bang Theory really is
- &e{player} &7only wanted to travel to the moon. Looks like that rocket blew up
- &e{player} &7went boom, boom, BOOM! Silly &e{player}

group entity-explosion-by-creeper
require cause ENTITY_EXPLOSION
require killer CREEPER
message: 
- &e{player} &7was the victim of a creeper ambush
- &e{player} &7believed creepers were the hugging type
- &e{player} &7thought that creeper was a bush
- &e{player} &7didn't know walking TNT could be so well camoflauged
- &e{player} &7tried to hug a creeper
- &e{player} &7tried to befriend a creeper
- &e{player} &7should listen more carefully for &2creepers&f next time
- &7Oh my gosh &e{player}&7, look at that guy. He is so creepy! But I hear he's the bomb!
- &7A creeper blast vaporized &e{player}

group entity-explosion-by-ender-crystal
require cause ENTITY_EXPLOSION
require killer ENDER_CRYSTAL
message: 
- &e{player} &7sneezed too hard next to an end crystal

group entity-explosion-by-primed-tnt
require cause ENTITY_EXPLOSION
require killer PRIMED_TNT
message: 
- &e{player} &7died from TNT

group entity-explosion-by-wither
require cause ENTITY_EXPLOSION
require killer WITHER
message: 
- &e{player} &7tried to fist bump a spawning wither

group entity-explosion
require cause ENTITY_EXPLOSION
message: 
- &e{player} &7blew up

group void
require cause VOID
message: 
- &e{player} &7figured out how to mine bedrock
- &e{player} &7went swimming in the void
- &e{player} &7forgot to look where they were walking
- &e{player} &7wanted to a-void you
- &7The void has claimed &e{player}

group lightning
require cause LIGHTNING
message: 
- &e{player} &7was thunderstruck
- &e{player} &7was seeing stars
- &e{player} &7was destroyed by Thor's mighty hammer
- &e{player} &7thought it was a good idea to play in the rain
- &7Ca-chow! &e{player} &7was struck by Lightning... McQueen
- &7I'm just as shocked as you are, &e{player}

group suicide
require cause SUICIDE
message: 
- &e{player} &7decided to Let It Go...
- &e{player} &7was running with scissors
- &e{player} &7fell on their sword... ouch
- &7Life was too hard for &e{player}
- &7Oh yes, that was brilliant &e{player}&7.  Congrats, you are dead. *facepalm*

group starvation
require cause STARVATION
message: 
- &e{player} &7should learn to eat
- &e{player} &7doesn't know how to cook
- &e{player} &7skipped breakfast... and lunch... and dinner

group magic-by-players-potion
require cause MAGIC
require killer PLAYER
message: 
- &e{player} &7was killed by {killer}'s deadly potion

group magic-by-witchs-potion
require cause MAGIC
require killer WITCH
message: 
- &e{player} &7got too close to a witch

group magic
require cause MAGIC
message: 
- &e{player} &7has died of magic

group wither
require cause WITHER
message: 
- &e{player} &7slowly withered away

group falling-block
require cause FALLING_BLOCK
message: 
- &e{player} &7tried to catch a falling anvil and failed

group thorns
require cause THORNS
message: 
- &e{player} &7has died from thorns damage

group dragon-breath
require cause DRAGON_BREATH
message: 
- &7The Enderdragon has annihilated &e{player}
- &e{player} &7couldn't tame a NightFury

group custom
require cause CUSTOM
message: 
- &e{player} &7has miraculously died

group fly-into-wall
require cause FLY_INTO_WALL
message: 
- &e{player} &7hasn't quite mastered the art of flying yet

group hot-floor
require cause HOT_FLOOR
message: 
- &e{player} &7couldn't do the fire walk after all

group cramming
require cause CRAMMING
message: 
- &e{player} &7felt what it was like to be a sardine

group dryout
require cause DRYOUT
message: 
- &e{player} &7has dried out

# If no group from above applies, fall back and send a default message.
group default
# You can also broadcast this over BungeeCord
#bungee
then log {player} has died at {world} {x} {y} {z} by {cause}
message:
- {player} has died by unknown forces
# Or you can just show the one from Minecraft
#- {original_message}