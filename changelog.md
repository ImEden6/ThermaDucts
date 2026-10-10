# v1.0.2

## Bug Fixes

- Fixed missing block and item models: ducts and items no longer show as missing textures.
- Fixed missing crafting recipes: all ducts, attachments and materials are craftable again.
- Fixed ducts dropping nothing when broken: loot tables were missing from the jar.
- Fixed attachments (servos, filters, retrievers, relays) being destroyed when a duct is mined or blown up. They now drop as items.
- Fixed a multiplayer exploit where a modified client could teleport into any transport network from anywhere, or change and rename other players' ducts from any distance. The server now only accepts changes from the menu the player has open, while they're in range.
- Relay menus now close when you walk away or the relay is removed, like the other menus.

## Changes

- Updated to NeoForge 26.1.2.115. The mod now requires NeoForge 26.1.2.76 or newer.
