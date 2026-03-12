## InvBackup

InvBackup is a **player inventory and state backup & restore plugin** for Paper / Spigot servers (Paper 1.21.x recommended).

### Key features

- **Multiple backup modes**: manual `/ib save`, admin `/ib saveall`, auto on join/quit, and periodic backups.
- **Safe restore flow**: almost all restores go through a **restore request + Restore GUI**, so players claim items themselves and abuse is harder.
- **Fine-grained restore**: restore single inventory slots, armor, offhand, ender chest, and player state (health, hunger, exp, location, effects, gamemode).
- **Admin tooling**:
  - `AdminGui`: browse all players with backups and their snapshots;
  - `PreviewGui`: read-only snapshot preview and request creation (optionally cross-player);
  - `BulkRestoreGui`: send restore requests to many players at once with filters.
- **Import / export / web tooling**:
  - `/ib import` with confirmation GUI from `data/imports`;
  - `/ib export` to export current snapshots;
  - `/ib exportjson` to convert YAML backups to JSON for web or external tools;
  - embedded read-only web UI (`web.enabled=true`) with filtering and command generation.

### Basic usage

- Players:
  - `/ib save [label]`: create a snapshot for yourself.
  - `/ib list`: list your snapshots.
  - `/ib preview <id>`: preview a snapshot in a GUI.
  - `/ib accept <requestId>` / `/ib decline <requestId>`: accept or decline restore requests.
- Admins:
  - `/ib gui`: open the admin GUI (players → snapshots → preview).
  - `/ib restore <player> [id]`: enqueue a restore request (does not directly modify the inventory).
  - `/ib forcerestore <player> [id] [minimal|full]`: the only command that restores directly.
  - `/ib web`: show embedded web UI address and auth token hint.
  - `/ib import ...`, `/ib export ...`, `/ib exportjson ...`: import/export data and JSON.

### Requirements

- Java 17+ (for `paper-1.18-1.20` build) / Java 21+ (for `paper-1.21-plus` build)
- Paper/Spigot 1.18.x - 1.21.x

### Build profiles

- `paper-1.18-1.20`: one jar for Paper/Spigot 1.18.x - 1.20.x (`api-version: 1.18`)
- `paper-1.21-plus`: one jar for Paper/Spigot 1.21.x+ (`api-version: 1.21`)

```bash
# Build Paper 1.18 - 1.20 profile
./gradlew clean build -PbuildTarget=paper-1.18-1.20

# Build 1.21+ profile
./gradlew clean build -PbuildTarget=paper-1.21-plus

# Build both jars into dist/
build-all.bat        # Windows
./build-all.sh       # Linux/macOS
powershell -ExecutionPolicy Bypass -File .\build-versions.ps1
```

For detailed explanation of features, configuration and commands, see `README.en.full.md` (English Full) or `README.full.md` (Chinese Full).
