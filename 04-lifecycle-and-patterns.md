# 04 · 生命周期流程与 Hook 模式代码库

> **本册定位**：生命周期回调的**触发顺序、各回调内的操作边界**，以及一套可直接抄写的 Hook 模式代码库（最小骨架 / 生产级骨架 / Kotlin 差异 / 进程过滤 / detach 用法）。
> **何时读这一册**：写模块入口类之前、hook 装错位置不生效时、需要 decide"这段逻辑放哪个回调"时。
> **前置依赖**：API 语义 → 见 `03-api-reference.md §10-§13`；工程配置 → 见 `02-project-scaffold.md`。
> **交叉引用**：→ 见 `03-api-reference.md §12-§13`、`05-pitfalls.md §B.1-§B.2`、`08-service-and-advanced.md §4`（热重载）、`06-debugging-runbook.md`（不生效排障）

---

**基准**：API 102。全部生命周期事实为【一手·源码】（`XposedModuleInterface.java` / `XposedModule.java` / `package-info.java`），模式代码改编自【一手·源码】`libxposed/example` 官方示例。

---

## 1. 生命周期总图

### 1.1 普通 App 进程

```
[框架在 zygote fork 后实例化入口类]      ← 反射调用 public 无参构造器
        │
        ▼
  attachFramework(base, detachImpl)     ← @InternalApi，框架自动调用，模块禁止调用
        │
        ▼
  onModuleLoaded(ModuleLoadedParam)     ← 每个模块代加载时调用一次；热重载不重放
        │   可用：log / 能力探测。不可用：任何 ClassLoader（还没有包被加载）
        ▼
  onPackageLoaded(PackageLoadedParam)   ← @RequiresApi(Q=29)
        │   时机：默认 classloader 就绪后、AppComponentFactory 实例化之前
        │   可用：param.getDefaultClassLoader()（无 getClassLoader()）
        │   ⚠️ Android < 29 不会收到此回调
        ▼
  onPackageReady(PackageReadyParam)     ← ★ 主 Hook 安装点
        │   时机：AppComponentFactory 造好真实 classloader、准备创建 Application 时
        │   可用：param.getClassLoader() / param.getAppComponentFactory()（P=28+）
        ▼
  （应用继续启动，hook 生效）
```

### 1.2 system_server 进程

```
onModuleLoaded
        ▼
onSystemServerStarting(SystemServerStartingParam)   ← 取代首轮回调；param.getClassLoader() 可用
        ▼
onPackageLoaded / onPackageReady（后续包照常触发）
```

- system_server 中 `isFirstPackage()` **恒为 false** —— 不要用它判断"是否首次"。
- 想在 system_server hook：走 `onSystemServerStarting`。
- hook `system_server` 的前提：`getFrameworkProperties() & PROP_CAP_SYSTEM != 0`【一手·源码】（`PROP_CAP_SYSTEM=1L`）。

### 1.3 热重载（API 102，详细语义 → 见 `08-service-and-advanced.md §4`）

> **前提**（【一手·PR libxposed/api#62】）：热重载仅支持**恰好声明一个 Java 入口类**的模块。多入口类模块的 `onHotReloading` 不会被调用。

```
onHotReloading(HotReloadingParam)     ← 运行在【旧代码】；返回 true 放行 / false 拒绝（默认 false）
    │
    ├── 可调用：param.setSavedInstanceState(Object) 传递跨代状态
    │   ⚠️ 状态对象必须是 ClassLoader 无关的（如 Bundle、Map<String, String>、基本类型数组）
    │   ⚠️ 框架会检测并拒绝旧模块 ClassLoader 创建的对象，抛出 IllegalArgumentException
    │   ⚠️ Native 模块必须在此回调中清理旧 native 状态（JNI_OnUnload 不是热重载回调）
    │
    ▼
（框架加载新代码，实例化新代入口类）
        ▼
onHotReloaded(HotReloadedParam)       ← 运行在【新代码】
    │
    ├── param.getExtras()             ← 获取 setSavedInstanceState 传递的状态
    ├── param.getOldHookHandles()     ← 获取旧代码安装的所有 HookHandle
    │   默认实现 = forEach(HookHandle::unhook)
    │   ⚠️ 不重写 onHotReloaded = 旧 hook 残留 + 新 hook 未装 = 模块静默失效
    │
    └── 包生命周期回调（onPackageReady 等）**不会重放** → 必须在此手动重新安装 hook
```

