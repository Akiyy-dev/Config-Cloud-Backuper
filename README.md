# <img src="icon.png" width="28"/> Config Backuper

一个简单的 Minecraft 模组，用于备份游戏配置文件，支持可配置的备份路径和 WebDAV 云备份，可在客户端与服务端使用。

## 功能特性

- **双端命令支持** — 客户端与服务端均可使用命令；服务端额外支持区分前缀 `/server_config_backuper ...`（需安装 **Fabric API**）
- **客户端上传到服务端** — 客户端可将本地备份上传到服务端；服务端按玩家名分目录保存并可配置每位玩家保留数量
- **可选图形界面** — 安装 [ModMenu](https://modrinth.com/mod/modmenu) 与 [Cloth Config API](https://modrinth.com/mod/cloth-config) 后，可通过 ModMenu 打开设置界面
- 备份游戏配置、模组配置、着色器与更多目录（见 `config/config-cloud-backuper.json`）
- 备份文件压缩与自动清理旧备份
- **WebDAV** — 备份完成后可按配置自动上传；亦可通过命令列出/上传/下载
- **安全校验链** — 客户端上传仅允许 `.zip` 且限制大小，文件名净化，服务端按 SHA-256 校验并记录审计日志

## 环境要求

- Minecraft 1.20+
- Fabric Loader >=0.15.0
- **Fabric API**（本模组注册客户端与服务端命令所必需）
- [ModMenu](https://modrinth.com/mod/modmenu)（可选）
- [Cloth Config API](https://modrinth.com/mod/cloth-config)（可选，仅在使用 ModMenu 图形界面时需要）

## 命令说明（客户端/服务端）

命令统一使用分组路径：`remote cloud` / `remote server`。

根命令前缀：

- 客户端：`/config_backuper`
- 服务端：`/config_backuper` 或 `/server_config_backuper`（双前缀，功能一致）

| 子命令 | 说明 |
|--------|------|
| `backup` | 执行本地备份并清理超出 `maxBackups` 的旧文件；若已在 WebDAV 配置中启用上传，则随后上传**最新**本地备份 |
| `list` | 列出本地备份目录中符合前缀/后缀的备份文件 |
| `config reload` | 从磁盘重新读取 `config-cloud-backuper.json` 并刷新备份器 |
| `config show` | 显示当前主配置项与取值 |
| `config set <键> <值>` | 修改并保存主配置（键名与 `config show` 一致，布尔值可用 `true`/`false` 等） |
| `remote cloud status` | 查看 WebDAV 配置（密码以掩码显示） |
| `remote cloud list` | 列出远程目录中的文件 |
| `remote cloud upload [文件名]` | 将本地备份目录中的指定文件（省略则选最新）**强制**上传到 WebDAV（不要求开启「启用上传」开关，但仍需填写 URL 与凭据） |
| `remote cloud download [文件名]` | 将远程文件下载到本地备份目录（省略则下载按名称排序后的最新一个） |
| `remote cloud set <字段> <值>` | 写入 `config-cloud-backuper_webdav.json` 中的字段：`enabled`、`serverUrl`、`username`、`password`、`remotePath` |
| `remote server status` | （客户端）查询服务端“客户端上传”配置状态 |
| `remote server list` | （客户端）查询当前玩家在服务端的已上传备份列表 |
| `remote server upload [文件名]` | （客户端）上传本地备份到服务端（省略文件名则上传最新） |

未带子命令时执行根命令会显示简要帮助。

### 服务端示例

- `/server_config_backuper backup`：触发服务端备份
- `/server_config_backuper list`：查看服务端备份目录列表
- `/server_config_backuper config show`：查看服务端当前配置
- `/server_config_backuper remote server status`：查看客户端上传到服务端配置
- `/server_config_backuper remote server list`：查看你自己的上传备份列表（需 OP 等级 > 2）
- `/server_config_backuper remote server set folder ./configcloudbackuper-backups/client-uploads`：设置上传根目录
- `/server_config_backuper remote server set maxPerPlayer 20`：设置每位玩家最多保留 20 个上传备份

## 配置说明

主配置：`config/config-cloud-backuper.json`。WebDAV：`config/config-cloud-backuper_webdav.json`。亦可使用上文 `config` / `remote cloud` / `remote server` 子命令修改。

### 通用与备份存储

与 JSON 字段一致，主要包括：`includeGameConfigs`、`includeModConfigs`、`includeShaderPackConfigs`、`includeSchematics`、`include3dSkin`、`includeSyncmatics`、`includeDefaultConfigs`、`compressionEnabled`、`maxBackups`、`backupFolder`、`backupFilePrefix`、`backupFileSuffix`。

### 客户端上传到服务端（主配置字段）

- `clientUploadToServerEnabled`：是否允许客户端上传到服务端（默认 `true`）
- `clientUploadFolder`：服务端保存客户端上传备份的根目录（默认 `./configcloudbackuper-backups/client-uploads`）
- `clientUploadMaxBackupsPerPlayer`：每位玩家最多保留数量（`-1` 不限制，默认 `10`）

服务端保存结构示例：

- `<clientUploadFolder>/<玩家名>/<备份文件>`

### WebDAV（`config-cloud-backuper_webdav.json`）

- `enabled` — 是否在**本地备份流程**结束后自动上传最新备份
- `serverUrl`、`username`、`password`、`remotePath` — 服务器与路径

WebDAV 安全行为：
- 上传备份时会同时上传同名校验文件：`<filename>.sha256`
- 下载备份前会先读取 `.sha256`，下载后校验 SHA-256；不一致则删除下载文件并报错

## 整合包

你可以在整合包中自由使用本模组，无需请求许可。

## 开发

### 构建

```shell
cd fabric
./gradlew build
```

开发时若要在客户端测试 ModMenu + Cloth 界面，仓库已配置 `modLocalRuntime` 的 Cloth Config；ModMenu 仍需自行加入运行实例（或 `modRuntimeOnly`）。
