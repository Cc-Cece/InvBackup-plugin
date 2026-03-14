## InvBackup (English Full)

InvBackup is a **player inventory and state backup & restore plugin** for Paper / Spigot servers, designed around a **safe, auditable restore request queue plus GUIs**.

Most restore operations only create restore requests and let players claim items themselves via a GUI, while `/ib forcerestore` remains as a single, explicit “direct restore” entry for emergencies.

---

## Overview of Features

- **Backup capabilities**
  - Manual backups:
    - Players: `/ib save [label]` creates a snapshot for themselves.
    - Admins: `/ib saveall [label]` creates snapshots for all online players.
  - Automatic backups (configured in `config.yml`):
    - On player quit: `auto-backup.on-quit`
    - On player join: `auto-backup.on-join`
    - Periodic for all online players: `auto-backup.interval` (seconds)
  - Backup levels:
    - `backup-level: minimal`:
      - Stores only inventory contents: main inventory, armor, offhand, ender chest.
    - `backup-level: full`:
      - In addition to the above, also stores:
        - health, max health;
        - food level, saturation;
        - exp, level, total experience;
        - gamemode, flight state;
        - potion effects;
        - location (world, coordinates, yaw, pitch).

- **Restore requests**
  - When admins trigger restores via commands or GUIs, they normally **create a restore request** instead of restoring immediately:
    1. A `RestoreRequest` is written under `data/pending/<targetUuid>.yml`.
    2. If the target player is online, they receive a chat message with `[Accept] / [Decline]` buttons; otherwise the request is queued and delivered when they join.
    3. Players run `/ib accept <requestId>` or click `[Accept]` to open the `RestoreGui` and claim items or state.
  - Each request includes:
    - `sourceUuid/sourceName`: snapshot owner (can differ from target for cross-player restores);
    - `targetUuid/targetName`: player who will receive the restore;
    - `snapshotId`: snapshot identifier;
    - `requestedBy/requestedByUuid`: who created the request (admin/console);
    - `status`: `pending/accepted/declined/expired/revoked`;
    - `openExpiredAt`: timestamp until which `RestoreGui` can be re-opened after acceptance.

- **Restore history & anti-abuse**
  - `RestoredTracker` persists which parts of a snapshot have already been restored in `data/history/<uuid>.yml` under `restored.<snapshotId>`:
    - restored main inventory slot indices;
    - restored armor indices;
    - a flag for offhand;
    - restored ender chest slot indices;
    - restored status keys (`health/food/exp/location/effects/gamemode`).
  - For regular players in `RestoreGui`:
    - already restored slots/parts/statuses are rendered as “claimed” and cannot be reclaimed;
    - even if they open the GUI multiple times or receive multiple requests for the same snapshot, they will not receive duplicated items or state.
  - Admins are allowed to bypass these checks in GUIs when necessary for manual corrections.

- **GUIs**
  - `AdminGui` (admin main GUI)
    - Entry: `/ib gui`.
    - Player list:
      - Lists all players that have backups (scanned from `data/history` and `data/current`);
      - clicking a head opens that player’s snapshot list;
      - navigation row includes previous/next page and a “bulk restore” button.
    - Snapshot list:
      - Shows snapshot ID, timestamp, label, trigger type, triggerer, backup level;
      - clicking an entry opens `PreviewGui`.
  - `PreviewGui` (snapshot preview GUI)
    - Entry:
      - Players: `/ib preview <id>`;
      - Admins: `/ib preview <player> <id>`;
      - Or via `AdminGui`.
    - Shows:
      - main inventory 0–35;
      - armor and offhand;
      - ender chest contents (preview);
      - player status (health/food/exp/gamemode/location/effects).
    - Admin actions:
      - trigger an “items only” or “full” restore request (actual restoration still happens in `RestoreGui`);
      - if `restore-request.manual-name-input.enabled` is `true`, an extra entry lets you queue this snapshot to another player by name (A → B).
  - `RestoreGui` (player restore GUI)
    - Entry: player runs `/ib accept <requestId>` or click the `[Accept]` button.
    - Capabilities:
      - claim individual main-inventory slots;
      - restore armor pieces one by one (existing armor is moved to inventory);
      - restore offhand;
      - restore ender chest (current implementation directly overwrites the ender chest and marks all ender slots as restored);
      - independent status buttons:
        - health, food, exp, location, effects, gamemode;
      - “Restore all”:
        - automatically creates a safety snapshot of the current state before restoring;
        - obeys `restore-request.restore-all-overflow` (`drop` vs `keep`) for overflowed items.
    - When opened via a request:
      - The GUI indicates that it was opened from a restore request;
      - It displays whether the request can be re-opened, based on `open-window-seconds`;
      - Each click validates that the request has not been revoked by an admin.

