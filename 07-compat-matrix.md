# 07 · 运行环境兼容矩阵与能力探测

> **本册定位**：模块面对的运行环境（Root 方案 / Zygisk 实现 / 免 Root / Android 版本 / ABI）的兼容事实表，以及一套可复制的能力探测与优雅降级模板。
> **何时读这一册**：评估模块在目标设备上能不能跑、写环境分支逻辑、处理"换个环境就失效"类问题。
> **前置依赖**：无。排障 → 见 `06-debugging-runbook.md`；service 能力探测 → 见 `08-service-and-advanced.md §1`。
> **交叉引用**：→ 见 `06-debugging-runbook.md §S9`、`08-service-and-advanced.md §1.5`、`10-agent-rules.md`

---

## ⭐ 版本基准声明（先读）

存在**两条版本号互相冲突的 LSPosed 2.x 线**，本册提及版本号时一律同时点明是哪条线：

| 线 | 状态 | API 支持 |
|---|---|---|
| `LSPosed/LSPosed` 官方 GitHub | 停在 v1.9.2（2023-10-11），master 最后提交 2024-01-07 | — |
| `JingMatrix/Vector`（原 JingMatrix/LSPosed） | 活跃；v2.0=API 100、v2.1=API 101、**v2.2=API 102** | Android 8.1~17 |
| lsposed.cn 镜像线 | **v2.0.2~v2.2.0**，由原 LSPosed 核心团队维护的平行发布渠道。v2.1.0 起实现 API 102（注意：此 2.1 ≠ Vector 的 2.1=API 101） | 全部为【一手·changelog】 |

**模块代码绝对禁止比较框架版本字符串**；一切环境判断走运行时探测（本册 §3）。

---

## 1. 兼容矩阵

### 1.1 Root 方案【一手·README/release body】

| Root 方案 | 支持 | 注意 |
|---|---|---|
| **Magisk** | ✅（v24+） | 需开启 Zygisk，或用 ZygiskNext 替代 |
| **KernelSU** | ✅ | Zygisk 需 ZygiskNext（KernelSU ≥ 10940，ksud ≥ 11575）或 NeoZygisk |
| **APatch** | ✅ | Zygisk 需 NeoZygisk |
| **Riru** | ❌ 已移除 | v1.10.1 起改用 Zygisk API v4，移除 Riru |
| 多 root 共存 | ❌ | ZygiskNext README："No multiple root implementation installed" |

### 1.2 Zygisk 实现

| 实现 | 定位 |
|---|---|
| Magisk 内置 | 基准 |
| **ZygiskNext**（Dr-TSNG） | 独立实现，为 KernelSU 提供 Zygisk API；+Magisk 时需 ≥ 26402 且**关闭内置 Zygisk** |
| **ReZygisk**（PerformanC） | 官方排障指南推荐替代 |
| **NeoZygisk**（JingMatrix） | ptrace 注入，为 APatch/KernelSU 提供 API；Vector 官方示例环境 |

【一手】官方排障指南原话："For non-Magisk users, please flash the latest Zygisk module (debug version if possible), such as NeoZygisk, ReZygisk or ZygiskNext."

**"兼容 Zygisk Next API 的 Zygisk 实现"的含义**【一手·changelog v2.2.0 + ZygiskNext docs】：Zygisk Next API 是 native-only 头文件 API（`zygisk_next_api.h`），覆盖两类常规 Zygisk 拿不到的进程——① init-oriented 早期进程（如 Android 17+ 的 dex2oat 包装器）；② HyperOS Runtime 应用（native-only）。**v2.2.0 changelog 明确确认**：HyperOS Runtime 注入与 A17 dex2oat 包装器注入均**强依赖**此条件。框架不满足此条件时的表现是**那两类进程 hook 静默失效**（功能缺失），不是整体崩溃。可用的兼容实现包括 ZygiskNext、ReZygisk、NeoZygisk。

