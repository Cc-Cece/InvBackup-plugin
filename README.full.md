## InvBackup（中文完整版）

InvBackup 是一个用于 Paper / Spigot 服务器的**玩家物品与状态备份与恢复**插件，围绕“安全、可审计、可防刷”的恢复流程设计。

它通过**恢复请求队列 + Restore GUI** 避免了常见的误操作与盗刷风险，同时保留 `/ib forcerestore` 这种在极端场景下可直接写背包的能力。

---

## 功能总览

- **备份功能**
  - 手动备份：
    - 玩家：`/ib save [标签]` 为自己创建一份快照。
    - 管理员：`/ib saveall [标签]` 为所有在线玩家创建快照。
  - 自动备份（可在 `config.yml` 中配置）：
    - 玩家离线自动备份：`auto-backup.on-quit`
    - 玩家加入自动备份：`auto-backup.on-join`
    - 定时全服自动备份：`auto-backup.interval`（秒）
  - 备份内容级别：
    - `backup-level: minimal`：
      - 只记录主背包、护甲、副手、末影箱。
    - `backup-level: full`：
      - 在 `minimal` 基础上额外记录：
        - 血量、最大血量；
        - 饥饿值、饱和度；
        - 经验值、等级、总经验；
        - 游戏模式、是否可飞；
        - 药水效果；
        - 位置（世界、坐标、朝向）。

- **恢复请求队列**
  - 管理员通过命令或 GUI 发起恢复时，默认不会直接写玩家背包，而是：
    1. 为目标玩家在 `data/pending/<targetUuid>.yml` 中创建一条 `RestoreRequest`；
    2. 若玩家在线，立刻发送带 `[接受] / [拒绝]` 按钮的消息；
    3. 玩家通过 `/ib accept <requestId>` 或点击 `[接受]` 打开 `RestoreGui`，自行取回物品/状态。
  - 每条请求包含：
    - `sourceUuid/sourceName`：快照拥有者（可以与目标玩家不同，用于跨玩家恢复）；
    - `targetUuid/targetName`：恢复目标玩家；
    - `snapshotId`：快照 ID；
    - `requestedBy/requestedByUuid`：请求发起者（管理员/控制台）；
    - `status`：`pending/accepted/declined/expired/revoked`；
    - `openExpiredAt`：接受后允许重复打开 `RestoreGui` 的截止时间戳。

- **恢复记录与防刷机制**
  - `RestoredTracker` 把“已经恢复过的内容”记录在 `data/history/<uuid>.yml` 对应快照下的 `restored.<snapshotId>` 中：
    - 已领取的主背包格子集合；
    - 已恢复的护甲位布尔数组；
    - 是否已恢复副手；
    - 已恢复的末影箱格子集合；
    - 已恢复的状态键（health/food/exp/location/effects/gamemode）。
  - 普通玩家在 `RestoreGui` 中：
    - 已被标记恢复的格子/部位/状态会显示为“已恢复”，不能再次领取；
    - 即使多次打开 GUI 或收到多条指向同一快照的请求，也不会重复获得相同内容。
  - 管理员在 GUI 中不受此限制，可用于特殊场景手动补发。