**关键约束**（【一手·PR libxposed/api#62】）：
1. **ClassLoader 隔离**：`setSavedInstanceState` 传递的对象不能是由旧模块 ClassLoader 加载的类实例，否则框架会检测并抛 `IllegalArgumentException`。正确做法是只传递 `Bundle`、`Map<String, Object>`（值为基础类型）或基本类型数组。
2. **Native 状态清理**：使用 native hook 的模块必须在 `onHotReloading` 中手动清理旧 native 状态（如取消 Dobby/LSPlant hook、释放 native 内存）。`JNI_OnUnload` 在热重载时**不会**被调用。
3. **框架可能拒绝热重载**：即使 `onHotReloading` 返回 `true`，框架仍可能因无法提供有效的新代模块而拒绝（如进程正在退出、内存不足）。模块不应假设热重载一定成功。

---

## 2. 各回调操作边界矩阵

| 操作 | onModuleLoaded | onPackageLoaded | onPackageReady | onSystemServerStarting |
|---|---|---|---|---|
| `log(...)` / 能力探测 | ✅ | ✅ | ✅ | ✅ |
| 装 Hook | ❌ | ❌ | ✅ | ✅ |
| `param.getClassLoader()` | — | ❌（不存在，只有 `getDefaultClassLoader()`） | ✅ | ✅ |
| `param.getDefaultClassLoader()` | — | ✅ | ✅（继承） | — |
| `Class.forName(name, true, cl)` 找宿主类 | ❌ | ⚠️ 可用但类可能尚未加载完毕 | ✅ 推荐 | ✅ |
| `hook(...)` | ❌ | ❌ | ✅ | ✅ |
| `deoptimize(...)` | ❌ | ❌ | ✅ | ✅ |
| 触发一次 / 每包触发 | 每代一次 | 每包一次 | 每包一次 | system_server 一次 |

【一手·源码】依据：`package-info.java`（"Modules should not perform initialization before `onModuleLoaded()` is called"）；`PackageLoadedParam.getDefaultClassLoader()` 带 `@RequiresApi(Q)`；`PackageReadyParam.getClassLoader()` Javadoc 明确"真实 classloader，若包有自定义 AppComponentFactory 则与默认不同"。

**未 attach 就调用任何 `XposedInterface` 方法** → `IllegalStateException("Framework not attached")`。所以：**构造器和字段初始化器里禁止调用 `getXxx()` / `hook()`**【一手·源码】（`XposedInterfaceWrapper.ensureAttached()`）。

---

## 3. 入口类硬规则

1. `public final class ModuleMain extends XposedModule`——必须 public、必须提供**公开无参构造器**（写了带参构造器就必须再写无参的）。【一手·源码】`XposedModule` 不声明任何构造器，框架反射实例化。
2. 入口类**每个模块代在每个进程中实例化一次**；热重载产生新代 = 新实例。
3. 禁止在构造器 / 字段初始化器 / `onModuleLoaded` 之前做任何初始化。
4. `java_init.list` 每行一个 FQCN，支持多入口；但热重载要求**恰好一个** Java 入口类，生产模块建议只写一行。【一手·源码】`XposedService.hotReloadModule` Javadoc（`UNSUPPORTED`：模块未声明恰好一个 Java 入口类）。
5. `detach()`（@SinceApi 102）：让**当前入口实例**停止接收后续生命周期回调；API 能力依然可用；幂等。典型用法：多入口模块里"非目标 App 的入口立即 detach"。

---

## 4. 进程 / 包过滤模式