### 1.3 免 Root（LSPatch / embedded）

| 事实 | 依据 |
|---|---|
| LSPatch = 向目标 APK 重打包注入 dex/so 的 Rootless 实现 | 【一手】README（仓库最后提交 2023-12-13，core 指向 v1.9.2） |
| 签名必然改变（重打包固有） | 逻辑必然 |
| **embedded 框架下 `getRemotePreferences` / `listRemoteFiles` / `openRemoteFile` 契约性抛 `UnsupportedOperationException`** | 【一手·硬证据】三个方法的 Javadoc 均写 `@throws UnsupportedOperationException If the framework is embedded` |
| 对 API 102 的支持存疑（core 停在 v1.9.2） | 【一手】submodule 记录 |
| `XposedService` 在 embedded 下是否可用 | 【推断】缺独立框架进程推送 binder，`onServiceBind` 极可能永不回调 → 见 `08-service-and-advanced.md §1.5` |

### 1.4 Android 版本

| 版本 | 要点 | 依据 |
|---|---|---|
| **9 (API 28)** | **v2.1.1 起最低支持版本**。模块 `minSdk` 应设为 28 | 【一手·changelog v2.1.1】 |
| 8.1 (O) | Vector v2.1 release body 声称支持 8.1~17，但 v2.1.1 changelog 明确提升底线至 9。**以 changelog 为准** | 【一手·Vector release + changelog v2.1.1】 |
| 10 (Q) | `onPackageLoaded`/`getDefaultClassLoader()` 带 @RequiresApi(Q)，**10 以下不可用** | 【一手·源码】 |
| 12+ | 静态方法 hook 首次调用问题（v1.8.0 修）；LSPlt hook 被 strip 的 libart（v1.10.1 起）；**v2.1.1 起高级隐藏功能支持 A12+** | 【一手·changelog v2.1.1】 |
| 13 | v1.8.4 tested；DP1 曾有系统框架 hook 失效（#1675 已修） | 【一手·release】 |
| 14 | 14 QPR2 曾有 ART 兼容问题（v1.8.6 修）；后续版本已稳定 | 【一手·release】 |
| 15 / 16 | v1.10.1 / v1.10.2 完全支持；16 QPR3 Beta 有 UserManager 与 dex2oat wrapper 变更（v1.11.0 修） | 【一手·release】 |
| **17** | Vector v2.1 完整覆盖；**v2.2.0 起以注入方式处理 dex2oat 包装器，避免挂载泄漏**（强依赖 ZygiskNext API 兼容实现） | 【一手·Vector release + changelog v2.2.0】 |



**HyperOS Runtime 注入（v2.2.0 新增）**【一手·changelog v2.2.0】：
- 小米 HyperOS 将部分系统应用重写为 Rust Runtime，运行在 native 层而非 ART，无 JNI 环境。
- v2.2.0 新增对此类进程的注入支持，**强依赖兼容 Zygisk Next API 的 Zygisk 实现**（ZygiskNext / ReZygisk / NeoZygisk 三选一）。
- 若 Hook 目标是 HyperOS Rust 应用（如系统设置、安全中心），必须在模块文档中声明对 ZygiskNext 类实现的依赖，否则 Hook 将静默失效。
- 判断方法：`getFrameworkProperties()` 的扩展位掩码（具体位值待框架侧定义），或在 `onPackageReady` 中探测 `ClassLoader` 是否为常规 ART PathClassLoader。

**Android 17 dex2oat 包装器注入（v2.2.0 新增）**【一手·changelog v2.2.0】：
- Android 17 起 dex2oat 包装器行为变更，旧版框架的挂载方式会导致泄漏。
- v2.2.0 改用注入方式处理，避免挂载泄漏，同样**强依赖兼容 Zygisk Next API 的 Zygisk 实现**。
- 症状：旧框架在 A17 上 dex2oat 相关进程 hook 失效或系统日志出现 dex2oat 挂载警告。

