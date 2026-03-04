## 项目概览

InvBackup 是一个面向 Paper / Spigot 服务器的备份与恢复插件，目标是：

- 为玩家提供可靠的**背包与状态历史快照**；
- 让管理员可以方便地**查看、搜索、导出、导入**备份；
- 通过**恢复请求队列 + GUI** 实现安全、可审计、可防盗刷的恢复流程；
- 支持**跨玩家恢复**与**批量恢复请求**。

本文档详细介绍 InvBackup 的：

- 功能与设计；
- 配置项说明；
- 命令与权限；
- GUI 结构与交互流程；
- 恢复请求与防刷机制。

---

## 功能与设计

### 备份功能

- **手动备份**
  - 玩家可以使用 `/ib save [标签]` 为自己创建一份备份。
  - 管理员可以使用 `/ib saveall [标签]` 为所有在线玩家创建备份。

- **自动备份**
  - 支持在玩家加入、离开或按时间间隔自动备份：
    - `auto-backup.on-join`
    - `auto-backup.on-quit`
    - `auto-backup.interval`（秒）

- **备份内容**
  - 由 `backup-level` 控制：
    - `minimal`：只记录物品（背包、护甲、副手、末影箱）。
    - `full`：在 `minimal` 基础上额外保存：
      - 血量、最大血量；
      - 饥饿值、饱和度；
      - 经验值、等级；
      - 游戏模式、是否可飞；
      - 药水效果；
      - 位置（世界、坐标、朝向）。

- **存储结构**
  - `data/history/<uuid>.yml`：所有历史快照；
  - `data/current/<uuid>.yml`：玩家最新一次快照的副本；
  - `data/pending/<uuid>.yml`：恢复请求队列；
  - `data/imports/`：导入源数据。

---

## 配置说明（config.yml）