- **Bulk restore GUI (`BulkRestoreGui`)**
  - Entry: from the Admin player list (`AdminGui`) bottom “bulk restore” button.
  - Features:
    - multi-select players (per-page or entire list);
    - filters:
      - `Latest`: whether to fall back to the latest snapshot when others don’t match filters;
      - `Survival only`: only use snapshots with `backupLevel=full`;
      - `Recent`: time window (unlimited / last 24h / last 72h).
    - On send:
      - picks a suitable snapshot for each selected player using the filters;
      - creates a restore request and notifies online players;
      - sends an overall summary (`sent / skipped / total`) to the admin.

- **Import / export / web tooling**
  - Import (with confirmation GUI):
    - `/ib import file:<name>.yml`:
      - reads from `plugins/InvBackup/data/imports/<name>.yml`;
    - `/ib import folder:<name>`:
      - reads from all `<uuid>.yml` files inside `plugins/InvBackup/data/imports/<name>/`;
    - `ImportConfirmGui` allows:
      - selecting / unselecting entries;
      - select-all / clear;
      - Shift-left-click preview (jumps to `PreviewGui`, then returns);
      - importing the selected entries or all entries into history.
  - Export:
    - `/ib export <folder>`:
      - exports all players’ current snapshots to `data/imports/<folder>/`;
    - `/ib export <folder> <player>`:
      - exports only that player’s current snapshot.
  - JSON / web tooling:
    - `/ib exportjson file:<uuid>.yml`:
      - reads from `json-tool/import/<uuid>.yml` and writes JSON to `json-tool/export/<uuid>.json`;
    - `/ib exportjson folder:<name>`:
      - converts all `.yml` in `json-tool/import/<name>/` to `.json` in `json-tool/export/<name>/`.
    - The exporter understands both InvBackup and CreativeManager-like YAML formats and normalizes them to a common JSON structure.

- **UUID migration & search**
  - `/ib migrate <oldUUID> <newUUID>`:
    - Migrates data in `data/history`, `data/current` and `data/pending` from `oldUUID` to `newUUID`;
    - Updates `meta.target-uuid` inside snapshots.
  - `/ib search <playerName>`:
    - Searches history and current data by player name;
    - Returns matching UUIDs and folder names.

---

## Installation & Requirements

- **Requirements**
  - Java 17+ (for `paper-1.18-1.20`) / Java 21+ (for `paper-1.21-plus`)
  - Paper / Spigot:
    - `paper-1.18-1.20`: `plugin.yml` `api-version: "1.18"`, built against `paper-api:1.18.2-R0.1-SNAPSHOT`;
    - `paper-1.21-plus`: `plugin.yml` `api-version: "1.21"`, built against `paper-api:1.21.4-R0.1-SNAPSHOT`.

