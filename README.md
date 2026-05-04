# <img src="icon.png" width="28"/> Config Backuper

一个简单的 Minecraft 模组，用于备份游戏配置文件，支持可配置的备份路径和 WebDAV 云备份。

## 功能特性

- **可配置的备份触发** — 在配置界面保存时执行备份，不再自动在游戏启动时备份
- **可视化配置界面** — 基于 ModMenu + Cloth Config API，所有设置可在游戏内配置
- 备份游戏配置
- 备份模组配置
- 备份着色器配置
- 备份文件压缩
- 自动清理旧备份
- **WebDAV 云备份** — 备份完成后自动上传到 WebDAV 服务器

## 环境要求

- Minecraft 1.20+
- Fabric Loader >=0.15.0
- [ModMenu](https://modrinth.com/mod/modmenu)（可选，用于访问配置界面）
- [Cloth Config API](https://modrinth.com/mod/cloth-config)（必需）

## 配置说明

可通过 **ModMenu** → **Config Backuper** 在游戏内访问配置界面，或手动编辑 `config/config-backuper.json` 文件。

### 通用设置

- `includeGameConfigs` — 是否备份游戏配置（默认：`true`）
- `includeModConfigs` — 是否备份模组配置（默认：`true`）
- `includeShaderPackConfigs` — 是否备份着色器配置（默认：`true`）
- `compressionEnabled` — 是否启用备份压缩（默认：`true`）

### 备份存储

- `backupFolder` — 备份文件存储目录（默认：`./config-backuper-backups`）
- `backupFilePrefix` — 备份文件名称前缀（默认：`backup`）
- `backupFileSuffix` — 备份文件名称后缀（默认：`.zip`）
- `maxBackups` — 最大保留备份数量（`-1` 表示不限制，默认：`10`）

### WebDAV 云备份

- `webdavEnabled` — 是否启用 WebDAV 上传（默认：`false`）
- `webdavServerUrl` — WebDAV 服务器地址（例如：`https://example.com/remote.php/dav/files/user/`）
- `webdavUsername` — WebDAV 账户用户名
- `webdavPassword` — WebDAV 账户密码
- `webdavRemotePath` — 服务器上的远程目录路径（默认：`/ConfigBackuper/`）

> **注意：** 在游戏内配置界面保存时，会自动触发一次备份。如果启用了 WebDAV，备份文件将自动上传到配置的服务器。

## 整合包

你可以在整合包中自由使用本模组，无需请求许可。

## 开发

### 构建

```shell
cd fabric
./gradlew build
```