作用域（`scope.list` + 管理器勾选）只决定"**模块被注入哪些进程**"，**不决定"回调只来自哪些包"**——同一进程可能加载多个包，回调会超出 scope 范围。【一手·源码】`PackageLoadedParam` Javadoc："the callbacks may not be limited to the package"。

```java
// app/src/main/java/com/example/myhook/ModuleMain.java （片段）
@Override
public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
    if (!TARGET_PACKAGE.equals(param.getPackageName())) return;  // ① 包名过滤
    if (!param.isFirstPackage()) return;                         // ② 首包过滤：同进程多包只装一次
    installAppHooks(param.getClassLoader());
}
```

| 场景 | 正确做法 |
|---|---|
| 只 hook 某一个包 | `param.getPackageName()` 精确匹配 |
| 只 hook 进程主包 | `param.isFirstPackage()` |
| 想同时 hook 多个 App | 按 `getPackageName()` 分发表；或一个 App 一个专用入口类 + 不匹配即 `detach()` |
| `system_server` | 不走 `isFirstPackage()`（恒 false）；在 `onSystemServerStarting` 装 hook |
| 只要某个子进程 | `param.getProcessName()` 匹配（`PackageReadyParam` 继承自 `ModuleLoadedParam`） |

---

## 5. 模式代码库

### 5.1 最小可运行骨架（4 个声明文件 + 1 个 Java 类）

```java
// app/src/main/java/com/example/mymodule/ModuleMain.java
package com.example.mymodule;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static final String TAG = "MyModule";
    private static final String TARGET_PACKAGE = "com.android.settings";

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;
        try {
            // ⚠️ 必须三参 Class.forName：两参版本用模块自己的 ClassLoader，一定找不到宿主类
            Class<?> target = Class.forName(
                    "com.android.settings.fuelgauge.BatteryMeterView",
                    true,
                    param.getClassLoader());
            Method m = target.getDeclaredMethod("updatePercentText");

            hook(m)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)          // = 50
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId("settings:battery:updatePercentText")
                    .intercept(chain -> {
                        Object result = chain.proceed();   // 不调 proceed() = 原方法不执行
                        log(Log.DEBUG, TAG, "hooked, result=" + result);
                        return result;                     // 不 return 结果 = 丢弃返回值
                    });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install hook failed", t);
        }
    }
}
```

配套声明文件（完整内容 → 见 `02-project-scaffold.md §4` 与 `templates/`）：

```properties
# app/src/main/resources/META-INF/xposed/module.prop
minApiVersion=102
targetApiVersion=102
staticScope=true
```

```
# app/src/main/resources/META-INF/xposed/java_init.list
com.example.mymodule.ModuleMain
```

```
# app/src/main/resources/META-INF/xposed/scope.list
com.android.settings
```

### 5.2 生产级骨架（过滤 / 去优化 / 统一注册 / 热重载 / 远程配置）

