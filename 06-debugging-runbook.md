# 06 · 调试 Runbook：日志、Hook 验证、S0-S9 排障决策树、adb 速查

> **本册定位**：模块"行为不符合预期"时的完整排障手册——日志抓取、hook 生效性验证、标准化二分决策树、adb 命令、热重载验证清单。
> **何时读这一册**：模块装上后没反应、hook 装上了不触发、release 失效、热重载后功能消失等一切"不生效"场景。
> **前置依赖**：无。坑点根因 → 见 `05-pitfalls.md`；兼容性 → 见 `07-compat-matrix.md`。
> **交叉引用**：→ 见 `05-pitfalls.md`（快查索引表）、`08-service-and-advanced.md §4`、`02-project-scaffold.md §7`

---

## A1. 日志

### A1.1 `log()` API【一手·源码】

```java
// io.github.libxposed.api.XposedInterface
void log(int priority, @Nullable String tag, @NonNull String msg);
void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr);
```

- `priority` 直接取 **`android.util.Log` 常量**（VERBOSE=2 / DEBUG=3 / INFO=4 / WARN=5 / ERROR=6 / ASSERT=7）；libxposed API 自身不定义优先级常量。
- **tag 可 null、msg 不可 null**；带 Throwable 的重载是唯一能把堆栈写进 Xposed 日志的方式。
- ❌ 不存在 `XposedBridge.log(String)`；`targetApiVersion >= 102` 禁止调用 legacy API。

### A1.2 输出目的地

| 通道 | 说明 |
|---|---|
| 框架日志 | 管理器 → Logs 页面；落盘 `/data/adb/lspd/log/`（官方排障指南） |
| logcat | v1.9.x `LSPosedContext` 实现把模块日志写入 `android.util.Log`（tag 形如 `LSPosedContext`），**会出现在 logcat**。API 102 实现的 tag 原样/加前缀**未验证** |
| 管理器自动保存 | 上一次用户会话的日志会保留——进程重启后仍可回捞 |
| daemon 落盘 | Zygisk 崩溃时 daemon 仍会写日志——"框架没起来"也要抓 |

**Agent 稳妥做法**：模块固定唯一 TAG；logcat 里同时用「模块 TAG」和「LSPosed 相关 tag」两种过滤捞。

### A1.3 指纹行（每个模块必写）

```java
// app/src/main/java/com/example/myhook/ModuleMain.java （片段）
@Override
public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
    log(Log.INFO, TAG, String.format(Locale.ROOT,
        "loaded pkg=%s proc=%s sysServer=%b | fw=%s ver=%s code=%d api=%d props=0x%x",
        getModuleApplicationInfo().packageName,
        param.getProcessName(),
        param.isSystemServer(),
        getFrameworkName(), getFrameworkVersion(), getFrameworkVersionCode(),
        getApiVersion(), getFrameworkProperties()));
}
```

之后所有排障先看这一行**出不出现**（→ S0-S4 / S5-S9 的二分信号）。

### A1.4 官方日志抓取命令【一手·官方排障指南】

```bash
# 抓 verbose 日志（即使 Zygisk 崩了，daemon 仍落盘）
adb shell "su -c 'cat /data/adb/lspd/log/verbose_* > /data/local/tmp/lsposed_verbose.log && chown shell:shell /data/local/tmp/lsposed_verbose.log'" \
  && adb pull /data/local/tmp/lsposed_verbose.log

# 兜底：整目录拖出
adb shell "su -c 'cp -r /data/adb/lspd/log /data/local/tmp/lspd_log && chmod -R 777 /data/local/tmp/lspd_log'"
adb pull /data/local/tmp/lspd_log
```

---

## A2. Hook 生效性验证

### A2.1 安装期验证模板

```java
// app/src/main/java/com/example/myhook/ModuleMain.java （片段）
private void installHook(@NonNull Method target) {
    try {
        HookHandle h = hook(target)
                .setId("my.module:decode")                    // @SinceApi(102)
                .setPriority(PRIORITY_DEFAULT)
                .setExceptionMode(ExceptionMode.PASSTHROUGH)  // ⚠️ 调试期务必 PASSTHROUGH
                .intercept(this::onDecode);
        log(Log.INFO, TAG, "hook installed: " + target);
    } catch (HookFailedError e) {
        log(Log.ERROR, TAG, "HookFailedError (framework bug): " + target, e);
    } catch (IllegalArgumentException e) {
        log(Log.ERROR, TAG, "invalid hook target: " + target, e);  // framework internal / Constructor#newInstance / hooker 非法
    } catch (Throwable t) {
        log(Log.ERROR, TAG, "unexpected while hooking " + target, t);
    }
}
```

