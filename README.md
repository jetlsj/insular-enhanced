# Insular Enhanced（炼妖壶增强版）

一个面向 Android 工作资料（Work Profile）的应用隔离、克隆与管理工具。本仓库在 Insular 的基础上，重点修复了部分 Android 新版本及 Xiaomi HyperOS 设备上的克隆、安装和跨空间启动问题，并增加了“直接安装外部 APK 到壶中界”功能。

> [!IMPORTANT]
> 这是社区维护的非官方分支，与 Insular、Island、F-Droid 或设备厂商不存在隶属或背书关系。安装外部 APK 前请确认文件来源可信。

## 上游来源

本项目的代码继承关系如下：

1. 本仓库基于 [secure-system/Insular](https://gitlab.com/secure-system/Insular) 的 `dev-ci` 分支修改。
2. Insular 是 [oasisfeng/Island](https://github.com/oasisfeng/island) 的完全自由软件分支。
3. Insular 的建立还受到 [PeterCxy/Shelter](https://github.com/PeterCxy/Shelter) 启发。

上游 Insular 的使用文档可在 [secure-system.gitlab.io/Insular](https://secure-system.gitlab.io/Insular/) 查看；F-Droid 上的 `com.oasisfeng.island.fdroid` 是上游发行版，并非本增强分支的构建产物。

## 上游主要能力

- 使用 Android 工作资料隔离应用及其数据
- 将主空间中已安装的应用克隆到壶中界
- 冻结、解冻、隐藏和按需启动应用
- 分别控制不同应用的 VPN、USB 等策略
- 通过 Android DPC/Open API 向第三方应用提供受控能力

Android 同一用户下通常只能由一个 DPC 管理一个工作资料；本项目不是虚拟机，也不会在同一个壶中界中同时安装多个相同包名、不同版本的 APK。

## 本分支的修改

### 1. “必要权限”设置

设置页增加“必要权限”分组，集中展示并跳转到与核心流程相关的系统权限：

- 安装未知应用：克隆和安装外部 APK 前检查当前空间是否已授权
- 通知：避免安装失败通知被关闭后用户得不到反馈
- 启动其他应用（HyperOS）：在 Xiaomi/HyperOS 上跳转到对应权限管理页

权限状态会在进入或返回设置页时刷新。

### 2. 克隆安装可靠性与反馈

- 克隆前校验目标空间的“安装未知应用”权限，缺失时给出明确提示并打开授权页
- 将安装器、应用商店和授权页的启动改为由前台界面发送目标空间创建的 `PendingIntent`，兼容 Android 14+ 的后台启动限制
- 补充安装会话提交、确认页面、安装状态和失败原因日志
- 安装确认页面无法打开、安装失败或通知不可见时，使用通知或 Toast 提供可见反馈
- 改进连续克隆多个应用时的安装会话与结果处理，减少必须执行“修缮壶中界”后才能继续安装的情况
- 安装完成后主动刷新应用列表和可启动状态

### 3. 新装应用首次启动

- 检查新安装应用的 `FLAG_STOPPED` 状态，识别系统或厂商固件静默丢弃的首次启动请求
- 常规 `LauncherApps` 启动未生效时，回退到目标空间创建、主界面发送的跨空间启动动作
- 对 HyperOS 的“启动应用”权限增加设置入口和清晰提示
- 首次启动成功后分阶段刷新应用列表，避免必须手动启动一次后才能从本应用唤起

### 4. 直接安装外部 APK 到壶中界

主界面右上角菜单新增“安装 APK 到壶中界”：

1. 在壶中界内检查“安装未知应用”权限。
2. 打开壶中界可用的系统文件选择器。
3. 将选中的 APK 交给项目自带安装器，在壶中界内创建安装会话。
4. 展示系统安装确认页，并记录最终安装结果。

此流程不要求主空间已经安装同包名应用。针对 HyperOS 对隐式文件选择 Intent 的改写，代码会优先锁定工作资料内真实可用的 DocumentsUI；同时修复了 Android 16 上独立 APK 的空 `splitNames` 被误判为拆分 APK 的问题。

目前只支持单个、完整的 `.apk` 文件，不支持 `.apks`、`.xapk` 或其他 split APK 集合。壶中界已有同包名应用时，Android 仍会执行正常的签名与版本校验；本功能不会绕过系统安全规则。

## 兼容性与验证

当前修改已完成以下验证：

- `completeFdroidDebug` 变体可成功编译
- Xiaomi HyperOS、Android 16（API 36）真机上的工作资料流程
- 连续克隆、安装结果反馈和跨空间首次启动
- 从外部选择独立 APK，并在工作资料用户中返回 `INSTALL_SUCCEEDED`
- 测试完成后移除工作资料中的测试应用，主空间应用不受影响

不同厂商会修改工作资料、后台启动和权限管理行为。如果遇到问题，请附上 Android 版本、系统版本、设备厂商以及下文日志。

## 构建

建议准备 JDK 17、Android SDK 35，并使用仓库中的 Gradle Wrapper。首次克隆时需要同时拉取子模块：

```bash
git clone --recurse-submodules https://github.com/KaiLiDev/insular-enhanced.git
cd insular-enhanced
```

Windows：

```powershell
.\gradlew.bat :assembly:assembleCompleteFdroidDebug --console=plain
```

Linux/macOS：

```bash
./gradlew :assembly:assembleCompleteFdroidDebug --console=plain
```

调试 APK 输出位置：

```text
assembly/build/outputs/apk/completeFdroid/debug/assembly-complete-fdroid-debug.apk
```

正式分发前请自行配置签名并完整回归测试。更换签名会影响已有安装的升级路径，也可能影响已经建立的工作资料，请勿在不了解后果时直接覆盖生产环境。

## 排查日志

克隆、安装和跨空间启动均带有操作追踪 ID，可使用以下命令收集相关日志：

```bash
adb logcat -v time -s Island.AC Island.AIA Island.AISR Island.AIN Island.AppControl Island.Manager Island.XPAL Island.ExternalApk
```

提交公开 Issue 前请检查并移除日志中的应用包名、文件 URI 或其他不希望公开的信息。

## 许可证与署名

仓库已经包含上游的 [Apache License 2.0](LICENSE)，本分支继续使用该协议，无需另加一份不同许可证。再分发源码或二进制时，请保留 `LICENSE`、上游版权/署名信息以及对修改内容的说明。更多来源和修改声明见 [NOTICE](NOTICE)。

除非适用法律另有要求，本软件按“原样”提供，不附带任何明示或默示担保。

## 贡献

欢迎通过 GitHub Issue 提交可复现的问题，并附上必要的系统信息和脱敏日志。提交 Pull Request 时，请说明目标 Android/ROM 版本和验证结果。
