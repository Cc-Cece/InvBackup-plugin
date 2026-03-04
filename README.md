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

# PaperMC/Spigot Minecraft Server Plugin Template
A template for building PaperMC/Spigot Minecraft server plugins!

<!-- TODO: CHANGE ME -->
[![Test and Release](https://github.com/CrimsonWarpedcraft/plugin-template/actions/workflows/main.yml/badge.svg)](https://github.com/CrimsonWarpedcraft/plugin-template/actions/workflows/main.yml)

<!-- TODO: CHANGE ME -->
[![](https://dcbadge.limes.pink/api/server/5XMmeV6EtJ)](https://discord.gg/5XMmeV6EtJ)

## Features
### Github Actions 🎬
* Automated builds, testing, and release drafting
* [Discord notifcations](https://github.com/marketplace/actions/discord-message-notify) for snapshots and releases

### Bots 🤖
* **Probot: Stale**
    * Mark issues stale after 30 days
* **Dependabot**
    * Update GitHub Actions workflows
    * Update Gradle dependencies

### Issue Templates 📋
* Bug report template
* Feature request template

### Gradle Builds 🏗
* Shadowed [PaperLib](https://github.com/PaperMC/PaperLib) build
* [Checkstyle](https://checkstyle.org/) Google standard style check
* [SpotBugs](https://spotbugs.github.io/) code analysis
* [JUnit](https://junit.org/) testing

### Config Files 📁
* Sample plugin.yml with autofill name, version, and main class.
* Empty config.yml (just to make life \*that\* much easier)
* Gradle build config
* Simple .gitignore for common Gradle files

## Usage
In order to use this template for yourself, there are a few things that you will need to keep in mind.

### Release Info
#### PaperMC Version Mapping
Here's a list of the PaperMC versions and the versions of this latest compatible version.

| PaperMC | ExamplePlugin |
|---------|---------------|
| 1.21.11 | 4.0.18+       |
| 1.21.10 | 4.0.17        |
| 1.21.8  | 4.0.16        |
| 1.21.7  | 4.0.15        |
| 1.21.6  | 4.0.14        |
| 1.21.5  | 4.0.12        |
| 1.21.4  | 4.0.7         |        
| 1.21.3  | 4.0.3         |
| 1.21.1  | 4.0.2         |
| 1.21    | 3.12.1        |
| 1.20.6  | 3.11.0        |
| 1.19.4  | 3.2.1         |
| 1.18.2  | 3.0.2         |
| 1.17.1  | 2.2.0         |
| 1.16.5  | 2.1.2         |

This chart would make more sense if this plugin actually did anything and people would have a reason
to be looking for older releases to run on older servers.

To use this as a template, just use the latest version of this project and update the PaperMC
version as needed. See more info on release stability below.

#### Release and Versioning Strategy
Stable versions of this repo are tagged `vX.Y.Z` and have an associated [release](https://github.com/CrimsonWarpedcraft/plugin-template/releases).

Testing versions of this repo are tagged `vX.Y.Z-RC-N` and have an associated [pre-release](https://github.com/CrimsonWarpedcraft/plugin-template/releases).

Development versions of this repo are pushed to the master branch and are **not** tagged.

| Event             | Plugin Version Format | CI Action                        | GitHub Release Draft? |
|-------------------|-----------------------|----------------------------------|-----------------------|
| PR                | yyMMdd-HHmm-SNAPSHOT  | Build and test                   | No                    |
| Cron              | yyMMdd-HHmm-SNAPSHOT  | Build, test, and notify          | No                    |
| Push to `main`    | 0.0.0-SNAPSHOT        | Build, test, release, and notify | No                    |
| Tag `vX.Y.Z-RC-N` | X.Y.Z-SNAPSHOT        | Build, test, release, and notify | Pre-release           |
| Tag `vX.Y.Z`      | X.Y.Z                 | Build, test, release, and notify | Release               |

### Discord Notifications
In order to use Discord notifications, you will need to create two GitHub secrets. `DISCORD_WEBHOOK_ID` 
should be set to the id of your Discord webhook. `DISCORD_WEBHOOK_TOKEN` will be the token for the webhook.

You can find these values by copying the Discord Webhook URL:  
`https://discord.com/api/webhooks/<DISCORD_WEBHOOK_ID>/<DISCORD_WEBHOOK_TOKEN>`

Optionally, you can also configure `DISCORD_RELEASE_WEBHOOK_ID` and `DISCORD_RELEASE_WEBHOOK_TOKEN`
to send release announcements to a separate channel.

For more information, see [Discord Message Notify](https://github.com/marketplace/actions/discord-message-notify).

---

**I've broken the rest of the changes up by their files to make things a bit easier to find.**

---

### settings.gradle
Update the line below with the name of your plugin.

```groovy
rootProject.name = 'ExamplePlugin'
```

### build.gradle
Make sure to update the `group` to your package's name in the following section.

```groovy
group = "com.crimsonwarpedcraft.exampleplugin"
```

Add any required repositories for your dependencies in the following section.

```groovy
repositories {
    maven {
        name 'papermc'
        url 'https://papermc.io/repo/repository/maven-public/'
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("io.papermc", "paperlib")
            includeModule("net.md-5", "bungeecord-chat")
        }
    }

    mavenCentral()
}
```

Also, update your dependencies as needed (of course).

```groovy
dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT'
    compileOnly 'com.github.spotbugs:spotbugs-annotations:4.9.3'
    implementation 'io.papermc:paperlib:1.0.8'
    spotbugsPlugins 'com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0'
    testCompileOnly 'com.github.spotbugs:spotbugs-annotations:4.9.3'
    testImplementation 'io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.13.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.13.1'
}
```

### src/main/resources/plugin.yml
First, update the following with your information.

```yaml
author: AUTHOR
description: DESCRIPTION
```

Next, the `commands` and `permissions` sections below should be updated as needed.

```yaml
commands:
  ex:
    description: Base command for EXAMPLE
    usage: "For a list of commands, type /ex help"
    aliases: example
permissions:
  example.test:
    description: DESCRIPTION
    default: true
  example.*:
    description: Grants all other permissions
    default: false
    children:
      example.test: true
```

### .github/dependabot.yml
You will need to replace all instances of `leviem1`, such as the one below, with your GitHub
username.

```yaml
reviewers:
  - "leviem1"
```

### .github/CODEOWNERS
You will need to replace `leviem1`, with your GitHub username.

```text
*   @leviem1
```

### .github/FUNDING.yml
Update or delete this file, whatever applies to you.

```yaml
github: leviem1
```

For more information see: [Displaying a sponsor button in your repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/displaying-a-sponsor-button-in-your-repository)

### CODE_OF_CONDUCT.md
If you chose to adopt a Code of Conduct for your project, please update line 63 with your preferred
contact method.

## Creating a Release
Below are the steps you should follow to create a release.

1. Create a tag on `main` using semantic versioning (e.g. v0.1.0)
2. Push the tag and get some coffee while the workflows run
3. Publish the release draft once it's been automatically created

## Building locally
Thanks to [Gradle](https://gradle.org/), building locally is easy no matter what platform you're on. Simply run the following command:

```text
./gradlew build
```

This build step will also run all checks and tests, making sure your code is clean.

JARs can be found in `build/libs/`.

## Contributing
See [CONTRIBUTING.md](https://github.com/CrimsonWarpedcraft/plugin-template/blob/main/CONTRIBUTING.md).

---

I think that's all... phew! Oh, and update this README! ;)