**心智模型**：
- **内联随版本推进加剧**，是"hook 装上了不触发"的头号原因（处置 → `06-debugging-runbook.md §S6`）。
- **dex2oat 包装器**是框架防 AOT 破坏 hook 的机制，本身极脆弱（Android 10 命名空间 / 15 APEX linker / 16 ART 更新 / 17+ 改注入）。
- **别在旧框架上追新 Android**——症状是"莫名 hook 失效"而非崩溃。框架版本必须跟 Android 版本对齐。

### 1.5 ABI

- **【一手·changelog v2.0.3】移除 32 位 x86 架构支持**。模块 `abiFilters` 中禁止包含 `x86`，否则在 x86 模拟器上安装后框架无法加载模块。
- `x86_64`（64 位 x86）不受影响，仍可用于模拟器调试。
- 模块 native 库路径由框架按 `SUPPORTED_64_BIT_ABIS`/`SUPPORTED_32_BIT_ABIS` 拼出（`<apk>!/lib/<abi>`）【一手】。
- **推论**：带 native 库的模块必须打 `arm64-v8a`；`armeabi-v7a` 按目标设备保留；32 位进程在 64-only 框架下拿不到注入；混 ABI 是真实故障源（v1.11.0 修过 Android 10 上的 32/64 dex2oat 崩溃）。

---

## 2. 探测 API 与能力位【一手·源码】

```java
// io.github.libxposed.api.XposedInterface
int  getApiVersion();             // 运行时 Xposed API 版本（非 LIB_API 常量）
String getFrameworkName();        // 只用于日志展示，禁止字符串比较
String getFrameworkVersion();
long getFrameworkVersionCode();
long getFrameworkProperties();    // 能力位掩码
```

| 常量 | 值 | 含义 |
|---|---|---|
| `PROP_CAP_SYSTEM` | `1L` | 可 hook system_server 及系统进程 |
| `PROP_CAP_REMOTE` | `1L << 1` | 支持远程 preferences / 远程文件 |
| `PROP_RT_API_PROTECTION` | `1L << 2` | **禁止**反射/动态加载代码访问 Xposed API；`PROP_RT_` 前缀 = **每次启动可能变** |

## 3. 能力探测模板（可直接复制）

```java
// app/src/main/java/com/example/myhook/ModuleMain.java
package com.example.myhook;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static final String TAG = "MyModule";

    /** 本次会话的能力快照；只在 onModuleLoaded 填一次。PROP_RT_* 禁止跨启动缓存。 */
    public static final class Caps {
        public final int  api;
        public final long props;
        public final boolean capSystem;
        public final boolean capRemote;
        public final boolean apiProtected;
        public final boolean embedded;   // 推断：无远程能力 ⇒ 嵌入式框架

        Caps(int api, long props) {
            this.api = api;
            this.props = props;
            this.capSystem    = (props & PROP_CAP_SYSTEM) != 0;
            this.capRemote    = (props & PROP_CAP_REMOTE) != 0;
            this.apiProtected = (props & PROP_RT_API_PROTECTION) != 0;
            this.embedded     = !this.capRemote;
        }

        @Override public String toString() {
            return String.format(Locale.ROOT,
                "Caps{api=%d, props=0x%x, system=%b, remote=%b, apiProtected=%b, embedded=%b}",
                api, props, capSystem, capRemote, apiProtected, embedded);
        }
    }

    private volatile Caps caps;

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        caps = new Caps(getApiVersion(), getFrameworkProperties());
        log(Log.INFO, TAG, String.format(Locale.ROOT,
            "framework=%s ver=%s code=%d proc=%s sysServer=%b | %s",
            getFrameworkName(), getFrameworkVersion(), getFrameworkVersionCode(),
            param.getProcessName(), param.isSystemServer(), caps));

        // 降级点 1：API 版本
        if (caps.api < API_101) {
            log(Log.ERROR, TAG, "framework API too old (<101), module disabled");
            return;
        }
        // 降级点 2：远程能力（embedded/LSPatch 下三个方法契约性抛 UnsupportedOperationException）
        if (!caps.capRemote) {
            log(Log.WARN, TAG, "no remote capability: getRemotePreferences/listRemoteFiles/openRemoteFile will throw");
        }
        // 降级点 3：API 保护
        if (caps.apiProtected) {
            log(Log.WARN, TAG, "PROP_RT_API_PROTECTION: never touch Xposed API via reflection/dynamic code");
        }
        // 降级点 4：系统进程 hook 能力
        if (param.isSystemServer() && !caps.capSystem) {
            log(Log.WARN, TAG, "no PROP_CAP_SYSTEM; skip system hooks");
        }
    }

    /** 102+ 特性统一走这个守卫（同时满足 XposedNewApi lint 检查）。 */
    private boolean supports(int api) {
        return getApiVersion() >= api;
    }
}
```