```java
// app/src/main/java/com/example/mymodule/ModuleMain.java （生产级）
package com.example.mymodule;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static final String TAG = "MyModule";
    private static final String TARGET_PACKAGE = "com.android.settings";

    /** hook id -> 该 id 对应的 Hooker。热重载时原子替换用。 */
    private final Map<String, XposedInterface.Hooker> mHookers = new HashMap<>();

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        // 只做日志 + 能力探测；此处没有任何 ClassLoader，禁止 hook
        log(Log.INFO, TAG, "loaded in " + param.getProcessName()
                + ", api=" + getApiVersion()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + ", props=0x" + Long.toHexString(getFrameworkProperties()));
    }

    @Override
    public void onSystemServerStarting(@NonNull XposedModuleInterface.SystemServerStartingParam param) {
        // system_server 专用入口；isFirstPackage() 恒为 false，别用它判断
        if ((getFrameworkProperties() & PROP_CAP_SYSTEM) == 0) return;
        installSystemHooks(param.getClassLoader());
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;
        installAppHooks(param.getClassLoader());
    }

    private void installAppHooks(@NonNull ClassLoader cl) {
        try {
            Class<?> c = Class.forName("com.android.settings.SomeTarget", true, cl);
            Method m = c.getDeclaredMethod("targetMethod", String.class);

            // 内联兜底：去优化【调用方】（deoptimize 的对象是 caller，不是被 hook 的方法本身！）
            deoptimizeCallers(cl,
                    "com.android.settings.SomeCallerA", "callSiteA",
                    "com.android.settings.SomeCallerB", "callSiteB");

            register("settings:targetMethod", m, chain -> {
                String arg0 = (String) chain.getArg(0);            // getArgs() 是不可变 List
                Object result = chain.proceed(new Object[]{"replaced"});  // 改参数 = 传新数组给 proceed
                return "wrapped:" + result;                        // 改返回值
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "installAppHooks failed", t);
        }
    }

    private void installSystemHooks(@NonNull ClassLoader cl) { /* 同 installAppHooks 模式 */ }

    /** 统一注册：始终带 id（重复注册自动原子替换）+ 显式优先级 + 显式异常模式 */
    private void register(@NonNull String id, @NonNull Executable exec,
                          @NonNull XposedInterface.Hooker hooker) {
        mHookers.put(id, hooker);
        hook(exec)
                .setId(id)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
    }

    private void deoptimizeCallers(@NonNull ClassLoader cl, @NonNull String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            try {
                Class<?> c = Class.forName(pairs[i], true, cl);
                deoptimize(c.getDeclaredMethod(pairs[i + 1]));
            } catch (Throwable t) {
                log(Log.WARN, TAG, "deoptimize failed: " + pairs[i] + "#" + pairs[i + 1], t);
            }
        }
    }

    // ---------- 热重载（API 102） ----------

    @Override
    public boolean onHotReloading(@NonNull XposedModuleInterface.HotReloadingParam param) {
        // 返回 true 前必须：停掉模块自有线程、注销 native hook / 外部回调、
        // 释放 JNI 全局引用、清掉系统或 App 类持有的模块对象引用。
        // 默认实现返回 false —— 不重写 = 热重载永远被拒绝。
        param.setSavedInstanceState(null);   // ⚠️ 只能放 classloader 中立的值，放旧代对象会 IllegalArgumentException
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        // 生命周期回调不会重放 → 必须自己把旧 hook 换成新代码
        for (XposedInterface.HookHandle old : param.getOldHookHandles()) {
            String id = old.getId();
            XposedInterface.Hooker fresh = id == null ? null : mHookers.get(id);
            try {
                if (fresh != null) {
                    old.replaceHook(fresh);   // 保留 priority/exceptionMode/id 原子替换；old 此后失效
                } else {
                    old.unhook();
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "replace hook failed: " + id, t);
                old.unhook();
            }
        }
    }

    // ---------- 远程配置（宿主进程内只读） ----------

    @Nullable
    private String readConfig(@NonNull String group, @NonNull String key) {
        if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) return null;
        try {
            return getRemotePreferences(group).getString(key, null);
        } catch (UnsupportedOperationException e) {
            return null;   // embedded 框架不支持 remote 能力
        }
    }
}
```

**这套骨架编码的 6 条模式**（生成代码时保持一致）：

| # | 模式 | 原因 |
|---|---|---|
| 1 | hook 安装块整体 `try/catch (Throwable)` | 找不到类/方法时只记日志，不让异常扩散；回调异常被框架记录（`ExceptionMode.PROTECTIVE` 下被吞 → 见 §6） |
| 2 | 一律 `setId(...)` | 重复注册同 id 自动原子替换；热重载 `replaceHook` 靠 id 关联 |
| 3 | 显式 `setPriority` / `setExceptionMode`，不用默认值猜测 | `PRIORITY_DEFAULT=50`；默认异常模式来自 `module.prop` 的 `exceptionMode`（默认 protective），被吞异常会让"hook 没生效"假象 |
| 4 | `deoptimize` 只对**调用方** | Javadoc：deoptimize 的是"调用该方法的 caller" → 见 `03-api-reference.md §6` |
| 5 | 远程配置先探测 `PROP_CAP_REMOTE` 且每会话重读 | `PROP_RT_*` 每次启动可能变化，禁止缓存到磁盘 |
| 6 | 热重载 `onHotReloaded` 按 id `replaceHook`，失败的 `unhook` | 默认实现会 unhook 全部旧 hook 且不重放 `onPackageReady` |

