## InvBackup

InvBackup 是一个面向 Paper / Spigot 的**玩家物品与状态备份与恢复插件**，提供两档发布版本以覆盖 1.18.x - 1.21.x+。

### 主要特性

- **多种备份方式**：手动 `/ib save`、管理员 `/ib saveall`、玩家上下线自动备份、定时备份。
- **安全恢复流程**：绝大部分恢复都走“恢复请求 + Restore GUI”，玩家自己从 GUI 中取回，防止误操作和盗刷。
- **精细恢复**：支持单格物品、整套装备、副手、末影箱、血量/经验/位置/药水效果/游戏模式等按需恢复。
- **管理员工具**：
  - `AdminGui`：图形化浏览所有玩家及其快照；
  - `PreviewGui`：只读预览、发起恢复请求、可选跨玩家排队；
  - `BulkRestoreGui`：按筛选条件批量向玩家发送恢复请求。
- **数据导入导出**：
  - `/ib import` + GUI 确认，支持从 `data/imports` 导入 InvBackup/部分 CreativeManager 数据；
  - `/ib export` 导出当前快照；
  - `/ib exportjson` 将 Yaml 转为 Web/工具使用的 JSON。

### 基本用法

- 玩家：
  - `/ib save [标签]`：为自己创建快照。
  - `/ib list`：列出自己的备份列表。
  - `/ib preview <id>`：GUI 中预览某次备份。
  - `/ib accept <请求ID>` / `/ib decline <请求ID>`：接受或拒绝恢复请求。
- 管理员：
  - `/ib gui`：打开管理 GUI，从“玩家 → 备份 → 预览”管理所有备份。
  - `/ib restore <玩家> [id]`：向玩家发送恢复请求（不直接写背包）。
  - `/ib forcerestore <玩家> [id] [minimal|full]`：唯一直接覆盖背包/状态的恢复命令。
  - `/ib import ...`、`/ib export ...`、`/ib exportjson ...`：导入/导出数据。

### 运行环境

- **Java 17+**（1.18.x - 1.21.x 版本）
- **支持版本**：
  - Paper/Spigot 1.18.x
  - Paper/Spigot 1.19.x
  - Paper/Spigot 1.20.x
  - Paper/Spigot 1.21.x（推荐）
- **构建目标**：
  - `paper-1.18-1.20`（适用于 Paper/Spigot 1.18.x - 1.20.x，`api-version: 1.18`）
  - `paper-1.21-plus`（适用于 Paper/Spigot 1.21.x+，`api-version: 1.21`）

### 构建与下载

项目支持双目标构建，可以使用以下命令构建：

```bash
# 构建 paper-1.18-1.20
./gradlew clean build -PbuildTarget=paper-1.18-1.20

# 构建 paper-1.21-plus
./gradlew clean build -PbuildTarget=paper-1.21-plus

# 构建所有版本（Windows）
build-all.bat

# 构建所有版本（Linux/macOS）
./build-all.sh

# 构建所有版本（PowerShell）
powershell -ExecutionPolicy Bypass -File .\build-versions.ps1
```

构建完成后，可以在 `dist/` 目录找到两个产物：
- `InvBackup-paper-1.18-1.20.jar`
- `InvBackup-paper-1.21-plus.jar`

更完整的功能说明、配置与命令细节，请参见仓库中的 `README.full.md`（中文完整版）或 `README.en.full.md`（English Full）。
