# 发布维护

## 自动发布

1. 修改 `app/build.gradle.kts` 的 `versionCode`、`versionName`，同步更新关于页与发布说明。
2. 更新 `docs/RELEASE_NOTES.md`，提交到 `main`，等待 Android CI 通过。
3. 创建与 `versionName` 一致的标签（例如 `v0.2.0`），推送标签。
4. `Publish Android release` 工作流执行测试、Lint、签名构建、签名校验，发布 APK 与 SHA-256 文件。

仓库 Actions Secrets：

| 名称 | 内容 |
|---|---|
| `SCHEDULE_KEYSTORE_BASE64` | 签名 keystore 的 Base64 内容 |
| `SCHEDULE_KEYSTORE_PASSWORD` | keystore 密码 |
| `SCHEDULE_KEY_ALIAS` | 签名别名 |
| `SCHEDULE_KEY_PASSWORD` | 私钥密码 |

Secrets 仅由标签发布任务使用，PR 验证不接触签名密钥。不要将密钥、密码或真实课表提交到仓库。
签名 keystore 必须离线备份，丢失后无法用相同签名为既有安装发布更新。
首个发布保留此前开发安装的签名证书，但使用非调试 release 构建。

## 本地发布构建

准备环境变量 `SCHEDULE_KEYSTORE_PATH`、`SCHEDULE_KEYSTORE_PASSWORD`、
`SCHEDULE_KEY_ALIAS`、`SCHEDULE_KEY_PASSWORD`，执行：

```sh
scripts/package-release.sh
```

产物位于被 Git 忽略的 `release/`。没有签名环境变量时，普通 `assembleRelease` 仅用于未签名构建，不可作为可安装发布包。

GitHub 默认源码 ZIP 不包含 APK；安装包位于 Release 的 Assets。