⚠️ `HookFailedError` 继承 `Error`，是框架内部失败的信号——**只记录上报，不要拿它做 fallback**（`05-pitfalls.md #78`）。

### A2.2 排障第一动作：ExceptionMode

PROTECTIVE（默认）会把 hooker 抛的异常**静默吞掉**走原方法——表现就是"hook 装上了但完全没反应"。`ClassCastException`/`NoSuchMethodError` 都会被吞。

> **把目标 hook 的 `setExceptionMode(ExceptionMode.PASSTHROUGH)` 打开 → 重装 → 重启进程 → 看日志/崩溃。**

### A2.3 回调内打点

```java
private Object onDecode(@NonNull Chain chain) throws Throwable {
    log(Log.DEBUG, TAG, "enter decode, this=" + chain.getThisObject()
            + " argc=" + chain.getArgs().size()
            + " exe=" + chain.getExecutable());   // 确认 hook 到的是不是预期的那个重载
    Object r = chain.proceed();
    log(Log.DEBUG, TAG, "leave decode -> " + r);
    return r;
}
```

### A2.4 `hookClassInitializer` 特殊性【一手·源码】

把 `<clinit>` 当 `static void()` hook：`getThisObject()` 恒 null、`getArgs()` 恒空、`proceed()` 恒 null；**类已被初始化过则 hook 永不调用**。不触发 ≠ 失败，可能是"来晚了"——把 hook 提前到更早的生命周期点再验。

### A2.5 框架侧的模块加载日志【一手·源码 v1.9.x，新版措辞可能变化】

```
LSPosedContext: Loading module <pkg>
LSPosedContext:   Loading class <entryClass>
LSPosedContext: Loaded module <pkg>: <ctx>
LSPosedContext:     This class doesn't implement any sub-interface of XposedModule, skipping it
LSPosedContext:     Failed to load class <entryClass>
LSPosedContext:   Cannot load module: <pkg>
LSPosedContext:   The Xposed API classes are compiled into the module's APK.
```

**最致命的一条**：`The Xposed API classes are compiled into the module's APK.` —— 源码级判定：

```java
if (mcl.loadClass(XposedModule.class.getName()).getClassLoader() != initLoader) {
    return false;   // 模块直接不加载
}
```

成因：`api` 写成了 `implementation`（应为 `compileOnly`）。模块"完全没反应"时**先 grep 这条**。

### A2.6 管理器观察点

| 观察点 | 期望 |
|---|---|
| 「模块」列表出现 | 框架解析到 `java_init.list` |
| 模块开关 ON | 未启用 = 完全不加载 |
| 「作用域」勾选目标 | 没勾 = 不注入该进程 |
| "框架未激活"提示 | Zygisk/root 环境问题，非模块问题 |

---

## A3. 「Hook 不生效」S0-S9 标准化决策树

> 每一步都有可执行验证点，每步砍掉一半可能性。**第一步（ALIVE 信号）必须先做。**