- **Build profiles**
  - Build `paper-1.18-1.20`:
    - `./gradlew clean build -PbuildTarget=paper-1.18-1.20`
  - Build `paper-1.21-plus`:
    - `./gradlew clean build -PbuildTarget=paper-1.21-plus`
  - Build both:
    - `build-all.bat` (Windows)
    - `./build-all.sh` (Linux/macOS)
    - `powershell -ExecutionPolicy Bypass -File .\build-versions.ps1`
  - Output artifacts:
    - `dist/InvBackup-paper-1.18-1.20.jar`
    - `dist/InvBackup-paper-1.21-plus.jar`

- **Installation**
  1. Put the correct built jar into your server `plugins/` folder.
  2. Start the server to generate default config and language files:
     - `plugins/InvBackup/config.yml`
     - `plugins/InvBackup/lang/*.yml`
  3. Adjust `config.yml` and language files as needed.
  4. Use `/ib reload` or restart the server when you make changes.

---

## Configuration (`config.yml`)

Default structure (excerpt):

```yaml
language: zh_CN

backup-level: full

auto-backup:
  on-quit: true
  on-join: false
  interval: 0

max-snapshots: 20

restore-request:
  expire-days: 7
  open-window-seconds: 0
  restore-all-overflow: drop
  match-by-name: true
  manual-name-input:
    enabled: false
```

- **`language`**
  - Name (without `.yml`) of the active language file under `plugins/InvBackup/lang/`;
  - Built-in: `zh_CN`, `en_US`, `zh_TW`.

- **`backup-level`**
  - `minimal`: backup only items (main inventory, armor, offhand, ender chest).
  - `full`: additionally backup player status (health, hunger, exp, gamemode, effects, location, etc).

- **`auto-backup`**
  - `on-quit`:
    - `true`: automatically create a snapshot when a player leaves.
  - `on-join`:
    - `true`: automatically create a snapshot when a player joins.
  - `interval`:
    - > 0: every N seconds, all online players are auto-backed-up;
    - `0`: disabled.

- **`max-snapshots`**
  - Maximum number of historical snapshots per player;
  - Oldest snapshots are removed when the count is exceeded;
  - `0` means unlimited (not recommended for large/long-running servers).

- **`restore-request`**
  - `expire-days`:
    - number of days after which `pending` requests expire;
    - expired requests are marked as `expired`.
  - `open-window-seconds`:
    - after the first successful `/ib accept`, how long the player may re-open `RestoreGui`;
    - `0`: only the first open is allowed; subsequent attempts are considered “used”.
  - `restore-all-overflow`:
    - controls what happens when “Restore all” does not fit into the inventory:
      - `drop`: overflow items are dropped at the player’s feet and considered restored;
      - `keep`: overflow items are not restored and remain available in the snapshot.
  - `match-by-name`:
    - when enabled, `RestoreRequestManager` tries to migrate `pending` requests to the new UUID if the stored target name matches the joining player’s name (useful when switching online/offline mode or after name changes).
  - `manual-name-input.enabled`:
    - when `true`, enables cross-player restore entry points in `PreviewGui` and `RestoreGui` so that admins can queue snapshots from A to B by entering a name.

---

## Commands & Permissions

### Permissions (from `plugin.yml`)

```yaml
permissions:
  invbackup.save:
    description: Backup own inventory
    default: false
  invbackup.view:
    description: View and preview own backups
    default: false
  invbackup.admin:
    description: Full admin access (restore, delete, saveall, import, export, migrate, gui, etc.)
    default: op
    children:
      invbackup.save: true
      invbackup.view: true
```

- `invbackup.save`: allows `/ib save`.
- `invbackup.view`: allows `/ib list` and `/ib preview`.
- `invbackup.admin`: full admin access to all commands and GUIs.

### Player commands

- `/ib save [label]`
  - Permission: `invbackup.save`
  - Description: create a snapshot for yourself, with an optional label.

- `/ib list`
  - Permission: `invbackup.view`
  - Description: list your snapshots.

- `/ib preview <snapshotId>`
  - Permission: `invbackup.view`
  - Description: preview a snapshot in `PreviewGui` (read-only).

