# InvBackup

InvBackup 是一个用于 Paper / Spigot 服务器的**玩家物品与状态备份与恢复**插件，支持：

- **自动与手动备份**：玩家上线/下线/定时/命令触发备份。
- **恢复请求队列**：除 `/ib forcerestore` 外，所有恢复都通过“玩家自己接受的请求 + GUI 恢复”，防止误操作与盗刷。
- **细粒度 GUI 恢复**：支持单格物品、整套装备、副手、末影箱、血量/经验/位置等按需恢复。
- **跨玩家恢复与导入**：管理员可把 A 的存档以请求方式排队给 B，也可通过导入 GUI 恢复外部存档。
- **批量工具**：支持在 Admin GUI 中批量向玩家发送恢复请求。

详细文档请见仓库中的 `detail.md`。

---

## 特性一览

- **安全的恢复流程**
  - 所有普通恢复行为（命令 `/ib restore`、Admin GUI 预览里的恢复按钮、RestoreGui 中的按名字恢复等）统一走“可排队恢复请求”。
  - 玩家通过聊天中的 `[Accept]/[Decline]` 按钮决定是否打开 `RestoreGui`，由玩家自己从 GUI 中取回物品。
  - 每份快照都有恢复记录（RestoredTracker），同一玩家对同一快照不会重复领取已恢复的物品/状态。

- **强制恢复命令**
  - `/ib forcerestore` 仍然保留为**唯一的“直接恢复”入口**，要求目标玩家在线，立刻写入背包与状态，适合紧急场景。

- **现代 GUI 体验**
  - `AdminGui`：浏览所有有备份的玩家 → 查看其备份列表 → 预览 → 发送恢复请求或打开 Restore GUI。
  - `PreviewGui`：查看指定快照的物品、护甲、副手、末影箱与状态，并支持一键发送恢复请求。
  - `RestoreGui`：玩家端恢复界面，支持：
    - 单格物品取回；
    - 护甲 / 副手 / 末影箱 / 状态单独恢复；
    - 一键恢复（智能合并背包，溢出按配置丢在地上或保留在 GUI）。
  - `ImportConfirmGui`：导入文件/文件夹中的备份条目，并配合 PreviewGui 做导入前预览与确认。
  - `BulkRestoreGui`：在 Admin GUI 玩家列表中一键进入，按条件批量向玩家发送恢复请求。

- **灵活的配置**
  - 备份级别（仅物品 / 物品 + 状态）。
  - 自动备份触发条件与间隔。
  - 恢复请求：
    - 请求过期天数；
    - `match-by-name` 跨 UUID / 名字匹配策略；
    - `open-window-seconds` 控制玩家接受请求后在多久内可以重复打开 RestoreGui；
    - `restore-all-overflow` 控制一键恢复时多余物品是掉落在地上还是保留在 GUI。

---

## 安装与支持版本

- **前置条件**
  - Java 17+（建议跟随 Paper 官方版本要求）。
  - 服务器核心：Paper / Spigot 1.18+（代码基于 Paper API，推荐使用 Paper）。

- **安装步骤**
  1. 从 GitHub Release 下载对应版本的 `InvBackup-x.y.z.jar`。
  2. 放入 `plugins/` 目录。
  3. 启动服务器，生成默认配置与语言文件。
  4. 按需编辑 `config.yml` 与 `lang/` 下的语言文件。

---

## 快速上手

### 基本命令（玩家）

- `/ib save [标签]`
  - 为自己创建一份快照。
- `/ib list`
  - 查看自己的备份列表。
- `/ib preview <id>`
  - 预览指定备份（仅查看，不会改变背包）。

### 管理员命令

- `/ib gui`
  - 打开 Admin 管理 GUI：从玩家列表开始浏览和管理所有备份。
- `/ib restore <玩家> [id]`
  - 向该玩家发送恢复请求（不会立即恢复，玩家需在线/上线后自己接受）。
- `/ib forcerestore <玩家> [id] [minimal|full]`
  - 立刻对在线玩家执行强制恢复（唯一直接写背包的入口）。
- `/ib saveall [标签]`
  - 为所有在线玩家保存一份备份。
- `/ib import file:<name>.yml | folder:<name>`
  - 通过导入确认 GUI 导入 YAML 备份。
- `/ib export <文件夹> [玩家]`
  - 将备份导出为 YAML 文件。
- `/ib search <玩家名>`
  - 根据名字在历史中搜索对应 UUID 与保存位置。
- `/ib reload`
  - 重载配置和语言。

完整命令、权限与行为细节，请参考 `detail.md`。

---

## 恢复请求工作流概览

1. 管理员通过命令或 GUI 选择某个玩家和某份备份。  
2. 插件根据 UUID / 名字匹配策略（`match-by-name` 等）为“目标玩家”生成 `pending/<uuid>.yml` 请求。  
3. 如果目标玩家在线，立即发送聊天消息，包含 `[Accept]` / `[Decline]` 按钮；否则只写入 pending，等其上线时再通知。  
4. 玩家点击 `[Accept]`：
   - `/ib accept <requestId>` 会根据 `open-window-seconds`：
     - `0`：只允许成功打开 RestoreGui 一次；
     - `>0`：在 N 秒内可以多次重新打开 RestoreGui。  
5. 在 RestoreGui 中，玩家按需取回物品/状态，一切恢复都会写入恢复记录，防止重复领取。  

只有 `/ib forcerestore` 会跳过这套流程直接写物品。

---

## 构建与贡献

- **本地构建**
  - 在项目根目录执行：
    ```bash
    ./gradlew build
    ```
  - 构建好的插件位于 `build/libs/`。

- **贡献与反馈**
  - 欢迎通过 Issue 提交 Bug 或功能建议，也欢迎 PR。
  - 提交 PR 前请确保：
    - 通过 `./gradlew build`；
    - 遵守现有代码风格（Checkstyle / SpotBugs）。

---

## 许可证

本项目基于 `LICENSE` 中声明的协议开源发布。请在二次分发与商用前仔细阅读许可条款。