```
START：目标进程行为没变化
│
├─ S0 基线环境
│   ├─ 最新 debug 构建？非 Magisk 用户 Zygisk 实现（NeoZygisk/ReZygisk/ZygiskNext）最新？
│   └─ 验证：adb shell "su -c 'ls /data/adb/lspd/log/'" 有内容 ⇒ daemon 起过
│   失败 ⇒ 先修框架，不往下走
│
├─ S1 模块被识别？（PC 端静态自检，无需设备）
│   ├─ unzip -l app-release.apk | grep -E 'META-INF/xposed/'
│   ├─ unzip -p app-release.apk META-INF/xposed/java_init.list   # 非空、FQCN 正确
│   ├─ unzip -p app-release.apk META-INF/xposed/module.prop      # 两个 ApiVersion 在；staticScope 小写 true
│   └─ ❌ 管理器列表没有 ⇒ 空清单回退 assets/xposed_init 判 Legacy，两者皆无 = 静默失效
│
├─ S2 模块启用？
│   └─ ❌ 没开 ⇒ 打开 → 必须重启目标进程（S4）
│
├─ S3 作用域包含目标？
│   ├─ scope.list 是否列出目标包（不 trim、不支持 # 注释）
│   ├─ staticScope=true ⇒ 用户不能加 scope 之外的应用
│   ├─ 动态申请：service.requestScope(...) → 08 册 §1
│   └─ ❌ ⇒ 勾上/写入 → 重启目标进程
│
├─ S4 目标进程重启了？（最高频根因）
│   ├─ 普通应用：adb shell am force-stop <target.pkg> 后冷启动
│   ├─ system_server/SystemUI：adb reboot（kill system_server 软重启在部分 ROM 会 bootloop【未验证】，优先 adb reboot）
│   └─ 验证：adb shell pidof <target.pkg> 前后 PID 不同
│
├─ S5 类/方法找得到？（ClassLoader）
│   ├─ 用 onPackageReady 的 getClassLoader()（不是 onPackageLoaded 的 getDefaultClassLoader()）
│   ├─ Class.forName(name, true, cl) 试探并打印结果
│   ├─ 枚举全部重载：for (Method m : cls.getDeclaredMethods()) log(...)
│   │   → 常见坑：hook 到同名不同签名的重载
│   └─ 加壳/延迟加载 ⇒ 换 hook 点或 hookClassInitializer 抢占
│
├─ S6 被内联？
│   ├─ 症状：有 HookHandle 但回调永不进
│   ├─ deoptimize(callerExecutable)——反优化【调用方】；找 caller 用 DexKit
│   ├─ 重装应用（不卸载）触发 dex2oat 重编
│   └─ 换 hook 点（hook 更靠外的方法）
│
├─ S7 优先级/其它模块干扰？
│   ├─ 链上更前的 hooker 不调 proceed() ⇒ 你的被短路
│   ├─ 临时 PRIORITY_HIGHEST 验证是否被吃掉
│   └─ 管理器里逐个禁用其它模块二分；调试器附加本身也可能使 hook 失效（v1.8.1 修复记录）
│
├─ S8 混淆（debug 正常/release 失效）？
│   ├─ 三件套齐全？（→ 02 册 §6）
│   ├─ packaging merges/excludes 两条成对？
│   └─ 参考事故：Vector v2.2 修复"发布版无 hook"（R8 合并 XResources 致 findClass 永久失败，【一手】release body）
│
└─ S9 版本不匹配？
    ├─ 指纹行里 api= 的值 < minApiVersion ⇒ 模块被拒（几乎无日志，最阴险）
    ├─ targetApiVersion ≥ 102 ⇒ 不能调 legacy API
    ├─ props 含 PROP_RT_API_PROTECTION(0x4) ⇒ 禁止反射/动态加载访问 API
    └─ ⇒ 降 minApiVersion / 升框架 / 加运行时守卫（→ 07 册 §3）
```

### 二分信号法（必须先做）

`onModuleLoaded` 里无条件 `log(Log.ERROR, TAG, "ALIVE")`：
- **ALIVE 不出现** → 问题在 S0–S4（环境/识别/启用/作用域/重启），与 hook 代码无关。
- **ALIVE 出现但回调不进** → 问题在 S5–S9（ClassLoader/内联/优先级/混淆/版本）。

其余纪律：一次只改一个变量，每次改完重启进程；debug/release 各测一遍（S8 只在 release 暴露）；官方只接受基于最新 debug 构建的问题报告。

---

## A4. adb 命令速查

### A4.1 官方验证过的命令【一手】

```bash
# 管理器通知不出来 → 把管理器装成普通应用（Vector 路径）
adb shell su -c cp /data/adb/modules/zygisk_vector/manager.apk /data/local/tmp \
  && adb pull /data/local/tmp/manager.apk && adb install ./manager.apk

# 命令行工具（管理器起不来时的兜底）——路径以 --help 实测为准
adb shell "su -c /data/adb/lspd/cli"
```

### A4.2 标准 Android 命令

```bash
# —— 安装/卸载 ——
adb install -r app-release.apk        # 覆盖安装（保留数据与作用域）
adb install -r -d app-debug.apk       # 允许降级
adb uninstall com.example.mymodule    # 卸载（清作用域最快的方式）

# —— 重启目标进程（hook 生效的必需动作）——
adb shell am force-stop com.target.app
adb shell pidof com.target.app        # 确认 PID 变了

# —— 系统级 / 日志 ——
adb reboot
adb shell ps -A | grep -iE 'lspd|zygisk|vector'
adb logcat -c
adb logcat -v time | grep -iE 'lsposed|vector|zygisk|YourModuleTag'
adb logcat -d > full.txt
```

