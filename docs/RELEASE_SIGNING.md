# Release APK 签名配置

> 本文档说明如何配置签名 keystore，让 GitHub Actions 在打 tag 时自动构建并签名 release APK。
>
> **不配置也能用**：项目默认会跳过 release 签名，仍然产出 `debug` APK 附到 release 上。本文档只是给需要"签名 release 包"场景的开发者用的。

---

## 为什么需要签名

- **Android 拒绝安装 unsigned APK**（除非用户用 ADB `--bypass-low-target-sdk-block` 等）
- **同一应用的不同版本必须用同一签名**才能升级，否则用户必须先卸载再装
- **签名让用户能确认 APK 真的来自你**（防中间人篡改）

---

## 一次性准备：生成 keystore

> 这个文件是你的"应用身份证"。**生成后请保存到 1Password / Bitwarden / 安全网盘多副本备份**。一旦丢失，你以后发布的版本将无法被现有用户升级，必须改 applicationId 重新发布。

打开命令行执行：

```bash
# Windows / macOS / Linux 通用
keytool -genkey -v \
  -keystore offscan-release.jks \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500 \
  -alias offscan
```

会问你 6 个问题：

| 问题 | 建议 |
|---|---|
| Enter keystore password | 用密码管理器生成强密码（≥ 16 位） |
| Re-enter new password | 同上 |
| What is your first and last name? | 你的名字 / 项目名（如 OffScan）|
| What is the name of your organizational unit? | 留空回车 |
| What is the name of your organization? | 留空回车 |
| What is the name of your City or Locality? | 留空回车 |
| What is the name of your State or Province? | 留空回车 |
| What is the two-letter country code? | CN |
| Is XXX correct? | yes |
| Enter key password (RETURN if same as keystore password) | **直接回车**（用同一个密码，简化配置）|

执行完会生成 `offscan-release.jks` 文件。**这个文件不能提交到 git！**

---

## 把 keystore 配到 GitHub Secrets

打开 `https://github.com/<你的用户名>/OfflineScanner/settings/secrets/actions`，依次添加以下 4 个 secret：

### Secret 1: `OFFSCAN_KEYSTORE_BASE64`

把 keystore 文件 base64 编码后粘贴：

**Windows PowerShell**:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("offscan-release.jks")) | Set-Clipboard
# 内容已经复制到剪贴板，直接粘贴到 GitHub
```

**macOS / Linux**:
```bash
base64 -i offscan-release.jks | pbcopy   # macOS
base64 -w0 offscan-release.jks | xclip   # Linux
```

### Secret 2: `OFFSCAN_KEYSTORE_PASSWORD`

刚才生成 keystore 时用的密码。

### Secret 3: `OFFSCAN_KEY_ALIAS`

填 `offscan`（就是你 keytool 命令里的 `-alias` 值）。

### Secret 4: `OFFSCAN_KEY_PASSWORD`

如果你按上面建议"直接回车"了，这个密码和 `OFFSCAN_KEYSTORE_PASSWORD` 一样。

---

## 验证签名 CI

配好 4 个 secret 后，下次推 `v*` tag 时（如 `git tag v1.0.1 && git push origin v1.0.1`），`.github/workflows/release.yml` 会自动：

1. 拉代码
2. 解码 keystore 到 CI runner 的临时目录
3. `./gradlew assembleRelease`（启用 R8 + 资源压缩 + 签名）
4. `./gradlew assembleDebug`
5. 把两个 APK 都附到 GitHub Release 上：
   - `offscan-v1.0.1-release.apk` (~70MB, 已签名, 推荐用户安装)
   - `offscan-v1.0.1-debug.apk` (~130MB, 未签名 / 用 debug 证书签, 仅供开发者排错)

也可以在 [Actions 页面](../../actions/workflows/release.yml) 手动 `workflow_dispatch` 重跑一次现有 tag。

---

## 本地验证

如果想在本地试一下签名流程（避免推 tag 才发现配置不对）：

```bash
# Windows PowerShell
$env:OFFSCAN_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("offscan-release.jks"))
$env:OFFSCAN_KEYSTORE_PASSWORD = "你的密码"
$env:OFFSCAN_KEY_ALIAS = "offscan"
$env:OFFSCAN_KEY_PASSWORD = "你的密码"
.\gradlew.bat assembleRelease
```

```bash
# macOS / Linux
export OFFSCAN_KEYSTORE_BASE64="$(base64 -w0 offscan-release.jks)"
export OFFSCAN_KEYSTORE_PASSWORD="你的密码"
export OFFSCAN_KEY_ALIAS="offscan"
export OFFSCAN_KEY_PASSWORD="你的密码"
./gradlew assembleRelease
```

产出在 `app/build/outputs/apk/release/app-release.apk`。验证签名：

```bash
# 用 jdk 自带的 jarsigner
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
# 应该看到 "jar verified."
```

---

## 安全须知

1. **永远不要把 keystore 文件 commit 到 git**。当前 `.gitignore` 已经 cover `*.jks` / `*.keystore`，但还是要小心。
2. **不要把 secrets 截图发到 issue / PR**。GitHub 自带 secret scanner 会扫描 commit 历史，但不能完全防误传。
3. **离职 / 卸任前导出 keystore 给接手者**。否则项目下任维护者无法发版。
4. **永远保持至少 2 份离线备份**。U 盘 + 加密网盘双保险，最理想。

---

## 常见问题

### Q: 已经发布过 v1.0.0（debug 签名版），后续切换到 release 签名会冲突吗？

**会**。Android 用 keystore 公钥指纹判定 "同一应用"。如果你 v1.0.0 用了 debug keystore，v1.0.1 用了 release keystore，用户升级会被拒绝（"应用未签名或签名不一致"）。

**解法**：保留 v1.0.0 不动，让 v1.0.1 是首个 release 签名版。用户升级路径上需要"先卸载 v1.0.0 再装 v1.0.1"，可以在 release notes 里说明。

### Q: 如果丢了 keystore？

无解。改 `applicationId`（如 `com.scanner.offline.v2`）重新发布是唯一办法。所以 **请保管好 keystore**。

### Q: 想换更安全的签名方案（GitHub OIDC + Sigstore）？

可以，参考 [Sigstore for Android](https://github.com/sigstore/sigstore-java)。但对个人项目来说，**经典 keystore 已经够用**。