- `/ib accept <requestId>`
  - Permission: none, for the player themselves
  - Description:
    - accepts a restore request and opens `RestoreGui`;
    - enforces the reopen window specified by `open-window-seconds`.

- `/ib decline <requestId>`
  - Permission: player themselves
  - Description: decline a restore request (status becomes `declined`).

### Admin commands

- `/ib gui`
  - Permission: `invbackup.admin`
  - Description: open the admin GUI (players → snapshots → preview).

- `/ib list <player> [--all]`
  - Permission: `invbackup.admin`
  - Description:
    - list backups for a given player;
    - without `--all`, only backups triggered by that player are shown;
    - with `--all`, all backups (any trigger type) are listed.

- `/ib preview <player> <snapshotId>`
  - Permission: `invbackup.admin`
  - Description: preview any player’s snapshot in a GUI.

- `/ib restore <player> [snapshotId]`
  - Permission: `invbackup.admin`
  - Description:
    - enqueue a restore request for the target player;
    - does not directly modify the player’s inventory;
    - if `snapshotId` is omitted, the latest snapshot is used.

- `/ib forcerestore <player> [snapshotId] [minimal|full]`
  - Permission: `invbackup.admin`
  - Description:
    - directly restores to an **online** player;
    - if `snapshotId` is omitted, uses the latest snapshot;
    - `minimal|full` controls whether to restore status;
    - this is the **only** entry point that bypasses the restore-request queue.

- `/ib delete <player> <snapshotId>`
  - Permission: `invbackup.admin`
  - Description: delete a snapshot and its associated restore tracking.

- `/ib saveall [label]`
  - Permission: `invbackup.admin`
  - Description: create snapshots for all online players.

- `/ib import file:<name>.yml | folder:<name>`
  - Permission: `invbackup.admin`
  - Description:
    - open the import confirmation GUI for sources in `data/imports`;
    - import is only performed after explicit confirmation in the GUI.

- `/ib export <folder> [player]`
  - Permission: `invbackup.admin`
  - Description:
    - without `player`: export all players’ current snapshots to `data/imports/<folder>/`;
    - with `player`: export only that player’s current snapshot.

- `/ib exportjson file:<uuid>.yml | folder:<name>`
  - Permission: `invbackup.admin`
  - Description:
    - convert YAML backups under `json-tool/import` into JSON under `json-tool/export`.

- `/ib migrate <oldUUID> <newUUID>`
  - Permission: `invbackup.admin`
  - Description: migrate history/current/pending data from `oldUUID` to `newUUID`.

- `/ib search <playerName>`
  - Permission: `invbackup.admin`
  - Description: search history and current data for a given player name.

- `/ib reload`
  - Permission: `invbackup.admin`
  - Description: reload config and language files, and refresh the identity cache.

- `/ib revoke <requestId>`
  - Permission: `invbackup.admin` and must be the original requester
  - Description: revoke a `pending` restore request; if the target is online, they receive a notification.

---

## Data layout

Under `plugins/InvBackup/`:

- `config.yml`: main configuration file.
- `lang/`: language files.
  - `zh_CN.yml`, `en_US.yml`, `zh_TW.yml`, and any custom language files.
- `data/`
  - `history/`:
    - one `<uuid>.yml` per player;
    - holds `snapshots` and `restored` sections.
  - `current/`:
    - one `<uuid>.yml` per player;
    - holds a “current” snapshot used mostly for export.
  - `imports/`:
    - import sources for `/ib import` and some export operations.
  - `pending/`:
    - restore request queues; one `<uuid>.yml` file per target player.
- `json-tool/`
  - `import/`: input for `/ib exportjson`;
  - `export/`: output JSON directory.

---

## License

This project is released under the license described in the repository’s `LICENSE` file.  
Please review the terms before using, redistributing, or integrating this plugin in commercial environments.