### A4.3 ⚠️ 伪命令隔离：`lsposed_cli`

网上流传 `adb shell su -c "/data/adb/lspd/bin/lsposed_cli modules list --format json"` 等命令系列**全部【未验证】，判定为 AI 生成伪文档**：
- 官方排障指南的 CLI 路径是 **`/data/adb/lspd/cli`**（无 `/bin/`、无 `lsposed_` 前缀）；
- 伪造命令的文章含"下期预告"等 AI 生成特征，源码链接指向 gitcode 镜像。

**结论：只有 `/data/adb/lspd/cli` 路径可信；子命令以实机 `--help` 为准。**

### A4.4 安全模式

冲突二分用**管理器 UI 逐个禁用模块 + 重启**（所有版本支持）。【二手】镜像线 v2.0.4 有"SystemUI 安全模式"；社区流传 `/data/adb/lspd/debug/safe_mode` 文件触发**未验证，不要盲信**。

---

## A5. 热重载验证（API 102，详细语义 → 见 `08-service-and-advanced.md §4`）

**触发两路**：① `module.prop` `autoHotReload=true` + APK 更新自动触发；② 模块 App 主动 `service.hotReloadModule(target, extras, callback)`（需 `service.getApiVersion() >= 102`）。

```java
// 模块 App 里（拿到 XposedService 之后）
if (service.getApiVersion() >= 102) {
    for (HookedTarget target : service.getRunningTargets()) {
        service.hotReloadModule(target, null, (p, r) ->
            Log.i(TAG, "reload " + p.getProcessName() + " -> " + r.status() + " msg=" + r.message()));
    }
}
```

`HotReloadResult.Status` 处置表：

| Status | 含义 | 处置 |
|---|---|---|
| `SUCCEEDED` | 成功 | 验证 `onHotReloaded` 是否被调、旧 hook 是否正确替换 |
| `FAILED` | `message==null`：旧代码 `onHotReloading` 返回 false；`message!=null`：重载异常诊断串 | 查返回值 / 看 message |
| `UNSUPPORTED` | 无恰好一个 Java 入口类 / 框架拿不到有效新代 | 查 `java_init.list` 是否只有一行 |
| `IN_PROGRESS` | 该目标正在重载（串行） | 稍后再试，不并发触发 |
| `PROCESS_DIED` | 目标进程在完成前死了 | 正常，重启再来 |

**验证生效检查清单**：
- [ ] `autoHotReload=true` 或代码触发；`service.getApiVersion() >= 102`
- [ ] `java_init.list` 恰好一个入口类
- [ ] `onHotReloading` 返回 `true`（默认 false = 永远拒绝），且未放旧 CL 对象进 `setSavedInstanceState`
- [ ] `onHotReloaded` 里对旧 handles 做了 `replaceHook`（默认实现只 unhook = 功能消失）
- [ ] 日志看到新代码输出，且**进程 PID 未变**；回调 `SUCCEEDED`

四条硬约束：① 生命周期回调不会自动重放；② 旧 hook 默认被卸载；③ hook 链快照式（不影响飞行中调用）；④ 框架不调 `UnregisterNatives`/`JNI_OnUnload`/`dlclose`，native 残留崩溃 = 模块 bug。

---

## 未验证 / 未获取到

- API 102 实现 `log()` 打到 logcat 时 tag 是否原样/加前缀：未取证（Vector 的 `LSPosedContext` 对应实现未取到）。
- 管理器 UI 的日志页结构（Verbose/Modules 分 tab 等）在各版本间变动大，未验证。
- kill system_server 软重启在哪些 ROM 会 bootloop：未验证，统一建议 `adb reboot`。
- `/data/adb/lspd/cli` 的具体子命令集：未取证，以实机 `--help` 为准；`lsposed_cli <subcmd>` 系列已隔离为伪文档。
- 安全模式的 `/data/adb/lspd/debug/safe_mode` 文件触发方式：社区传闻，未验证。
- v1.9.x `LSPosedContext` 日志措辞在 API 101/102 实现中是否一致：未验证（判定逻辑本身有源码级证据）。
