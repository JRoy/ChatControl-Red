# ---------------------------------------------------------------------------------
# Timed messages broadcaster. Uses the same syntax as files in rules/ folder but
# operators are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl-Red/wiki/messages
#
# They are read like a newspaper and each player only sees one message,
# that is the first one we can send to him. We select 1 message randomly from 
# "messages:" list.
# ---------------------------------------------------------------------------------

# You can run limited time offers and even make clickable links by referencing
# to a format from /formats in your message.
#group special
#expires 30 Dec 2020, 15:00
#then sound ENTITY_ARROW_HIT_PLAYER, 1.0, 0.1
#delay 6 seconds
#message:
#- special-offer

# This group will simply send messages everywhere.
#group global
#delay 1 second
#message:
#- Hey, {player}, did you know that we are running ChatControl?
#- Check out &ahttps://mineacademy.org/plugins

# This group will send messages only to the given worlds.
#group hardcore
#require sender world hardcore|hardcore_nether|hardcore_end
#message:
#- Grief is not permitted what-so-ever and every griefer will be banned.
#- Can you survive the night on {world} world?

# This group will only send messages to the creative world.
#group creative
#require sender world creative
#message:
#- Welcome on Creative world. Enjoy your gamemode :)
#- This is an example of multi-line.
#  Use it if the new '\ and n' character
#  is not working on your server.
#- This is another message, this time only on one line!