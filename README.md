# AnvilWatch

**AnvilWatch** is a lightweight Minecraft moderation plugin that monitors and controls item renaming through anvils. Designed for server administrators, it ensures that all rename actions are tracked and that inappropriate names are blocked automatically based on a configurable banned word list.
<p align="center">
    <a href="https://discord.gg/Evywvfz2FX">
        <img src="https://cdn.modrinth.com/data/cached_images/5ac8884b3c4916cbac2b514220b9bb678db039b7.png" width="300">
    </a>
    <br>
    <i>Please join the Discord if you have questions!</i>
</p>

## Features

- Logs all item renames done through anvils to a log file
- Blocks item names that contain banned words (case-insensitive)
- Alerts all online admins with the correct permission when a rename occurs
- Supports permission-based bypass for trusted users
- Provides a set of admin commands to manage banned words and plugin behavior
- Live reloading of banned word list without restarting the server
- **Advanced Filtering:** Supports Regular Expressions (Regex), Homoglyph normalization, and Leet-speak detection.
- Alerts all online admins with the correct permission when a rename occurs.

## Why Use AnvilWatch?

Players renaming items with offensive, inappropriate, or disruptive names is a common issue on Minecraft servers. AnvilWatch provides a simple and effective way to:

- Automatically prevent these renames using a customizable word filter
- Keep a detailed log of all rename events for moderation and accountability
- Notify staff in real time when rename attempts occur
- Manage filters and settings easily in-game using commands

## Regex & Advanced Filtering
AnvilWatch supports powerful **Regular Expressions (Regex)** for advanced moderation. Each entry in your `BannedWords.txt` is treated as a regex pattern, allowing you to block entire families of words or specific character patterns.
- **Case-Insensitive:** All checks are automatically case-insensitive.
- **Pattern Matching:** Use regex to catch variations. For example, `bad.*` will block "badword", "badstuff", and "badlink".
- **Character Sets:** Use `b[a4]dw[o0]rd` to catch leet-speak variations like "b4dw0rd".
- **Normalizations:** The plugin automatically converts homoglyphs (look-alike characters from other alphabets) and leet-speak back to standard text before checking your filters, preventing simple bypasses.

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

- **Minecraft Version:** 1.21.*
- **API Version:** 1.21
- **Dependencies:** None

## Getting Started

1. Drop the plugin JAR into your server's `plugins/` folder.
2. Start your server to generate the config and `BannedWords.txt`.
3. Add banned words either manually to the file or in-game using `/anvilwatch add <word>`.
4. Assign the `anvilwatch.admin` permission to trusted staff.
5. Monitor the log file and in-game alerts as players rename items.

[![bStats Metrics](https://bstats.org/signatures/bukkit/anvilwatch.svg)](https://bstats.org/plugin/bukkit/AnvilWatch)