### 5.3 Kotlin 差异清单

```kotlin
// app/src/main/java/com/example/mymodule/ModuleMain.kt （关键差异片段）
class ModuleMain : XposedModule() {
    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET) return       // getXxx() → 合成属性
        if (!param.isFirstPackage) return
        runCatching { install(param.classLoader) }
            .onFailure { log(Log.ERROR, TAG, "install failed", it) }
    }

    private fun install(cl: ClassLoader) {
        val m = Class.forName("com.android.settings.SomeTarget", true, cl)
            .getDeclaredMethod("targetMethod", String::class.java)

        hook(m).setId("settings:targetMethod").intercept { chain ->
            val arg0 = chain.args[0] as String        // chain.args 同样是不可变 List
            // ⚠️ proceed 形参是 Object[]（Kotlin 视角 Array<Any!>），
            //    arrayOf("x") 会被推断为 Array<String> → 编译不过，必须 arrayOf<Any>("x")
            val newArgs = arrayOf<Any>("replaced")
            val result = chain.proceed(newArgs)
            "wrapped:$result"
            // 目标方法返回 void：不写返回值即可（Unit → 框架按 null 处理，等价 Java 的 return null）
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        param.savedInstanceState = null
        return true
    }
}
```

| 差异点 | Java | Kotlin |
|---|---|---|
| `chain.getArgs()` | 方法调用 | `chain.args` 合成属性（仍不可变） |
| void 方法 | lambda 必须 `return null`（官方注释："for void methods, it's needed to return null because Java doesn't support unit type"） | 无需显式 return |
| 改参数数组 | `new Object[]{...}` | 必须 `arrayOf<Any>(...)` |
| 官方 Kotlin 示例无 try/catch 包裹 | — | 生产代码请自己加（按 Java 版习惯写） |

---

## 6. 异常模式与回调打点（与验证的关系）

| ExceptionMode | 回调抛异常时 | 用途 |
|---|---|---|
| `PROTECTIVE`（默认） | 框架吞掉异常，仅记录日志，原方法照常执行 | 生产默认。⚠️ 代价：bug 被吞 → 表现为"hook 没生效" → 排障时先怀疑它 |
| `PASSTHROUGH` | 异常**原样抛给宿主调用方** | 需要让宿主感知失败（如官方示例的构造器 hook 演示） |
| `module.prop` 的 `exceptionMode` | 决定 `ExceptionMode.DEFAULT` 的含义 | 全局开关，逐 hook 可覆盖 |

验证 hook 是否真的执行，**必须在回调体内打点**（`log(Log.DEBUG, ...)`)——只在安装处打日志无法区分"装上了但没触发"与"装失败了"。完整验证方法论 → 见 `06-debugging-runbook.md §A2`。

---

## 未验证 / 未获取到

- `onPackageLoaded` 在 Android < 29 是否整体不分发：`getDefaultClassLoader()` 带 `@RequiresApi(Q)` 是源码明文，但"整个回调不分发"是【推断】（官方未明文写回调分发条件）。
- `onSystemServerStarting` 与后续包的 `onPackageReady` 在 system_server 中的**精确交错顺序**（先 starting 后其他包 ready 的断言来自回调语义，未逐帧实测）。
- `detach()` 之后已安装 hook 是否继续生效：Javadoc 只说"API 依然完全可用、不再收生命周期回调"，未明说 hook 状态（【推断】hook 保留，因为 hook 归属框架而非入口实例）。
- `HotReloadingParam.getExtras()` 在 App 更新自动触发时恒为 null 的官方明文未复核（源码注释为"App 传 null 或由 App 更新触发时为 null"）。