当前默认配置示例（关键段落）：

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
```

### 语言与备份级别

- **`language`**
  - 语言文件位于 `lang/` 目录，例如 `zh_CN.yml`、`en_US.yml`。

- **`backup-level`**
  - `minimal`：只备份物品；
  - `full`：额外备份状态（推荐）。

### 自动备份

- **`auto-backup.on-quit`**
  - `true`：玩家离开服务器时自动备份。
- **`auto-backup.on-join`**
  - `true`：玩家加入服务器时自动备份。
- **`auto-backup.interval`**
  - 大于 0 时，每隔 N 秒对所有在线玩家执行一次自动备份。

### 快照上限

- **`max-snapshots`**
  - 每个玩家最多保留的历史快照数；
  - `0` 表示无限制（不建议长期使用在大服上）。

### 恢复请求（restore-request）

- **`expire-days`**
  - 恢复请求的**挂起过期天数**；
  - 超过天数后仍未接受的请求会被标记为 `expired`。

- **`open-window-seconds`**
  - 玩家点击 `[Accept]` 进入 `RestoreGui` 后的**重复打开窗口**：
    - `0`：只允许**第一次成功打开 RestoreGui**，之后再次 `/ib accept` 该请求会提示“已使用/已过期”；
    - `>0`：在指定秒数内，玩家可以多次 `/ib accept` 重新打开 RestoreGui（例如误关界面）。

- **`restore-all-overflow`**
  - 控制一键恢复（RestoreGui 下方的 “恢复全部” 按钮）在物品过多时的行为：
    - `drop`：如果玩家背包放不下，**多余物品会掉落在脚下**，并视为已恢复（被记录在 RestoredTracker 中）；
    - `keep`：放不下的物品**不会恢复**，仍保留在快照中，玩家以后可再次打开 RestoreGui 单格领取。

- **`match-by-name`**
  - `true` 时，插件在以下场景按名字尝试匹配玩家：
    - 服务器切换在线/离线模式导致 UUID 改变；
    - 恢复请求挂起时的 `target-name` 与当前玩家名匹配；
  - 匹配成功时，会将旧 UUID 下的请求迁移到新 UUID 对应的 pending 文件。

---

## 命令与权限

### 基础命令（玩家）

- **`/ib save [label]`**
  - **描述**：为自己创建一份快照，`label` 为可选标签。
  - **权限**：`invbackup.save`

- **`/ib list`**
  - **描述**：列出自己的备份列表。
  - **权限**：`invbackup.view`

- **`/ib preview <snapshotId>`**
  - **描述**：在 GUI 中预览指定快照（只读）。
  - **权限**：`invbackup.view`

### 管理员命令

- **`/ib gui`**
  - **描述**：打开 Admin 管理 GUI，从玩家列表开始浏览、预览与管理所有备份。
  - **权限**：`invbackup.admin`

- **`/ib list <player> [--all]`**
  - **描述**：查看指定玩家的备份列表。
  - **权限**：`invbackup.admin`

- **`/ib preview <player> <snapshotId>`**
  - **描述**：预览任意玩家的指定备份。
  - **权限**：`invbackup.admin`

- **`/ib saveall [label]`**
  - **描述**：为所有在线玩家创建备份。
  - **权限**：`invbackup.admin`

- **`/ib restore <player> [snapshotId]`**
  - **描述**：向目标玩家发送恢复请求：
    - 如果未指定 `snapshotId`，取该玩家最新快照；
    - 只创建请求文件与 chat 提示，不直接恢复物品。
  - **权限**：`invbackup.admin`

- **`/ib forcerestore <player> [snapshotId] [minimal|full]`**
  - **描述**：对在线玩家执行**强制恢复**（唯一直接写物品的入口）：
    - 在线玩家必需；
    - 若未指定 `snapshotId`，取该玩家最新快照；
    - `minimal|full` 控制是否恢复状态。
  - **权限**：`invbackup.admin`

- **`/ib delete <player> <snapshotId>`**
  - **描述**：删除指定玩家的一份备份。
  - **权限**：`invbackup.admin`

- **`/ib import file:<name>.yml | folder:<name>`**
  - **描述**：打开导入确认 GUI，从导入目录中选择 YAML 文件或文件夹导入备份。
  - **权限**：`invbackup.admin`

- **`/ib export <folder> [player]`**
  - **描述**：将备份导出到 `data/history-export/<folder>/`（或类似路径）：
    - 不带玩家参数：导出所有玩家；
    - 带玩家参数：只导出该玩家。
  - **权限**：`invbackup.admin`

- **`/ib exportjson file:<uuid>.yml | folder:<name>`**
  - **描述**：将 Yaml 备份转换为 Web / 工具使用的 JSON 格式，输出到 `json-tool/export/`。
  - **权限**：`invbackup.admin`

- **`/ib migrate <oldUUID> <newUUID>`**
  - **描述**：在数据层面迁移一个玩家的 UUID（包括历史与 pending 等）。
  - **权限**：`invbackup.admin`

- **`/ib search <playerName>`**
  - **描述**：按玩家名字搜寻历史记录中的 UUID 及存档目录信息。
  - **权限**：`invbackup.admin`

- **`/ib reload`**
  - **描述**：重载配置与语言文件。
  - **权限**：`invbackup.admin`

### 恢复请求相关命令（玩家）

- **`/ib accept <requestId>`**
  - **描述**：接受一条恢复请求：
    - 如果是第一次接受：
      - 根据 `open-window-seconds` 写入 `accepted` 状态与 `open-expired-at`；
      - 打开 RestoreGui 让玩家取回物品。
    - 如果已接受：
      - 当 `open-window-seconds == 0`：返回“请求已使用”，不再打开 GUI；
      - 当 `open-window-seconds > 0` 且未过期：允许再次打开 RestoreGui；
      - 过期后会把请求标记为 `expired` 并提示。
  - **权限**：玩家自己，无需额外权限。

- **`/ib decline <requestId>`**
  - **描述**：拒绝一条恢复请求，状态变为 `declined`。
  - **权限**：玩家自己。

---

## GUI 结构

### AdminGui（管理员主界面）

- **玩家列表**
  - 显示所有拥有备份记录的玩家（从 `history` / `current` 目录扫描）。
  - 点击玩家头像：打开该玩家的备份列表。
  - 导航栏中包含：
    - 前一页 / 后一页；
    - 当前页信息；
    - **批量恢复按钮**（打开 BulkRestoreGui）。

- **备份列表**
  - 显示该玩家的所有备份信息：
    - 快照 ID；
    - 时间戳；
    - 标签；
    - 触发来源（玩家/服务器）、备份级别等。
  - 点击某条备份：打开 PreviewGui 进行预览。

### PreviewGui（备份预览）

- 显示：
  - 主背包、护甲、副手、末影箱、状态信息等；
  - “仅物品恢复”、“全量恢复”按钮在现版本中**不直接恢复物品**，而是：
    - 为目标玩家创建一条恢复请求；
    - 若在线则立即通知。

- 用于：
  - 在 AdminGui 里精确查看某个快照；
  - 决定是否给该玩家发起恢复请求。

### RestoreGui（玩家恢复界面）

- 由玩家通过 `/ib accept <requestId>` 或点击 `[Accept]` 打开。
- 功能：
  - 主背包 0–35 格：逐格点击，把物品取回玩家当前背包；
  - 护甲 4 格：点击自动装备到对应护甲槽，原装备会尝试移入背包；
  - 副手：点击用备份物品替换当前副手；
  - 末影箱：通过子 GUI 预览并可单独恢复格子（未来扩展）；
  - 状态按钮（血量/饱食度/经验/位置/效果/模式）：
    - 单独恢复对应状态；
  - “恢复全部”按钮：
    - 在恢复前自动为当前玩家再做一次安全备份；
    - 智能合并背包与状态，按 `restore-all-overflow` 处理溢出。

- 防刷逻辑：
  - 每个槽位、护甲位、副手位、末影箱格、状态键在被恢复后都会被 `RestoredTracker` 标记；
  - 之后同一玩家即使多次打开 RestoreGui（包括新的请求），也无法再次领取同一快照中已恢复的项；
  - 管理员在 GUI 中操作时不受限制，可用于特殊情况手动补发。

### ImportConfirmGui（导入确认）

- 在 `/ib import` 命令后打开：
  - 显示将要导入的条目（文件 / 文件夹内所有条目）；
  - 支持：
    - 逐条选择/取消；
    - 一键全选/清空；
    - Shift-点击预览某条导入快照（跳转 PreviewGui，再返回）。
  - 确认导入后，将所选条目写入 `history` 结构。

### BulkRestoreGui（批量恢复请求 GUI）

- 在 AdminGui 玩家列表页的导航栏点击“批量操作”按钮打开。
- 上半区：
  - 显示所有有备份的玩家头像，点击以选择/取消加入此次批量请求名单。
- 下半区配置：
  - **Latest**（是否在没有命中筛选条件时回退到“最新快照”）；
  - **Survival only**（初版使用 `backupLevel=full` 作为“完整存档”近似）；  
  - **Recent**（在 `0 / 24 / 72` 小时窗口间切换，用于只考虑最近 N 小时的备份）；
  - 发送按钮 / 取消按钮；
  - 信息行：显示已选择玩家数与总玩家数。
- 发送逻辑：
  - 对每个被选玩家调用 `listBackups`，按配置选出唯一快照（失败则跳过并计数）；
  - 为每个成功匹配的玩家创建恢复请求并（若在线）立即通知；
  - 最后向管理员发送统计信息（成功数/跳过数/总数）。

---

## 恢复请求与防刷实现细节

- **数据结构**：`RestoreRequest`（简化描述）
  - `requestId`：唯一请求 ID。
  - `sourceUuid/sourceName`：备份所有者（A）。
  - `targetUuid/targetName`：恢复目标玩家（B）。
  - `snapshotId`：快照 ID。
  - `requestedBy` / `requestedByUuid`：请求发起者（管理员/控制台）。
  - `timestamp`：创建时间。
  - `status`：`pending` / `accepted` / `declined` / `expired`。
  - `openExpiredAt`：接受后允许重新打开 RestoreGui 的截止时间戳。

- **存储**：`data/pending/<targetUuid>.yml`
  - 每个 target 玩家对应一个 pending 文件；
  - 内部以 `requests.<requestId>.*` 形式存储请求。

- **接受逻辑（/ib accept）**：
  - 先对当前玩家调用 `cleanExpired(uuid)`：  
    - 依据 `expire-days` 与 `openExpiredAt` 将过期请求标记为 `expired`。
  - 找到对应 `requestId`：
    - `pending` → 标记为 `accepted` 并写入 `open-expired-at`；
    - `accepted` → 根据 `open-window-seconds` 判断是否允许重新打开或改为过期；
    - 其他（`declined` / `expired`）→ 提示请求无效。
  - 打开 RestoreGui 时使用：
    - 从 `sourceUuid` 中加载快照（支持 A → B 恢复场景）。

- **恢复记录（RestoredTracker）**
  - 存放在 `history/<uuid>.yml` 的 `restored.<snapshotId>` 下；
  - 记录内容包括：
    - 已恢复的主背包槽位集合；
    - 护甲布尔列表；
    - 副手标记；
    - 末影箱槽位集合；
    - 各状态键是否已恢复（health/food/exp/location/effects/gamemode）。
  - 每次从 RestoreGui 拿出物品或恢复状态时都会写入对应标记；
  - 之后再次打开 GUI 时，相应位置会被渲染为“已领取”，普通玩家不能再取（管理员可绕过）。

---

## 开发与扩展建议

- 如需扩展更多筛选维度（例如指定 label、trigger-type），可在：
  - `BackupManager.BackupInfo` 中添加字段；
  - `listBackups` 的填充逻辑中读取 `meta.*`；
  - `BulkRestoreGui.pickSnapshot` 中加入新的筛选条件。

- 如果需要增加新的 GUI 层级或按名字/UUID 跨服迁移：
  - 建议复用 `PlayerIdentityManager` 中的名字解析逻辑；
  - 所有直接写物品的逻辑应继续集中在 `RestoreGui` 与 `/ib forcerestore` 中，保持恢复路径的一致性与可控性。

---

如需查看整体实现，请从 `InvBackup.java` 入口类开始，向下阅读 `manager/`、`gui/` 与 `request/` 包内的代码。  
如果你希望我进一步补充英文文档或 API 说明，也可以再告诉我。 