- **图形界面（GUI）**
  - `AdminGui`（管理员主界面）
    - 入口：`/ib gui`。
    - 玩家列表：
      - 列出所有存在备份的玩家（从 `data/history`、`data/current` 扫描 UUID）。
      - 点击玩家头像进入该玩家的备份列表。
      - 导航行中包含上一页/下一页信息以及“批量恢复请求”按钮。
    - 备份列表：
      - 展示快照 ID、时间、标签、触发者、触发类型、备份级别等。
      - 点击某条备份进入 `PreviewGui` 进行预览。
  - `PreviewGui`（备份预览 GUI）
    - 入口：
      - 玩家：`/ib preview <id>`；
      - 管理员：`/ib preview <player> <id>`；
      - 或从 `AdminGui` 点击进入。
    - 展示内容：
      - 主背包 0–35 格；
      - 护甲与副手；
      - 末影箱预览；
      - 玩家状态（血量/饥饿/经验/模式/位置/效果等）。
    - 管理员可在此：
      - 发起“仅物品恢复”或“完整恢复”请求（内部仍走请求队列，由玩家在 `RestoreGui` 中实际领取）；
      - 当 `restore-request.manual-name-input.enabled` 为 `true` 时：
        - 通过“按昵称排队给玩家”入口，为任意玩家排队当前快照。
  - `RestoreGui`（玩家恢复 GUI）
    - 入口：玩家执行 `/ib accept <requestId>` 或点击聊天按钮 `[接受]`。
    - 功能：
      - 主背包格子逐格取回；
      - 护甲四件一件件装备（原装备尝试放入背包）；
      - 副手物品恢复；
      - 一键恢复全部（物品 + 状态）：
        - 恢复前自动再为玩家做一次安全备份；
        - 按 `restore-request.restore-all-overflow` 处理背包空间不足时的行为：
          - `drop`：多余物品掉落在玩家脚下并视为已恢复；
          - `keep`：多余物品不会恢复，仍保留在快照中。
      - 独立状态按钮：
        - 生命、饥饿、经验、位置、效果、游戏模式可以单独恢复。
      - 末影箱：
        - 当前版本中，通过预览 GUI 直接恢复整箱内容，并在追踪器中标记所有格子。
    - 当从恢复请求打开时：
      - GUI 中会展示请求来源与“可重开窗口”的提示；
      - 每次点击都会检查该请求是否被管理员撤回。

- **批量恢复请求 GUI（BulkRestoreGui）**
  - 从 `AdminGui` 玩家列表底部“批量恢复请求”按钮进入。
  - 功能：
    - 多选玩家（全服或某页）；
    - 筛选策略：
      - `Latest`：是否在没有命中筛选条件时回退到“最新快照”；
      - `Survival only`：是否只使用 `backup-level=full` 的备份；
      - `Recent`：时间范围（不限 / 近 24h / 近 72h）。
    - 发送时：
      - 对每个选中的玩家执行一次快照筛选；
      - 对命中的玩家创建恢复请求，在线玩家即时收到通知；
      - 最后向管理员发送成功/跳过/总数的统计消息。

- **导入 / 导出 / Web 工具**
  - 导入（带确认 GUI）：
    - `/ib import file:<name>.yml`：
      - 从 `plugins/InvBackup/data/imports/<name>.yml` 解析导入条目；
    - `/ib import folder:<name>`：
      - 从 `plugins/InvBackup/data/imports/<name>/` 下所有 `<uuid>.yml` 收集导入条目。
    - GUI（`ImportConfirmGui`）中可以：
      - 逐条选择/取消选择；
      - 全选/清空；
      - Shift+左键预览单条备份（跳转 `PreviewGui` 再返回）；
      - 确认导入选中条目或全部条目到历史备份。
  - 导出：
    - `/ib export <folder>`：
      - 将所有玩家的“当前快照”导出到 `data/imports/<folder>/`；
    - `/ib export <folder> <player>`：
      - 只导出指定玩家的当前快照。
  - JSON / Web 工具：
    - `/ib exportjson file:<uuid>.yml`：
      - 从 `json-tool/import/<uuid>.yml` 读取，导出 JSON 至 `json-tool/export/<uuid>.json`；
    - `/ib exportjson folder:<name>`：
      - 批量把 `json-tool/import/<name>/` 下的 `.yml` 转为 `json-tool/export/<name>/` 下的 `.json`。
    - 内部会自动识别 InvBackup/CreativeManager 两种格式，并输出统一 JSON。

- **UUID 迁移与搜索**
  - `/ib migrate <oldUUID> <newUUID>`：
    - 将 `data/history`、`data/current`、`data/pending` 中旧 UUID 的数据整体迁移到新 UUID；
    - 更新各快照 `meta.target-uuid` 字段。
  - `/ib search <playerName>`：
    - 在历史与当前数据中根据名字查找玩家；
    - 返回匹配的 UUID 以及其所在目录（history/current）。

---

## 安装与环境要求

- **前置条件**
  - Java 17+（`paper-1.18-1.20`）/ Java 21+（`paper-1.21-plus`）
  - Paper / Spigot：
    - `paper-1.18-1.20`：`plugin.yml` 中 `api-version: "1.18"`，基于 `paper-api:1.18.2-R0.1-SNAPSHOT` 构建；
    - `paper-1.21-plus`：`plugin.yml` 中 `api-version: "1.21"`，基于 `paper-api:1.21.4-R0.1-SNAPSHOT` 构建。

