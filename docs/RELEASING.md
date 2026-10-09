# 发布维护

## 自动发布

1. 修改 `app/build.gradle.kts` 的 `versionCode`、`versionName`，更新发布说明。关于页自动读取构建版本。
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

## 应用内更新提示

App 查询本仓库的 GitHub `releases/latest` API。自动提示要求正式发布、版本号
高于已安装版本，并至少存在一个状态为 uploaded、大小大于零的 APK 资产。
标签使用 `v主版本.次版本.修订版本`，例如 `v0.3.0`；数字逐段比较，构建元数据不影响排序。
草稿、预发布版、无法识别的标签及没有 APK 的发布不会触发提示。

开启自动检查后，应用进入前台时每 24 小时最多检查一次，网络失败不会弹窗。
“我的”中的手动检查绕过检查间隔和“跳过此版本”；关闭自动检查后仍可手动检查。
点击“更新”时，App 从 `https://github.com/Qi-huanye/Schedule/releases/download/<标签>/<APK 名>` 下载安装包，
并用同一发布的 `SHA256SUMS.txt` 校验；还会核对包名、版本号高于当前版本及签名证书，全部通过后才交给系统安装程序。
发布缺少 `SHA256SUMS.txt` 时只提供浏览器下载。发布工作流会自动上传该文件，请勿手动删除。

首次支持更新检查的 APK 仍需用户手动安装，之前的版本无法凭空获得此功能。