## 4. 三条探测纪律（Agent 必守）

1. **用能力位，不用框架名**。`getFrameworkProperties()` 是官方契约；`getFrameworkName()` 字符串不可靠（具体返回值" LSPosed"/"Vector"/其他无一手枚举）。
2. **`PROP_RT_*` 每次启动都可能变**（Javadoc 明文）→ 每会话重读，禁止持久化缓存。
3. **远程 API 必包 try/catch**：

```java
try {
    getRemotePreferences("default");   // 宿主进程内【只读】
} catch (UnsupportedOperationException e) {
    // embedded 框架（LSPatch/Patch 模式）：降级路径
}
```

`getRemotePreferences` / `listRemoteFiles` / `openRemoteFile` 三个方法在 embedded 下**契约性抛** `UnsupportedOperationException`【一手·Javadoc】。

## 5. 兼容性测试清单（发版前）

- [ ] `minSdk`（Gradle，≥26）与 `module.prop` 的 `minApiVersion`/`targetApiVersion`（Xposed API）各自正确
- [ ] `onPackageLoaded` 相关逻辑有 `SDK_INT >= 29` 意识（Q 以下不触发）
- [ ] 使用 `@SinceApi(102)` API 处都有 `getApiVersion() >= 102` 守卫或 `minApiVersion=102`
- [ ] Remote 能力调用前探测 `PROP_CAP_REMOTE` 且有 `UnsupportedOperationException` 降级
- [ ] system_server 逻辑前探测 `PROP_CAP_SYSTEM`
- [ ] native 库含 `arm64-v8a`（如适用）；16KB 页对齐（Android 15+ 新设备要求）
- [ ] release 构建在真机上验证过（三件套 / 内联 / dex2oat 问题只在实机暴露）
- [ ] 目标 Android 版本与所用框架版本对齐（旧框架不追新系统）

---

## 未验证 / 未获取到

- **镜像线全部条目**（v2.0.x~v2.1.1 的 Android 支持变化、A12+ 高级隐藏、A17 QPR 适配、移除 32 位 x86、New XSharedPreferences 移除计划）仅【二手】（lsposed.cn），GitHub 无对应 release 交叉验证。
- ~~"LSPosed 2.2.0 支持 HyperOS Runtime 注入 / dex2oat 包装器注入"~~ → 已转正为【一手·changelog v2.2.0】，见 §1.4。
- `getFrameworkName()` 的**具体返回值枚举**（"LSPosed"/"Vector"/"LSPatch"…）未取到一手证据——因此禁止字符串比较。
- Android 14 QPR2 ART 变更的绕过方案来自第三方博客【二手】。
- 16KB 页对齐对模块 native 库的具体要求：官方文档未获取到（Android 15+ 设备侧事实，非 libxposed 契约）。
- LSPatch 对 API 102 的实际支持：仓库最后提交 2023-12-13 / core=v1.9.2，无可验证的 102 支持证据。