- **构建档位**
  - 构建 `paper-1.18-1.20`：
    - `./gradlew clean build -PbuildTarget=paper-1.18-1.20`
  - 构建 `paper-1.21-plus`：
    - `./gradlew clean build -PbuildTarget=paper-1.21-plus`
  - 一次构建两个版本：
    - `build-all.bat`（Windows）
    - `./build-all.sh`（Linux/macOS）
    - `powershell -ExecutionPolicy Bypass -File .\build-versions.ps1`
  - 产物目录：
    - `dist/InvBackup-paper-1.18-1.20.jar`
    - `dist/InvBackup-paper-1.21-plus.jar`

- **安装步骤**
  1. 将对应档位构建出的 jar 放入 `plugins/` 目录。
  2. 启动服务器生成默认配置与语言文件：
     - `plugins/InvBackup/config.yml`
     - `plugins/InvBackup/lang/*.yml`
  3. 根据需要修改 `config.yml` 与语言文件。
  4. 使用 `/ib reload` 重载配置与语言，或重启服务器。

---

## 配置（config.yml）

默认配置结构（节选）：

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
  - 当前语言文件名（不含 `.yml`），如 `zh_CN`、`en_US`、`zh_TW`。
  - 插件启动时会自动释放内置语言文件到 `lang/` 目录。

- **`backup-level`**
  - `minimal`：只备份物品（背包、护甲、副手、末影箱）。
  - `full`：在 `minimal` 基础上额外备份血量、饥饿、经验、模式、效果、位置等状态（推荐）。

- **`auto-backup`**
  - `on-quit`：
    - 为 `true` 时，玩家离开服务器时自动备份；
  - `on-join`：
    - 为 `true` 时，玩家加入服务器时自动备份；
  - `interval`：
    - 大于 0 时，每隔 N 秒为所有在线玩家执行一次自动备份；
    - `0` 表示关闭该功能。

- **`max-snapshots`**
  - 每个玩家在历史备份中最多保留的快照数；
  - 超出时按时间顺序自动清理最旧的快照；
  - `0` 表示无限制（不建议用于长期大型服务器）。

- **`restore-request`**
  - `expire-days`：
    - `pending` 请求的过期天数；
    - 超时未接受的请求会被自动标记为 `expired`。
  - `open-window-seconds`：
    - 玩家第一次成功接受请求并打开 `RestoreGui` 之后，允许在 N 秒内多次重开；
    - `0` 表示只允许第一次成功打开，之后会提示“请求已使用”。
  - `restore-all-overflow`：
    - 控制“一键恢复全部”时背包放不下的物品：
      - `drop`：丢在玩家脚下，同时视为已恢复；
      - `keep`：不恢复，多余物品仍保留在快照中。
  - `match-by-name`：
    - 在玩家上线时，通过名字匹配迁移过去保存的 pending 请求；
    - 用于在线/离线模式切换或 UUID 变化场景。
  - `manual-name-input.enabled`：
    - 为 `true` 时开启跨玩家恢复入口：
      - 在 `PreviewGui` 与 `RestoreGui` 中通过输入玩家名，把 A 的快照排队给 B。

---

## 命令与权限

### 权限节点

来自 `plugin.yml`：

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

- `invbackup.save`：玩家允许使用 `/ib save`。
- `invbackup.view`：玩家允许使用 `/ib list`、`/ib preview`。
- `invbackup.admin`：管理员完全权限（含 save/view 子权限）。

### 玩家常用命令

- `/ib save [label]`
  - 权限：`invbackup.save`
  - 描述：为自己创建一份快照，`label` 为可选标签。

- `/ib list`
  - 权限：`invbackup.view`
  - 描述：列出自己的备份列表。

- `/ib preview <snapshotId>`
  - 权限：`invbackup.view`
  - 描述：在 GUI 中预览指定快照（只读）。

- `/ib accept <requestId>`
  - 权限：玩家自己（无需额外权限）
  - 描述：
    - 接受一条恢复请求；
    - 首次成功打开 `RestoreGui` 时记录可重开窗口；
    - 在窗口内可多次重复打开（若 `open-window-seconds > 0`）。

