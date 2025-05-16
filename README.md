# AnvilWatch

**AnvilWatch** is a lightweight Minecraft moderation plugin that monitors and controls item renaming through anvils. Designed for server administrators, it ensures that all rename actions are tracked and that inappropriate names are blocked automatically based on a configurable banned word list.

## Features

- Logs all item renames done through anvils to a log file
- Blocks item names that contain banned words (case-insensitive)
- Alerts all online admins with the correct permission when a rename occurs
- Supports permission-based bypass for trusted users
- Provides a set of admin commands to manage banned words and plugin behavior
- Live reloading of banned word list without restarting the server

## Why Use AnvilWatch?

Players renaming items with offensive, inappropriate, or disruptive names is a common issue on Minecraft servers. AnvilWatch provides a simple and effective way to:

- Automatically prevent these renames using a customizable word filter
- Keep a detailed log of all rename events for moderation and accountability
- Notify staff in real time when rename attempts occur
- Manage filters and settings easily in-game using commands

## Commands

| Command | Description |
|--------|-------------|
| `/anvilwatch help` | Displays a list of available commands |
| `/anvilwatch reload` | Reloads the banned word list from `BannedWords.txt` |
| `/anvilwatch add <word>` | Adds a word to the banned word list |
| `/anvilwatch remove <word>` | Removes a word from the banned word list |
| `/anvilwatch log <on/off>` | Toggles in-game rename log messages for the user |

**Alias:** `/anw`  
**Usage:** `/anvilwatch <help|reload|add|remove|log> <args>`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `anvilwatch.admin` | Receive rename alerts and use plugin management commands | `op` |
| `anvilwatch.bypass` | Allows renaming items without word filter restrictions | `false` |

## Configuration

- **Banned Word List:** Managed in the `BannedWords.txt` file located in the plugin folder.
- **Log File:** Rename events are written to a log file inside the plugin directory.
- **No server restart required:** Changes to the banned word list from in-game are updated on the fly. Banned words added through the config file can be applied using `/anvilwatch reload`.

## Compatibility

- **Minecraft Version:** 1.21 - 1.21.4
- **API Version:** 1.21
- **Soft Dependencies:** None
- **Plugin Version:** `1.0`

## Getting Started

1. Drop the plugin JAR into your server's `plugins/` folder.
2. Start your server to generate the config and `BannedWords.txt`.
3. Add banned words either manually to the file or in-game using `/anvilwatch add <word>`.
4. Assign the `anvilwatch.admin` permission to trusted staff.
5. Monitor the log file and in-game alerts as players rename items.