- `/ib decline <requestId>`
  - 权限：玩家自己
  - 描述：拒绝当前请求，状态变为 `declined`。

### 管理员命令

- `/ib gui`
  - 权限：`invbackup.admin`
  - 描述：打开 Admin GUI，从“玩家 → 备份 → 预览”图形化管理所有备份。

- `/ib list <player> [--all]`
  - 权限：`invbackup.admin`
  - 描述：
    - 查看指定玩家的备份列表；
    - 默认仅显示该玩家自己触发的备份，带 `--all` 显示全部来源。

- `/ib preview <player> <snapshotId>`
  - 权限：`invbackup.admin`
  - 描述：在 GUI 中预览任意玩家的指定快照。

- `/ib restore <player> [snapshotId]`
  - 权限：`invbackup.admin`
  - 描述：
    - 给目标玩家排队创建一条恢复请求；
    - 不直接写背包；
    - 若未指定 `snapshotId`，默认使用该玩家最新快照。

- `/ib forcerestore <player> [snapshotId] [minimal|full]`
  - 权限：`invbackup.admin`
  - 描述：
    - 直接对在线玩家执行强制恢复；
    - 若未指定 `snapshotId`，默认使用该玩家最新快照；
    - `minimal|full` 控制是否恢复状态；
    - **唯一不经过恢复请求队列的直接恢复入口**。

- `/ib delete <player> <snapshotId>`
  - 权限：`invbackup.admin`
  - 描述：删除指定玩家的某条备份，并清理对应恢复记录。

- `/ib saveall [label]`
  - 权限：`invbackup.admin`
  - 描述：为所有在线玩家创建备份。

- `/ib import file:<name>.yml | folder:<name>`
  - 权限：`invbackup.admin`
  - 描述：
    - 打开导入确认 GUI，预览 `data/imports` 中的导入源；
    - 支持 InvBackup / CreativeManager 格式；
    - 仅在 GUI 中确认后才真正写入历史备份。

- `/ib export <folder> [player]`
  - 权限：`invbackup.admin`
  - 描述：
    - 无玩家参数：导出所有玩家当前快照到 `data/imports/<folder>/`；
    - 带玩家参数：只导出该玩家的当前快照。

- `/ib exportjson file:<uuid>.yml | folder:<name>`
  - 权限：`invbackup.admin`
  - 描述：
    - 把 `json-tool/import` 下的 Yaml 文件/目录转换为 `json-tool/export` 下的 JSON。

- `/ib migrate <oldUUID> <newUUID>`
  - 权限：`invbackup.admin`
  - 描述：迁移旧 UUID 的历史/当前备份及 pending 请求到新 UUID。

- `/ib search <playerName>`
  - 权限：`invbackup.admin`
  - 描述：按名字在历史/当前数据中搜索对应 UUID。

- `/ib reload`
  - 权限：`invbackup.admin`
  - 描述：重载配置与语言，并刷新玩家身份缓存。

- `/ib revoke <requestId>`
  - 权限：`invbackup.admin` 且必须为该请求的发起者
  - 描述：撤回一条 `pending` 状态的恢复请求，若目标玩家在线会收到相应提示。

---

## 数据目录结构

在 `plugins/InvBackup/` 下的主要目录和文件：

- `config.yml`：主配置文件。
- `lang/`：语言文件目录。
  - `zh_CN.yml`、`en_US.yml`、`zh_TW.yml` 等。
- `data/`
  - `history/`：
    - 每个玩家一个 `<uuid>.yml`；
    - 包含 `snapshots` 与 `restored` 两部分，用于存储历史快照和恢复记录。
  - `current/`：
    - 每个玩家一个 `<uuid>.yml`；
    - 用于保存最新快照的精简副本，便于导出。
  - `imports/`：
    - 导入源所在目录；
    - `folder:<name>` 对应 `imports/<name>/`。
  - `pending/`：
    - 恢复请求队列，每个玩家一个 `<uuid>.yml`。
- `json-tool/`
  - `import/`：用于 JSON 导出的输入 Yaml 文件；
  - `export/`：JSON 输出目录。

---

## 许可证

本项目基于仓库中的 `LICENSE` 文件所列协议发布。  
在使用、二次分发或商用前，请仔细阅读并遵守相关条款。
