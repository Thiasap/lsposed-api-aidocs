# 03 · libxposed API 102 完整 API 参考

> **本册定位**：`io.github.libxposed:api:102.0.0` 的权威 API 参考。每个类型给出类声明 / 常量 / 方法签名 / 参数语义 / 返回值 / 异常 / 使用注意 / 可复制示例。
> **何时读这一册**：需要确认某个方法**到底怎么用**、参数是什么意思、会抛什么异常时；速查不够用时。
> **前置依赖**：无。若只需查签名 → 见 `01-quick-reference.md`。
> **交叉引用**：→ 见 `01-quick-reference.md`、`02-project-scaffold.md`（工程搭建）、`04-lifecycle-and-patterns.md`（生命周期模式）、`05-pitfalls.md`（坑点）、`08-service-and-advanced.md`（XposedService / 热重载 / Native Hook）

**基准**：`io.github.libxposed:api:102.0.0`（tag `102.0.0`，commit `45e7c5c`，2026-06-13）。
**可信度标注**：`【一手·源码】` = 直接读 libxposed 仓库 `.java` 源码 / Javadoc；`【一手·文档】` = 官方 Wiki / README；`【二手】` = 镜像站 / 第三方；`【推断】` = 无直接来源，由编写者推导。

> **三条不可违背的基准**（贯穿全册）：
> 1. **只写 API 102**。API 100/101 的废弃模型不作为可选写法介绍，仅在 §15 用「⚠️ 已移除 / 已变更」点名，目的是防止 Agent 被旧教程误导。
> 2. **不绑定发行版**。禁止 `if (frameworkVersion.equals("2.2.0"))`，一切环境判断走 `getApiVersion()` / `getFrameworkProperties()`。
> 3. **读者是 AI Agent**。每条结论必须是可复制代码 / 可执行检查 / 可判定规则之一。

---

## 1. 类型总览

### 1.1 `io.github.libxposed.api`（工件 `io.github.libxposed:api:102.0.0`，`compileOnly`）

| 类型 | 种类 | 说明 | 可信度 |
|---|---|---|---|
| `XposedInterface` | interface | **核心门面**：框架能力入口 + 全部 Hook 模型嵌套类型 | 【一手·源码】 |
| `XposedInterfaceWrapper` | class | `XposedInterface` 的默认包装实现，持有框架注入的 `mBase` 并屏蔽框架细节；提供 `detach()` | 【一手·源码】 |
| `XposedModule` | abstract class（**类体为空**） | 模块入口基类：`extends XposedInterfaceWrapper implements XposedModuleInterface` | 【一手·源码】 |
| `XposedModuleInterface` | interface | 生命周期回调 + `LoadedParam` 系列定义（**纯生命周期，不委托任何东西**） | 【一手·源码】 |

目录树（`libxposed/api` @master）证明**不存在** native 相关类型、也**不存在** `io.github.libxposed.api.annotations` 子包：

```
io/github/libxposed/api/
├── XposedInterface.java
├── XposedInterfaceWrapper.java
├── XposedModule.java
├── XposedModuleInterface.java
├── package-info.java
└── error/                 ← 仅两个异常类
```
【一手·源码】

`XposedInterface` 的**嵌套类型**（7 个 L1，其中 `Invoker` 内部另有 3 个）：

| 嵌套类型 | 种类 | 层级 |
|---|---|---|
| `Invoker<T extends Invoker<T,U>, U extends Executable>` | interface | L1 |
| ↳ `Invoker.Type` | `sealed interface`（`permits Type.Origin, Type.Chain`） | L2 |
| ↳ `Invoker.Type.Origin` | `record` | L3 |
| ↳ `Invoker.Type.Chain` | `record(int maxPriority)`，含 `static final Chain FULL` | L3 |
| `CtorInvoker<T>` | interface `extends Invoker<CtorInvoker<T>, Constructor<T>>` | L1 |
| `Chain` | interface | L1 |
| `Hooker` | interface（SAM：`Object intercept(Chain)`） | L1 |
| `HookHandle` | interface | L1 |
| `ExceptionMode` | enum：`DEFAULT` / `PROTECTIVE` / `PASSTHROUGH` | L1 |
| `HookBuilder` | interface | L1 |

### 1.2 `io.github.libxposed.api.error`

| 类型 | 基类 | 说明 |
|---|---|---|
| `XposedFrameworkError` | `java.lang.Error` | 框架功能损坏 |
| `HookFailedError` | `XposedFrameworkError` | Hook 因框架内部错误失败 |

> ⚠️ **包名陷阱**：API 100 用 `io.github.libxposed.api.errors`（**复数**）；**101+ 改为单数 `error`**。 → §3.4

### 1.3 `io.github.libxposed.annotation`（**在 `libxposed/lint` 仓库，不在 api 仓库**）

只有两个注解：`@SinceApi(int value)`、`@InternalApi`。**不存在 `@XposedHooker`**，也不存在 `io.github.libxposed.api.annotations.*`。 → §14

### 1.4 `io.github.libxposed.service`

`XposedService` / `XposedServiceHelper` / `HookedTarget` / `HotReloadResult` / `RemotePreferences` / `XposedProvider`。完整参考 → 见 `08-service-and-advanced.md §1`。

---

## 2. 常量

### 2.1 API 版本常量

| 常量 | 类型 | 值 | 语义 |
|---|---|---|---|
| `API_101` | `int` | `101` | API 版本 101 |
| `API_102` | `int` | `102` | API 版本 102 |
| `LIB_API` | `int` | `API_102`（=102） | **编译期**库版本 |

【一手·源码】

**唯一正确的版本判断写法**（可判定规则）：

```java
// app/src/main/java/com/example/ModuleMain.java
if (getApiVersion() >= XposedInterface.API_102) {
    hook(m).setId("my-hook");        // @SinceApi(102) 的 API
}
```

```java
// ❌ 禁止：把 LIB_API 当运行时版本用，或比较框架版本字符串
if (XposedInterface.LIB_API >= 102) { ... }              // 编译期常量，恒真，无意义
if (getFrameworkVersion().equals("2.2.0")) { ... }       // 禁止：绑定发行版
```

> ⚠️ `XposedInterface.API_102` 与 `XposedService.API_102` 是**两个不同类里的不同常量**，语义独立（前者 = libxposed 模块 API 版本，后者 = service IPC 版本）。不要交叉使用。
> ⚠️ 两条 LSPosed 2.x 版本号互相冲突的线：**Vector 线**（`JingMatrix/Vector`，v2.0=API 100 / v2.1=API 101 / v2.2=API 102）与 **lsposed.cn 镜像线**（v2.0.x~v2.1.1，其中 v2.1.0 起实现 API 102）。镜像线的 "2.1" ≠ Vector 的 "2.1"（后者是 API 101）。这正是禁止比较版本字符串的现实原因。【一手·源码】+【二手】

### 2.2 优先级常量

| 常量 | 值 | 语义 |
|---|---|---|
| `PRIORITY_DEFAULT` | `50` | 默认优先级 |
| `PRIORITY_LOWEST` | `Integer.MIN_VALUE` | 链尾执行 |
| `PRIORITY_HIGHEST` | `Integer.MAX_VALUE` | 链首执行 |

【一手·源码】

- 优先级**越高越先执行**；高优先级先 `proceed()`，把控制交给下一环；链尾是原方法；返回值沿链**反向**传回。
- 想"比默认早一点"：`PRIORITY_DEFAULT + 10`。**不要**硬编码 `100`。
- 同优先级的多个 hook 之间顺序**未定义**【推断，Javadoc 只保证 higher-before-lower，未规定 tie-break】。不要依赖与其它模块的相对顺序。

> ⚠️ API 100 的 `PRIORITY_LOWEST/HIGHEST` 是 `±10000`。101+ 已改。 → §15

### 2.3 框架属性位掩码（`getFrameworkProperties()`）

| 常量 | 值 | 语义 | 未置位时的影响 |
|---|---|---|---|
| `PROP_CAP_SYSTEM` | `1L` | 框架可 hook `system_server` 等系统进程 | 系统级 hook 不可用 |
| `PROP_CAP_REMOTE` | `1L << 1` | 框架支持 remote preferences / remote files | 远程三方法抛 `UnsupportedOperationException` |
| `PROP_RT_API_PROTECTION` | `1L << 2` | 框架禁止通过**反射或动态加载代码**访问 Xposed API | — （是警告信号，不 gate 任何方法） |

【一手·源码】

```java
// app/src/main/java/com/example/ModuleMain.java
private boolean hasProp(long prop) {
    return (getFrameworkProperties() & prop) != 0;
}

@Override
public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    log(Log.INFO, TAG, "framework: " + getFrameworkName()
            + " (" + getFrameworkVersionCode() + ") API " + getApiVersion());
    log(Log.INFO, TAG, "system supported: " + hasProp(PROP_CAP_SYSTEM));
    log(Log.INFO, TAG, "remote supported: " + hasProp(PROP_CAP_REMOTE));
    log(Log.INFO, TAG, "api protection:   " + hasProp(PROP_RT_API_PROTECTION));
}
```

**可判定规则**：
- **必须**：`PROP_RT_*` 前缀的属性**每次启动都可能变化**（Javadoc 原文："Properties with prefix `PROP_RT_` may change among launches."）→ **每会话重读，禁止持久化缓存**。【一手·源码】
- **必须**：模块**静态引用** libxposed API。用反射调 `XposedInterface` 方法、或把模块代码放进动态加载的 dex 再调 API，在 `PROP_RT_API_PROTECTION` 下会被拒。
- **禁止**：用 `getFrameworkVersion()` 字符串解析来推断能力。

### 2.4 `ExceptionMode`

| 枚举 | 行为（Javadoc 原文要点） | 何时用 |
|---|---|---|
| `DEFAULT` | 遵循 `module.prop` 的 `exceptionMode`（`protective` / `passthrough`）；未配置时等同 `PROTECTIVE` | 不显式设置时的默认。**推荐保持** |
| `PROTECTIVE` | hooker 抛的异常被**捕获并记日志**，调用"像没有该 hook 一样"继续。在 `proceed()` **之前**抛 → 链跳过该 hook 继续；在 `proceed()` **之后**抛 → 返回已 proceed 的值/异常作为结果。**`proceed()` 抛出的异常始终向上传播** | 生产环境默认（官方：recommended for most cases） |
| `PASSTHROUGH` | hooker 的异常照常传播给调用方 | **仅调试期**（官方：recommended for debugging purposes） |

【一手·源码】

> ⚠️ PROTECTIVE 的典型副作用：你的逻辑有 bug → 被吞掉 → **App 不崩但功能静默失效**。开发期请在 `module.prop` 写 `exceptionMode=passthrough`，或单 hook `.setExceptionMode(ExceptionMode.PASSTHROUGH)`，发布前改回。
> ⚠️ PROTECTIVE **不兜** `proceed()` 的异常。要兜自己 `try { chain.proceed(); } catch (Throwable t) { ... }`。

---

## 3. `XposedInterface` 顶层方法（16 个）

### 3.1 总表

| # | 方法 | 分类 |
|---|---|---|
| 1–5 | `getApiVersion()` / `getFrameworkName()` / `getFrameworkVersion()` / `getFrameworkVersionCode()` / `getFrameworkProperties()` | 框架信息（§3.2） |
| 6 | `getModuleApplicationInfo()` | 模块自身信息（§3.2） |
| 7–8 | `hook(Executable)` / `hookClassInitializer(Class<?>)` | Hook 安装（§3.3） |
| 9–10 | `getInvoker(Method)` / `getInvoker(Constructor<T>)` | 原方法调用（§5） |
| 11 | `deoptimize(Executable)` | 去优化（§6） |
| 12–13 | `log(int,String,String)` / `log(int,String,String,Throwable)` | 日志（§7） |
| 14–16 | `getRemotePreferences(String)` / `listRemoteFiles()` / `openRemoteFile(String)` | 远程数据（§8） |

### 3.2 框架信息类方法

```java
default int getApiVersion()
```
- **返回**：**运行时** Xposed API 版本（101 / 102）。
- **注意**：接口里是 `default` 方法，返回编译期 `LIB_API`。框架实现**不得**重写它 —— 这也是它能作为"运行时实际 API 版本"的原因。【一手·源码】
- **这是能力探测的唯一正解。**

```java
@NonNull String getFrameworkName()
@NonNull String getFrameworkVersion()
long getFrameworkVersionCode()
long getFrameworkProperties()
@NonNull ApplicationInfo getModuleApplicationInfo()
```

| 方法 | 返回什么 | 注意 |
|---|---|---|
| `getFrameworkName()` | 框架名，如 `LSPosed` / `Vector` / `LSPatch` | 只用于展示 |
| `getFrameworkVersion()` | 框架版本字符串 | **禁止解析**来做能力判断 |
| `getFrameworkVersionCode()` | 框架版本号（数值） | 只用于展示 |
| `getFrameworkProperties()` | 能力位掩码 | → §2.3 |
| `getModuleApplicationInfo()` | **模块自己**的 `ApplicationInfo`（**不是宿主的**）。`.sourceDir` = 模块 APK 路径 | 写入模块自己的数据目录**没用**（宿主进程读到的是自己的沙盒）。跨进程共享只能用 Remote Files |

【一手·源码】

### 3.3 Hook 安装

#### `hook(Executable)` → `HookBuilder`

```java
@NonNull
HookBuilder hook(@NonNull Executable origin);
```

- **参数**：`origin` — 要 hook 的 `Method` 或 `Constructor`。
- **返回**：`HookBuilder`。**不是 `HookHandle`** —— 必须再调 `.intercept(hooker)` 才真正安装（→ §4.1）。
- **唯一重载**：API 102 中 `hook` **只有这一个签名**，没有 `Class<? extends Hooker>` 重载，也没有 `hookBefore` / `hookAfter` / `hookAllMethods`。
- **异常**（实际在 `HookBuilder.intercept()` 时抛出）：
  - `IllegalArgumentException` —— `origin` 是框架内部方法，或是 **`Constructor.newInstance`**；或 hooker 非法
  - `HookFailedError` —— 框架内部错误（→ §3.4）

【一手·源码】

> ⚠️ **不能 hook `Constructor.newInstance`**。要拦截对象创建 → hook **具体的构造器**（`clazz.getDeclaredConstructor(types)`）。

#### `hookClassInitializer(Class<?>)`

```java
@NonNull
HookBuilder hookClassInitializer(@NonNull Class<?> origin);
```

Hook 类的静态初始化器 `<clinit>`，被当作 `static void()` 处理：

| `Chain` 方法 | 在 `<clinit>` hook 中的行为 |
|---|---|
| `getExecutable()` | 返回一个**合成的** `Method`（代表静态初始化器） |
| `getThisObject()` | 恒为 `null` |
| `getArgs()` | 空 list |
| `proceed()` | 返回 `null` |

【一手·源码】

```java
// app/src/main/java/com/example/ModuleMain.java —— 类初始化前切入
@Override
public void onPackageReady(@NonNull PackageReadyParam param) {
    try {
        Class<?> c = Class.forName("com.example.target.LazyInit", true, param.getClassLoader());
        hookClassInitializer(c).setId("lazy-init").intercept(chain -> {
            log(Log.INFO, TAG, "<clinit> of LazyInit is about to run");
            return chain.proceed();     // 恒返回 null
        });
    } catch (Throwable t) {
        log(Log.ERROR, TAG, "hook clinit failed", t);
    }
}
```

> ⚠️ **可判定规则**：**若类已经初始化完成，该 hook 永远不会被调用**（Javadoc 原文）。必须赶在类加载前安装 —— 通常放在 `onPackageReady` 的**最早**处，或用更早的锚点。

### 3.4 Hook 相关异常：`io.github.libxposed.api.error`

```java
package io.github.libxposed.api.error;

/** 框架功能损坏。 */
public class XposedFrameworkError extends Error { ... }

/**
 * This inherits from Error rather than RuntimeException because hook failures are considered
 * fatal framework bugs. Module developers should not attempt to catch this error to provide
 * fallbacks. Instead, please report the issue to the framework maintainers so it can be fixed
 * at the root.
 */
public class HookFailedError extends XposedFrameworkError { ... }
```

【一手·源码】

| 类型 | 基类 | 抛出场景 |
|---|---|---|
| `XposedFrameworkError` | `java.lang.Error` | 框架功能损坏 |
| `HookFailedError` | `XposedFrameworkError` | Hook 因框架内部错误失败（`HookBuilder.intercept()` / `HookHandle.replaceHook()`） |

**可判定规则**：

1. ❌ **不要 catch `HookFailedError` 来做降级** —— 它是 `Error` 不是 `RuntimeException`，设计意图是"框架致命 bug，应上报框架维护者"。
2. ✅ 若你写了 `catch (Throwable t)` 兜底，**必须重新抛出** `HookFailedError`：

```java
// app/src/main/java/com/example/ModuleMain.java
try {
    hook(m).setId("x").intercept(hooker);
} catch (HookFailedError e) {
    throw e;                                  // ⚠️ 必须重新抛出，不要吞
} catch (Throwable t) {
    log(Log.ERROR, TAG, "hook failed", t);    // IllegalArgumentException 等可兜
}
```

> ⚠️ **包名陷阱**：API **100** 用 `io.github.libxposed.api.errors`（**复数**）；**101+ 改为单数 `error`**。旧教程里的 `errors` 包已不存在。

---

## 4. 链式 Hook 的完整心智模型

### 4.1 类型流转（**`hook()` 返回 `HookBuilder` 不是 `HookHandle`，这是最常见的误用**）

```
 ① 你持有的对象            ② 配置                       ③ 安装                  ④ 结果句柄
┌──────────────────┐
│ Method /          │
│ Constructor      │   hook(origin)            setPriority(int)
│ (Executable)     │ ─────────────────► ┌──────────────────┐ ─────────────┐
└──────────────────┘                    │   HookBuilder    │              │
                                        │                  │  setExceptionMode(ExceptionMode)
      hookClassInitializer(Class)       │  「未安装的」     │              │
                       ──────────────►  │  配置载体         │  setId(String)  @SinceApi(102)
                                        └──────────────────┘              │
                                                  │                       │
                                                  │  intercept(Hooker) ←──┘
                                                  │  ← 只有这一步才真正安装 hook
                                                  ▼
                                        ┌──────────────────┐
                                        │   HookHandle     │
                                        │  「已安装的」句柄  │
                                        └──────────────────┘
                                                  │
                             getExecutable() / unhook() / getId() / replaceHook(Hooker)
                                                  │
                                                  ▼
                                        ┌──────────────────┐
                                        │  HookHandle (新)  │  ← replaceHook 后本 handle 失效
                                        └──────────────────┘


 运行期（每次目标方法被调用）：

   调用方 ──► ┌─ interceptor A (priority 高) ─┐
              │    chain.proceed() ───────►  │
              └─ interceptor B (priority 低) ─┤
                   chain.proceed() ───────►  │
                 ── 原方法（链尾）───────────► │
              ◄── 返回值沿链反向传回 ──────────┘
```

**四条可判定规则**：

1. `hook(...)` / `hookClassInitializer(...)` 返回的**永远是 `HookBuilder`**。写成 `HookHandle h = hook(m);` → **编译错误**。
2. `HookBuilder` 上的 `setPriority` / `setExceptionMode` / `setId` **返回 `this`**（可链式），但**都不是安装动作**。
3. 只有 `intercept(Hooker)` 才安装 hook，并**返回 `HookHandle`**。
4. 每次 `hook(...).intercept(...)` 都是**新增**一个拦截器，互不影响 —— 除非使用了相同的 `setId`（→ §4.2）。

### 4.2 `HookBuilder`

```java
interface HookBuilder {
    HookBuilder setPriority(int priority);
    HookBuilder setExceptionMode(@NonNull ExceptionMode mode);
    @SinceApi(API_102) HookBuilder setId(@Nullable String id);
    @NonNull HookHandle intercept(@NonNull Hooker hooker);
}
```

| 方法 | 参数语义 | 返回 | 异常 | 注意 |
|---|---|---|---|---|
| `setPriority(int)` | 优先级，**越高越先执行** | `this` | — | 默认 `PRIORITY_DEFAULT`（50） |
| `setExceptionMode(ExceptionMode)` | 异常处理模式 | `this` | — | 默认 `DEFAULT`。**这是单个 hook 的设置，不是全局** |
| `setId(String)` **@SinceApi(102)** | hook 的 id，**模块 + 方法作用域内唯一** | `this` | — | **同一模块在同一 executable 上用相同 id 再次 hook → 原子替换旧 hook，旧 handle 失效**。id 在不同模块间**隔离** |
| `intercept(Hooker)` | 拦截器 | `HookHandle` | `IllegalArgumentException`（origin 是框架内部方法 / `Constructor.newInstance` / hooker 非法）、`HookFailedError`（框架内部错误） | **唯一的安装动作** |

【一手·源码】

```java
// app/src/main/java/com/example/ModuleMain.java
XposedInterface.HookHandle handle = hook(method)
        .setPriority(XposedInterface.PRIORITY_DEFAULT)      // 50
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .setId("settings:battery:updatePercentText")        // @SinceApi(102)
        .intercept(chain -> {
            Object result = chain.proceed();
            log(Log.DEBUG, TAG, "hooked, result=" + result);
            return result;
        });
```

> **Hook 链是快照式（snapshot based）的**：在调用进行中替换 / 新增 hook，**不影响正在飞行中的那次调用**。【一手·源码】

### 4.3 `Hooker`（SAM 接口）

```java
interface Hooker {
    /**
     * @return 要返回给调用方的结果。不想改结果就 return chain.proceed()。
     *         void 方法与构造器的返回值被框架忽略。
     * @throws Throwable 任何异常都会向上传播（除非被 ExceptionMode 或上层 interceptor 捕获）
     */
    Object intercept(@NonNull Chain chain) throws Throwable;
}
```

【一手·源码】

**Java lambda 的三条硬规则**：

1. 返回值类型是 `Object`。**void 方法与构造器也要显式 `return null;`**（Java 没有 unit type）。返回 `null` 时框架忽略返回值。
2. `intercept` 声明 `throws Throwable` —— 你可以抛任何东西，但注意 `ExceptionMode` 决定它是否被吞。
3. 想改结果就返回新值；不想改就 `return chain.proceed();`。

```java
// app/src/main/java/com/example/ModuleMain.java —— 三种最常见形态
// (a) 保留原逻辑、只观察
hook(m).setId("observe").intercept(chain -> {
    log(Log.DEBUG, TAG, "args=" + chain.getArgs());
    return chain.proceed();
});

// (b) 完全替换（等价 XC_MethodReplacement）：不调 proceed
hook(m).setId("replace").intercept(chain -> "hardcoded");

// (c) void 方法：必须 return null
hook(voidMethod).setId("void").intercept(chain -> {
    chain.proceed();
    return null;   // ⚠️ for void methods, it's needed to return null
});
```

**Kotlin 差异**：lambda 可以没有显式 return（返回 `Unit` → 框架按 null 处理）。但 `proceed` 的形参是 `Object[]`，Kotlin 侧必须显式收成 `Array<Any>`：`val newArgs = arrayOf<Any>("replaced")`，否则 `arrayOf("x")` 被推断为 `Array<String>` 与 `Array<Any!>` 不匹配 —— **这是 Kotlin 侧最常见的一条编译错误**。【一手·源码】

### 4.4 `HookHandle`

```java
interface HookHandle {
    @NonNull Executable getExecutable();
    void unhook();
    @SinceApi(API_102) @Nullable String getId();
    @SinceApi(API_102) @NonNull HookHandle replaceHook(@NonNull Hooker hooker);
}
```

| 方法 | 语义 | 异常 | 注意 |
|---|---|---|---|
| `getExecutable()` | 被 hook 的方法 / 构造器 | — | |
| `unhook()` | 取消 hook | — | **幂等**，可重复调用（Javadoc："This method is idempotent."） |
| `getId()` | 返回 `setId` 设的 id；未设则 `null` | — | @SinceApi(102) |
| `replaceHook(Hooker)` | **原子替换** hooker。保留 executable / priority / exceptionMode / id | `IllegalArgumentException`（hooker 非法）、`IllegalStateException`（本 handle 已失效）、`HookFailedError` | **替换成功后本 handle 失效**（Javadoc："After a successful replacement, this handle is no longer valid."）。新代码入口 → 热重载（→ 见 `08-service-and-advanced.md §4.3`） |

【一手·源码】

### 4.5 `Chain`

```java
interface Chain {
    @NonNull Executable getExecutable();
    Object getThisObject();
    @NonNull List<Object> getArgs();
    Object getArg(int index) throws IndexOutOfBoundsException, ClassCastException;
    Object proceed() throws Throwable;
    Object proceed(@NonNull Object[] args) throws Throwable;
    Object proceedWith(@NonNull Object thisObject) throws Throwable;
    Object proceedWith(@NonNull Object thisObject, @NonNull Object[] args) throws Throwable;
}
```

【一手·源码】

| 方法 | 返回值 | 异常 | 注意 |
|---|---|---|---|
| `getExecutable()` | 当前被 hook 的 `Method` / `Constructor` | — | `proceedWith` **不会**改变它 |
| `getThisObject()` | 接收者；**静态方法为 `null`**；`<clinit>` hook 恒为 `null` | — | |
| `getArgs()` | 参数列表 | — | ⚠️ **返回不可变 List**（Javadoc："The returned list is immutable."）。`getArgs().set(i,v)` → `UnsupportedOperationException` |
| `getArg(int)` | 第 `index` 个参数 | `IndexOutOfBoundsException`、`ClassCastException` | Java 里 `(String) chain.getArg(0)` 的 CCE 由编译器插入的 checkcast 产生 |
| `proceed()` | 链下一环 / 原方法的结果 | `throws Throwable` | 不调 = 跳过原方法。**`proceed()` 抛的异常永远传播**，PROTECTIVE 也不吞 |
| `proceed(Object[])` | 用**新参数**继续的结果 | 同上 | **改参数的唯一正解** |
| `proceedWith(Object)` | 用**新接收者**继续的结果 | `IllegalArgumentException`（thisObject 不是声明类实例） | ⚠️ 不做虚派发（→ §4.6）。⚠️ **静态方法的拦截器禁止调用** |
| `proceedWith(Object, Object[])` | 同时换接收者与参数 | 同上 | 同上 |

**能力边界表（Agent 必读）**：

| 需求 | 该怎么做 | 不能怎么做 |
|---|---|---|
| 读 `this` | `chain.getThisObject()` | — |
| 读参数 | `chain.getArgs()` / `chain.getArg(i)` | ❌ `getArgs().set(...)` |
| **改参数** | `chain.proceed(newArgs)` | ❌ 改 `getArgs()` |
| **改 `this`** | `chain.proceedWith(newThis)` | ❌ 无 setter |
| **跳过原方法** | **不调 `proceed()`，直接 `return 值;`** | — |
| **改返回值** | `var r = chain.proceed(); return 新值;` | — |
| **抛异常给调用方** | `throw new XxxException();` | — |
| 捕获原方法异常 | `try { chain.proceed(); } catch (Throwable t) { ... }` | — |
| 重复调用原方法 | 可以多次调 `proceed()` | — |
| 虚派发到子类实现 | `getInvoker(childMethod).invoke(childObj, args)`（→ §5） | ❌ 指望 `proceedWith` 派发 |

```java
// app/src/main/java/com/example/ModuleMain.java —— 改参数的标准写法
hook(m).setId("tamper-arg").intercept(chain -> {
    Object[] args = chain.getArgs().toArray();   // 不可变 List → 先拷成数组
    args[0] = "replaced";
    Object result = chain.proceed(args);
    return "wrapped:" + result;                  // 改返回值
});
```

> ⚠️ **全局硬约束**：`Chain` 对象**不可跨线程共享**，`Hooker.intercept(Chain)` 返回后**不可复用**（Javadoc 首句原文）。要异步就先把 `getArgs()` / `getThisObject()` 拷出来，**绝不带 `chain` 进 `Runnable`**。

### 4.6 `proceedWith(Object thisObject)` 的精确语义

> 这段 Javadoc 来自 master（commit `495ca5d`，2026-08-27 "docs: specify the behavior of proceedWith"），**晚于 102.0.0 tag**。102.0.0 的措辞更简略，但行为一致。【一手·源码】

- 它**只改变当前链剩余部分使用的 receiver**。
- **不会**改变 `getExecutable()` 返回的方法，**不会**选择不同的 override，**不会**切换到另一个方法的 hook 链。
- 例：`getExecutable()` 是 `Parent.foo`，传一个 `Child` 对象进去，**仍然调用 `Parent.foo` 的原始实现**，不会派发到 `Child.foo`，也不会跑 `Child.foo` 上注册的 hook。
- 要虚派发 → 用 `Invoker` 发起一次**全新调用**（→ §5）。
- `thisObject` **必须**是 `getExecutable()` 声明类的实例，否则 `IllegalArgumentException`。
- **静态方法的 interceptor 不应调用它。**

### 4.7 `ExceptionMode` 使用位置

```java
// app/src/main/java/com/example/ModuleMain.java
hook(m)
    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)   // 单 hook 生效
    .setId("strict")
    .intercept(chain -> {
        throw new SecurityException("blocked by module");   // 直达调用方
    });
```

> 全局默认来自 `module.prop` 的 `exceptionMode=protective|passthrough`（默认 `protective`）。`setExceptionMode` 只影响**这一个 hook**。【一手·源码】

---

## 5. `Invoker` / `CtorInvoker` —— 调用原方法

> **这是 Agent 第二高频需求，也是无限递归的第一来源。**

### 5.1 类型定义

```java
interface Invoker<T extends Invoker<T,U>, U extends Executable> {
    sealed interface Type permits Type.Origin, Type.Chain {
        Origin ORIGIN = new Origin();
        record Origin() implements Type {}                        // 跳过全部 hook
        record Chain(int maxPriority) implements Type {           // 从链中间开始
            public static final Chain FULL = new Chain(PRIORITY_HIGHEST);
        }
    }
    T setType(@NonNull Type type);
    Object invoke(Object thisObject, Object... args)
        throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;
    Object invokeSpecial(@NonNull Object thisObject, Object... args)
        throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;
}

interface CtorInvoker<T> extends Invoker<CtorInvoker<T>, Constructor<T>> {
    @NonNull T newInstance(Object... args)
        throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;
    @NonNull <U> U newInstanceSpecial(@NonNull Class<U> subClass, Object... args)
        throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;
}
```

获取方式：

```java
Invoker<?, Method>   mi = getInvoker(someMethod);
CtorInvoker<Foo>     ci = getInvoker(someConstructor);
```

【一手·源码】

> **所有 Invoker 调用绕过访问检查**（Javadoc 原文："Invocations through invokers will bypass access checks."）—— 多数场景**不需要** `setAccessible(true)`。
> ⚠️ 但绕过访问检查**不等于**绕过异常包装：`invoke` / `invokeSpecial` / `newInstance` / `newInstanceSpecial` 全部声明 `throws InvocationTargetException`，原方法抛的异常被包装在里面，要 `e.getTargetException()` 取真实原因。

### 5.2 `Type` 三选一（**默认是 `Chain.FULL`**）

| Type | 语义 | 等价 legacy |
|---|---|---|
| `Invoker.Type.ORIGIN`（或 `new Invoker.Type.Origin()`） | 调用**原始实现**，跳过所有 hook | `XposedBridge.invokeOriginalMethod` |
| `new Invoker.Type.Chain(int maxPriority)` | 从链中间开始，**跳过优先级高于 `maxPriority` 的 hook** | — |
| `Invoker.Type.Chain.FULL`（= `new Chain(PRIORITY_HIGHEST)`） | **完整 hook 链**，≈ 直接 `Method.invoke` | `Method.invoke` |
| ⚠️ **不调 `setType` 时的默认值** | **`Chain.FULL`** | — |

【一手·源码】Javadoc 明文写在 `getInvoker(Method)` 与 `getInvoker(Constructor)` 上："The default type of the invoker is `Invoker.Type.Chain#FULL`."

> ⚠️ **无限递归首因**：在 hook 的拦截器里写 `getInvoker(m).invoke(...)` 而不 `setType(ORIGIN)` → 走完整链 → 重新进入你自己的 hook → `StackOverflowError`。
> **可判定规则**：要"调原方法"，**必须** `.setType(Invoker.Type.ORIGIN)`。

### 5.3 `invoke` vs `invokeSpecial`

| 方法 | 语义 | 何时用 |
|---|---|---|
| `invoke(thisObject, args)` | 通过 hook 链调用（走哪一环由 `setType` 决定）。**虚调用**：会按 `thisObject` 的实际类型派发 | 常规调用；静态方法 `thisObject` 传 `null` |
| `invokeSpecial(thisObject, args)` | **非虚调用**，等价 JNI `CallNonVirtual<type>Method`。绕过子类 override，直接调**声明类里定义的那个实现** | **在被 hook 的构造器里调 `super.xxx()`**（Javadoc 明文用途） |

【一手·源码】

### 5.4 `newInstance` / `newInstanceSpecial(subClass, args)`

| 方法 | 语义 |
|---|---|
| `newInstance(args)` | 通过 hook 链构造实例（type 决定从哪一环开始） |
| `newInstanceSpecial(subClass, args)` | 创建 **`subClass`** 的实例，但用**父类构造器**去初始化它 |

⚠️ `newInstanceSpecial` 的 Javadoc 警告（原文）："This could leave the object in an **invalid state**, where the subclass constructor is not called and the fields of the subclass are not initialized." 用途是"This method is useful when you need to initialize some fields in the subclass by yourself."

**`subClass` 的语义（极易搞错）**：传的是**子类的 `Class`**，构造参数是**父类构造器的参数**。

```java
getInvoker(parentCtor).newInstanceSpecial(Child.class, argsForParentCtor);
//          ↑ 父类构造器                ↑ 要创建的实际类型   ↑ 父类构造器的参数
```

【一手·源码】

### 5.5 可运行示例（覆盖全部 Invoker 用法）

```java
// app/src/main/java/com/example/ModuleMain.java
package com.example.mymodule;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static final String TAG = "MyModule";
    private static final String TARGET_PACKAGE = "com.example.target";

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;

        try {
            Class<?> c = Class.forName("com.example.target.Example", true, param.getClassLoader());
            Method m = c.getDeclaredMethod("method", String.class);
            Constructor<?> ctor = c.getDeclaredConstructor();

            // 1) 在拦截器里调原方法 —— 必须 setType(ORIGIN)，否则无限递归
            hook(m).setId("example:method").intercept(chain -> {
                Object thisObj = chain.getThisObject();
                String result = (String) chain.proceed();

                // ✅ 跳过所有 hook，直调原始实现
                String raw = (String) getInvoker(m)
                        .setType(XposedInterface.Invoker.Type.ORIGIN)
                        .invoke(thisObj, chain.getArgs().toArray());

                // ❌ 下面这行会无限递归（默认 type 是 Chain.FULL，会重进本 hook）
                // String boom = (String) getInvoker(m).invoke(thisObj, chain.getArgs().toArray());

                return result + raw;
            });

            // 2) 从链中间开始：跳过优先级高于 -50 的 hook
            Object mid = getInvoker(m)
                    .setType(new XposedInterface.Invoker.Type.Chain(-50))
                    .invokeSpecial(null, "arg");

            // 3) 构造器：原始构造（跳过所有 hook）
            Object plain = getInvoker(ctor)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .newInstance();

            // 4) 构造子类实例但用父类构造器初始化 —— 传子类 Class + 父类构造参数
            Object special = getInvoker(ctor)
                    .setType(XposedInterface.Invoker.Type.Chain.FULL)
                    .newInstanceSpecial(c);

            log(Log.INFO, TAG, "ok: " + mid + " / " + plain + " / " + special);
        } catch (InvocationTargetException e) {
            // ⚠️ 原方法抛的异常被包装在这里，必须取 targetException 才看得到真因
            log(Log.ERROR, TAG, "original method threw", e.getTargetException());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "invoker failed", t);
        }
    }
}
```

> **不要**把 `setType(ORIGIN)` 当成"避免递归的默认手段"【推断】：它绕过了其它模块的 hook 与自己的其它 hook，破坏 hook 链协作；若原方法有副作用，重复调用会执行两次。避免递归的正确手段是**别在拦截器里调用被 hook 的同一个方法**。

---

## 6. `deoptimize(Executable)`

```java
boolean deoptimize(@NonNull Executable executable);
```

- **返回**：是否成功。
- **参数语义（最大反直觉点）**：要**去优化**的方法/构造器 —— 即**调用方 A**，**不是**被 hook 的被调方 B。

Javadoc 原文（【一手·源码】）：

> "when a short hooked method **B** is invoked by method **A**, the callback to B is not invoked after hooking, which may mean **A has inlined B** inside its method body. **To force A to call the hooked B, you can deoptimize A**."

### 排查示例（可执行 runbook）

```java
// app/src/main/java/com/example/ModuleMain.java
@Override
public void onPackageReady(@NonNull PackageReadyParam param) {
    try {
        Class<?> c = Class.forName("com.android.settings.SomeTarget", true, param.getClassLoader());
        Method target = c.getDeclaredMethod("isFeatureEnabled");     // 被 hook 的短方法 B

        // 1) 先确认回调到底有没有进
        hook(target).setId("feature-enabled").intercept(chain -> {
            log(Log.ERROR, TAG, "intercept entered");                // 排障期用 ERROR 级别，确保被记录
            return chain.proceed();
        });

        // 2) 对「所有已知调用方」去优化。⚠️ 是 A(caller)，不是 B(target)
        deoptimizeCallers(param.getClassLoader(),
                "com.android.settings.SomeCallerA", "refresh",
                "com.android.settings.SomeCallerB", "onResume");

        // 3) 验证：用反射主动调一次。能触发 ⇒ 高度指向内联
        getInvoker(target).setType(XposedInterface.Invoker.Type.Chain.FULL).invoke(null);
    } catch (Throwable t) {
        log(Log.ERROR, TAG, "install failed", t);
    }
}

private void deoptimizeCallers(@NonNull ClassLoader cl, @NonNull String... pairs) {
    for (int i = 0; i + 1 < pairs.length; i += 2) {
        try {
            Class<?> c = Class.forName(pairs[i], true, cl);
            deoptimize(c.getDeclaredMethod(pairs[i + 1]));   // ← 去优化的是调用方
        } catch (Throwable t) {
            log(Log.WARN, TAG, "deoptimize failed: " + pairs[i] + "#" + pairs[i + 1], t);
        }
    }
}
```

### 局限（Javadoc 原文，【一手·源码】）

> "Generally, you need to find all the callers of your hooked callee, and that can hardly be achieved (but you can still search all callers by using DexKit). Use this method if you are sure the deoptimized callers are all you need. Otherwise, it would be better to **change the hook point** or to **deoptimize the whole app manually (by simply reinstalling the app without uninstall)**."

**可判定规则（优先级从高到低）**：

1. 优先**换 hook 点** —— 钩一个不会被内联的、更上层或更下层的方法。
2. 用 [DexKit](https://github.com/LuckyPray/DexKit) 找出全部 caller，逐个 `deoptimize()`。
3. 兜底：**不卸载重装目标 App**（触发 dex2oat 以非优化模式重编）。
4. 排障时**用 release 构建**验证。`android:debuggable=true` 与调试器挂载都会改变 ART 行为。

> 短方法、getter/setter、`boolean` 判定方法最容易被内联。典型症状：hook 注册成功（无异常），但回调**一次都不触发**；或只在部分路径触发（反射调用能触发、直接调用不能）。

---

## 7. `log(int priority, String tag, String msg[, Throwable])`

```java
void log(int priority, @Nullable String tag, @NonNull String msg);
void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr);
```

| 参数 | 语义 |
|---|---|
| `priority` | **`android.util.Log` 的优先级常量**：`Log.VERBOSE` / `DEBUG` / `INFO` / `WARN` / `ERROR` / `ASSERT`。**API 自己不定义任何优先级常量** |
| `tag` | 可为 `null` |
| `msg` | **不可为 `null`**（`@NonNull`） |
| `tr` | 可为 `null`。**带 `Throwable` 的重载是唯一能把堆栈写进 Xposed 日志的方式** |

【一手·源码】

```java
// app/src/main/java/com/example/ModuleMain.java
log(Log.INFO, TAG, "hook installed");
try {
    installHooks(cl);
} catch (Throwable t) {
    log(Log.ERROR, TAG, "install failed", t);      // ← 堆栈只在这一条里
}
```

**可判定规则**：
- ❌ 不要用 `XposedBridge.log()` / `Log.d()` 代替。`XposedBridge` 在宿主进程里**不存在**，且 `targetApiVersion ≥ 102` 时**禁止**调用 legacy `de.robv.android.xposed` API。
- ✅ `onModuleLoaded` 里**必须**打一行（框架名 + `getApiVersion()` + `getFrameworkProperties()` + 进程名）—— 这是所有排障的起点。
- 【推断】`log()` 写入框架日志系统（管理器 → 日志页面），**不是 logcat**。排障以管理器日志为准。

---

## 8. 远程 / 跨进程数据（三方法）

```java
@NonNull SharedPreferences getRemotePreferences(@NonNull String group)
    throws UnsupportedOperationException;                    // 框架为 embedded 时抛
@NonNull String[] listRemoteFiles()
    throws UnsupportedOperationException;
@NonNull ParcelFileDescriptor openRemoteFile(@NonNull String name)
    throws FileNotFoundException,                           // 文件不存在或路径非法
           UnsupportedOperationException;                   // 框架为 embedded
```

| 方法 | 语义 | 注意 |
|---|---|---|
| `getRemotePreferences(group)` | 取框架侧存储的 `SharedPreferences` | ⚠️ **在被 hook 的宿主 App 中只读**（Javadoc 原文："those are read-only in hooked apps"）。group 不存在时（service 侧）会自动创建 |
| `listRemoteFiles()` | 列出模块共享数据目录下的所有文件 | |
| `openRemoteFile(name)` | **只读**打开 | `name` **不得包含路径分隔符**，也不得是 `.` 或 `..`。不存在 → `FileNotFoundException` |

【一手·源码】

```java
// app/src/main/java/com/example/ModuleMain.java —— 宿主进程侧：只读 + 变更监听
@Override
public void onPackageReady(@NonNull PackageReadyParam param) {
    if (!param.isFirstPackage()) return;

    // ✅ 先探测能力位，再调用
    if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) {
        log(Log.WARN, TAG, "remote not supported, skip config");
        return;
    }
    try {
        SharedPreferences prefs = getRemotePreferences("conf");       // 只读
        log(Log.INFO, TAG, "flag=" + prefs.getBoolean("flag", false));
        prefs.registerOnSharedPreferenceChangeListener((p, key) ->
                log(Log.INFO, TAG, "changed: " + key + "=" + p.getBoolean(key, false)));
    } catch (UnsupportedOperationException e) {
        log(Log.WARN, TAG, "embedded framework, no remote prefs");
    }

    try {
        String text;
        try (ParcelFileDescriptor pfd = openRemoteFile("map.json");   // 只读；不存在则抛
             FileReader reader = new FileReader(pfd.getFileDescriptor())) {
            text = new BufferedReader(reader).lines().reduce("", String::concat);
        }
        log(Log.INFO, TAG, "remote file: " + text);
    } catch (FileNotFoundException e) {
        log(Log.INFO, TAG, "remote file not created yet");   // 正常分支，不是错误
    } catch (Throwable t) {
        log(Log.ERROR, TAG, "read remote file failed", t);
    }
}
```

**可判定规则**：

1. **宿主进程内只读**。**写**只能在模块 App 侧用 `XposedService#getRemotePreferences(group)`（→ 见 `08-service-and-advanced.md §1.3.4`）。
2. 调用前必须用 `PROP_CAP_REMOTE` 探测，或 `catch (UnsupportedOperationException)` 降级。
3. 大块数据（配置 blob / dex / 映射表）走 **Remote Files**；小配置 + 需要变更推送走 **Remote Preferences**。
4. ❌ **不要**在 hook 回调里写 SharedPreferences。❌ **不要**用任何 `XSharedPreferences`（含 New XSharedPreferences）。
5. ❌ 不要在 `intercept()` 里每次都 `getRemotePreferences()` 重新解析 —— 缓存 + 变更监听。

> 存储位置（官方 Wiki 对照表【一手·文档】）：Remote Preferences → LSPosed 数据库；Remote Files → `/data/adb/lspd/modules/<user>/<module>`。

---

## 9. 资源 Hook —— **已彻底移除，无对应 API**

官方 Wiki 原文（【一手·文档】）：

> "**Resource hooks are removed.** Resource hooks are hard to maintain and caused many problems previously, so it will not be supported."

**不存在的类型 / 方法**（API 100 / 101 / 102 **均不存在**）：

`onResourceLoaded` · `ResourcesHookParam` · `hookDrawable` · `hookXml` · `hookSystemWideLayout` · `XResources` · `IXposedHookInitPackageResources` · `XC_LayoutInflated`

- 这些是 LSPosed 1.x 时代的 Legacy 资源 Hook 能力；现代 API **明确不提供替代品**。
- `libxposed/api#58` [Feature Request] Resource Hook → `closed / not_planned` —— **资源 Hook 不会回来**。【一手·源码】

### 102 下的替代思路（若模块必须改资源）

| 目标 | 做法 |
|---|---|
| 改字符串 / 颜色 / 尺寸取值 | hook `Resources#getXxx(...)` / `AssetManager` 等 **Java 层读取点**，在 `Hooker` 里改写返回值 |
| 改布局 / 改 View 树 | hook `LayoutInflater` / `View` 构造过程，在 `onPackageReady` 之后操作 View 树 |
| 全局换 Resources | hook `Context#getResources()` 返回一个包装的 `Resources` |

```java
// app/src/main/java/com/example/ModuleMain.java —— 改字符串资源的替代写法
@Override
public void onPackageReady(@NonNull PackageReadyParam param) {
    try {
        Class<?> res = Class.forName("android.content.res.Resources", true, param.getClassLoader());
        Method getString = res.getDeclaredMethod("getString", int.class);
        hook(getString).setId("res:getString").intercept(chain -> {
            String origin = (String) chain.proceed();
            return "com.example.target:string/app_name".equals(origin) ? "Renamed" : origin;
        });
    } catch (Throwable t) {
        log(Log.ERROR, TAG, "hook resources failed", t);
    }
}
```

---

## 10. `XposedModule` / `XposedModuleInterface` / `XposedInterfaceWrapper`

### 10.1 `XposedModule`（API 102 完整源码）

```java
package io.github.libxposed.api;

/**
 * Super class which all Xposed module entry classes should extend.<br/>
 * Entry classes will be instantiated once for each loaded module generation in a process.
 */
@SuppressWarnings("unused")
public abstract class XposedModule extends XposedInterfaceWrapper implements XposedModuleInterface {
}
```

【一手·源码】

**可判定规则（入口类）**：

1. **必须** `public`、**必须** `extends XposedModule`。
2. **必须提供公开的无参构造器**（隐式默认构造器即可）。写了带参构造器却没写无参 → 框架反射实例化失败 → 模块静默失效。
3. **禁止在构造器 / 字段初始化里调用任何 `XposedInterface` 方法** —— 框架在实例化之后才调 `attachFramework()`，此前调用会抛 `IllegalStateException("Framework not attached")`。
4. 每个**模块代（generation）**在每个进程中实例化一次（热重载会产生新代）。
5. 每个入口类**一个进程一份实例**，多入口类互不影响。

### 10.2 `XposedModuleInterface`

`XposedModuleInterface` 是**纯生命周期接口**，方法**全部是 `default`**（空实现，或仅 unhook 旧 hook），**不委托任何东西**。真正的委托发生在 `XposedInterfaceWrapper`。

```
XposedInterface (interface)
        ▲ implements
        │
XposedInterfaceWrapper (class)  ──持有──> mBase: XposedInterface (框架实现)
        ▲ extends                                    ▲
        │                                            │ implements
XposedModule (abstract class，类体为空)          [LSPosed / Vector / LSPatch ...]
        │ implements
        ▼
XposedModuleInterface (interface，纯生命周期)
```

**设计意图**【一手·源码 + 推断】：

1. 模块只依赖 API 契约，不依赖框架实现；框架细节被 `XposedInterfaceWrapper` 屏蔽。
2. `XposedInterfaceWrapper` 的转发方法**全部是 `final`**，防止模块覆写核心能力，保证框架能插入 `ensureAttached()` 一致性检查。
3. `XposedModuleInterface` 全 `default` → 模块只重写关心的回调（对比 API 100 的 `onPackageLoaded` / `onSystemServerLoaded` 是抽象方法，必须全部实现）。
4. `XposedModule` 是空的抽象类 —— 纯粹是两个契约的聚合点，方便 `instanceof` 检查与 ProGuard `-keep` 规则。

> ⚠️ 常见误解：`XposedModuleInterface` 的"默认方法委托给 `XposedInterface`"。**这个描述不准确**（实测源码：它的方法体是空的）。

### 10.3 `LoadedParam` 体系总览

→ §13 逐个详解。

### 10.4 `XposedInterfaceWrapper` 与 `detach()`

```java
public class XposedInterfaceWrapper implements XposedInterface {
    @InternalApi
    public final void attachFramework(@NonNull XposedInterface base, @NonNull Runnable detachImpl);

    @SinceApi(API_102)
    public final void detach();

    // 以下全部为 final，委托给 mBase，调用前 ensureAttached()
    public final int getApiVersion();
    public final String getFrameworkName();
    public final String getFrameworkVersion();
    public final long getFrameworkVersionCode();
    public final long getFrameworkProperties();
    public final HookBuilder hook(Executable origin);
    public final HookBuilder hookClassInitializer(Class<?> origin);
    public final boolean deoptimize(Executable executable);
    public final Invoker<?, Method> getInvoker(Method method);
    public final <T> CtorInvoker<T> getInvoker(Constructor<T> constructor);
    public final void log(int priority, String tag, String msg);
    public final void log(int priority, String tag, String msg, Throwable tr);
    public final SharedPreferences getRemotePreferences(String name);
    public final ApplicationInfo getModuleApplicationInfo();
    public final String[] listRemoteFiles();
    public final ParcelFileDescriptor openRemoteFile(String name) throws FileNotFoundException;
}
```

| 成员 | 语义 |
|---|---|
| `attachFramework(base, detachImpl)` | **`@InternalApi`，模块禁止调用**。框架负责调。重复 attach → `IllegalStateException("Framework already attached")` |
| `detach()` **@SinceApi(102)** | 让**当前模块入口**停止接收后续生命周期回调；框架移除对该入口实例的引用 |
| 其余 `final` 方法 | 委托 + `ensureAttached()`；未 attach 时抛 `IllegalStateException("Framework not attached")` |

【一手·源码】

**`detach()` 的三条语义（可判定规则）**：

1. **只影响生命周期回调**。所有 `XposedInterface` API（`hook()` / `log()` / `deoptimize()` …）以及**已安装的 hook 依然完全可用**。
2. **幂等**。多入口类时**只影响调用它的那一个**。
3. **反模式**：`detach()` 后以为 hook 失效（不会）；以为只要 `detach()` 就能让 ClassLoader 被 GC（**必须**同时 unhook 全部 hook、停掉 Java 线程、清掉系统/App 对象持有的模块引用）。Javadoc 明文警告：若所有 Java 引用都清了但 native 代码还在跑，native 库被运行时卸载时可能崩溃 —— **这是模块生命周期 bug**。

```java
// app/src/main/java/com/example/ModuleMain.java —— 多入口 / 非目标进程场景
@Override
public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    if (!TARGET_PACKAGE.equals(param.getProcessName())) {
        detach();      // 不再接收后续任何生命周期回调
    }
}
```

---

## 11. 模块声明、`ClassLoader` 与 `module.prop`

### 11.1 `META-INF/xposed/` 下的文件

| 文件 | 内容 | 解析规则（**各不相同**） | 缺失 / 空文件的后果 |
|---|---|---|---|
| `java_init.list` | Java 入口类**全限定名**，一行一个 | `readName()`：逐行 `trim()`；**空行跳过**；**`#` 开头视为注释跳过**；支持多入口 | ⚠️ **空 / 全是注释 ⇒ 被判为 Legacy**，回退读 `assets/xposed_init`（也不存在则 `return null`，模块加载失败，**无任何报错**） |
| `module.prop` | Java Properties 键值对 | `new Properties().load(...)`；**键区分大小写** | 不存在 ⇒ `min=100 / target=100 / staticScope=false` |
| `scope.list` | 作用域包名，一行一个 | ⚠️ `reader.lines().collect(toList())` —— **原样收集：不 trim、不跳空行、不过滤注释** | 缺失 ⇒ 空作用域，**不报错** |
| `native_init.list` | native 库名（如 `libexample.so`） | 同 `java_init.list` 的 `readName()` | 不写则框架不会给你的 so 注入 `native_init` |

【一手·源码】（`ConfigFileManager.loadModule()` / `readName()` / `ModuleUtil`）

```java
// 框架侧判定逻辑（逐字，来自 ConfigFileManager.loadModule）
readName(apkFile, "META-INF/xposed/java_init.list", moduleClassNames);
if (moduleClassNames.isEmpty()) {
    file.legacy = true;                                              // 回退 Legacy
    readName(apkFile, "assets/xposed_init", moduleClassNames);
    readName(apkFile, "assets/native_init", moduleLibraryNames);
} else {
    file.legacy = false;
    readName(apkFile, "META-INF/xposed/native_init.list", moduleLibraryNames);
}
```

**可判定规则**：

1. `java_init.list` 与 `assets/xposed_init` 是**二选一开关，不是并存**。`java_init.list` 非空时框架**根本不读** `assets/xposed_init`。想同时兼容新旧框架，不能靠放两个文件。
2. `scope.list` 里**不要留空行、不要写注释、行尾不要带空格**。
3. **热重载要求 `java_init.list` 恰好一行**（0 行或多行 ⇒ `HotReloadResult.Status.UNSUPPORTED`）。
4. 两套判定标准并存：daemon 看 `java_init.list` 是否**非空**；manager 的 `isLegacyModule()` 看 manifest meta-data 是否含 `xposedminversion`。→ **现代模块不要在 manifest 里写 `xposedminversion`**。
5. 构建必须 `packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }`（两条成对），否则这些文件被 AGP 剥离。

### 11.2 ProGuard / R8 三件套

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

| 规则 | 作用 | 缺了它的后果 |
|---|---|---|
| `-dontwarn io.github.libxposed.annotation.**` | 注解类是 `compileOnly`，APK 里不存在，R8 会警告找不到引用类 | release 构建报 warning；`abortOnError=true` 时**直接失败** |
| `-adaptresourcefilecontents META-INF/xposed/java_init.list` | 入口类被混淆后**同步重写清单里的类名** | ⚠️ **debug 正常、release 完全失效** |
| `-keep,allowoptimization,allowobfuscation public class * extends XposedModule { public <init>(); }` | 入口类不被移除、保留**公开无参构造器**；但允许重命名（由上一行兜底） | 入口被 shrink ⇒ 静默失效；构造器被内联 ⇒ `NoSuchMethodException` / `IllegalAccessException` |

**自检命令**：`unzip -l app-release.apk | grep xposed`

### 11.3 `ClassLoader` 抉择

| 场景 | 用哪个 | 注意 |
|---|---|---|
| hook 应用代码 | `onPackageReady` 的 `param.getClassLoader()` | **必须用三参 `Class.forName(name, true, cl)`**。两参版本用调用者（模块）的 CL，一定找不到宿主类 |
| `onPackageLoaded` 里 | 只有 `param.getDefaultClassLoader()` | ⚠️ **没有 `getClassLoader()`**。且 `getDefaultClassLoader()` 带 `@RequiresApi(Q)` |
| system_server | `onSystemServerStarting` 的 `param.getClassLoader()` | 等价 legacy 的 `XposedBridge.BOOTCLASSLOADER` |
| 默认 CL vs 真实 CL | 优先真实 CL | Javadoc："`getClassLoader()` **may be different from** `getDefaultClassLoader()` if the package has a custom `AppComponentFactory` that creates a different classloader." |

【一手·源码】

### 11.4 `module.prop` 字段详解

文件：`src/main/resources/META-INF/xposed/module.prop`，**Java Properties 格式**。

| 字段 | 类型 | 必须 | 默认值 | 大小写敏感性 | 语义 |
|---|---|---|---|---|---|
| `minApiVersion` | int | **是** | `100`（prop 文件不存在时） | 键名**严格区分大小写** | 运行所需最低 Xposed API 版本。框架 API < 此值则模块不加载 |
| `targetApiVersion` | int | **是** | `100` | 同上 | 针对哪个 API 构建，决定启用哪些行为变更。**≥102 ⇒ 禁止调用 legacy `de.robv.android.xposed`** |
| `staticScope` | boolean | 否 | `false` | 键名区分大小写；值是**全等字符串比较** `"true"` | 禁止用户把模块用到 scope list 之外的 App |
| `autoHotReload` | boolean | 否 | 无（等同不启用） | 同 `staticScope` | App 更新时自动热重载。**仍以 `onHotReloading()` 返回 `true` 为前提** |

**第 5 个合法字段**（官方示例未写，`package-info.java` 记载）：`exceptionMode=protective|passthrough`，默认 `protective`。它是 `ExceptionMode.DEFAULT` 的全局来源。

```properties
# 推荐配置（需要 102-only API 时）
minApiVersion=102
targetApiVersion=102
staticScope=true
# 需要热重载再加：autoHotReload=true（且 java_init.list 只能有一行）
```

框架侧解析（【一手·源码】`ModuleUtil`）：

```java
staticScope = TextUtils.equals(prop.getProperty("staticScope"), "true");   // 全等字符串比较
```

**可判定规则**：

1. `staticScope` 必须写**小写字面量 `true`**。`True` / `TRUE` / `1` / `yes` **一律不生效**。
   > ⚠️ 素材冲突说明：03 号采集稿描述为 "Java `Properties` + `Boolean.parseBoolean` 语义"。以源码为准 —— 实际是 `TextUtils.equals(..., "true")`，比 `parseBoolean` 更严格（后者会接受 `TRUE`）。
2. 键缺失**不会**回退到默认值 `100`，而是走 `null` 处理路径。**三个键全部显式写出**。
3. `minSdk`（Gradle，Android SDK 版本）与 `minApiVersion`（Xposed API 版本）是**完全不同的两个数字**，不要互抄。
4. **不要在 `AndroidManifest.xml` 里写 `xposedmodule` / `xposeddescription` / `xposedminversion` meta-data** —— 那是 Legacy 写法，且会让管理器把你判为 Legacy 模块。模块名取 `android:label`，描述取 `android:description`。
5. 用 `minApiVersion=101` + `targetApiVersion=102` 可在只实现 101 的框架上运行，但**必须**对 102 新增 API 做 `getApiVersion() >= API_102` 守卫；接入 `libxposed/lint` 后 `@SinceApi` 会在编译期检查（lint 错误 id：`XposedNewApi`，**Severity.ERROR**）。

---

## 12. 生命周期回调（6 个，全部是 `default` 方法）

| 回调 | 触发时机 | 能拿到什么 | 该干什么 / 不该干什么 |
|---|---|---|---|
| `onModuleLoaded(ModuleLoadedParam)` | 模块代被载入目标进程时 | `isSystemServer()`、`getProcessName()` | ✅ 打诊断日志、能力探查。❌ **没有任何 ClassLoader，禁止 hook**。❌ 热重载时**不重放** |
| `onPackageLoaded(PackageLoadedParam)`<br>**`@RequiresApi(Q)`** | 有 `android:hasCode` 的包被载入进程；默认 classloader 就绪、**`AppComponentFactory` 实例化之前** | `getPackageName()`、`getApplicationInfo()`、`isFirstPackage()`、`getDefaultClassLoader()` | ⚠️ Android < 29 **不会被回调**。❌ 没有 `getClassLoader()`。每个包名只调一次 |
| `onPackageReady(PackageReadyParam)` | `AppComponentFactory` 造好真实 classloader、准备创建 `Application` | 上面全部 + `getClassLoader()`、`getAppComponentFactory()` | ✅ **绝大多数 hook 的安装点**。第一行先过滤 `getPackageName()` + `isFirstPackage()` |
| `onSystemServerStarting(SystemServerStartingParam)` | system_server 准备启动关键服务 | `getClassLoader()` | ✅ system_server 的唯一 hook 点。❌ 此处 `isFirstPackage()` **永不为 true** |
| `boolean onHotReloading(HotReloadingParam)`<br>**`@SinceApi(102)`** | 热重载即将发生，**运行在旧代码中** | `getExtras()`、`setSavedInstanceState(Object)` | 返回 `true` 放行（**默认 `false` = 拒绝**） → 见 `08-service-and-advanced.md §4.2` |
| `void onHotReloaded(HotReloadedParam)`<br>**`@SinceApi(102)`** | 重载完成，**运行在新代码中** | `getExtras()`、`getSavedInstanceState()`、`getOldHookHandles()` | ⚠️ **默认实现只是 unhook 全部旧 hook**，包生命周期回调**不重放** → 见 `08-service-and-advanced.md §4.3` |

【一手·源码】

**触发顺序（普通 App 进程）**：

```
[框架反射实例化入口类] → attachFramework(base, detachImpl)
   → onModuleLoaded(ModuleLoadedParam)        // 只做日志 / 能力探查
   → onPackageLoaded(PackageLoadedParam)      // 默认 CL 就绪（@RequiresApi(Q)）
   → onPackageReady(PackageReadyParam)        // 真实 CL 就绪 ← 在此 hook
   → (应用继续启动)
```

**system_server**：

```
onModuleLoaded → onSystemServerStarting(SystemServerStartingParam) → onPackageLoaded / onPackageReady（后续包）
```

**可判定规则**：

1. 所有回调抛出的异常都会被框架**捕获并记日志**（Javadoc："Everything the callback throws is caught and logged."）—— **不会崩宿主，但你的逻辑静默失效**。
2. **回调会超出 `scope.list` 的范围**。Javadoc 明文："After injection, **every loaded package in that process will trigger callbacks**. As a result, modules may receive callbacks **beyond** the originally scoped packages, so they should **always filter by process name and package name**." → 每个 `onPackageXXX` 第一行就过滤。
3. system_server 中 `onPackageLoaded` / `onPackageReady` 的**首轮**回调被 `onSystemServerStarting` 取代，因此 `isFirstPackage()` 在此**永不为 true**。
4. 作用域只决定"注入哪些进程"，不决定"收到哪些包的回调"。

---

## 13. `LoadedParam` 体系（逐个 getter）

| Param 接口 | 方法 | 语义 | 版本限制 |
|---|---|---|---|
| `ModuleLoadedParam` | `boolean isSystemServer()` | 当前进程是否 system_server | — |
| | `@NonNull String getProcessName()` | 进程名。**唯一**能拿到进程名的地方 | — |
| `PackageLoadedParam` | `@NonNull String getPackageName()` | 包名 | — |
| | `@NonNull ApplicationInfo getApplicationInfo()` | 宿主的 `ApplicationInfo`（API 100 叫 `getAppInfo()`） | — |
| | `boolean isFirstPackage()` | 是否进程中第一个 / 主包 | — |
| | `@NonNull ClassLoader getDefaultClassLoader()` | 加载包代码 / 资源 / 自定义 `AppComponentFactory` 的 classloader | **`@RequiresApi(Q)`（Android 29+）** |
| `PackageReadyParam extends PackageLoadedParam` | `@NonNull ClassLoader getClassLoader()` | **真实 classloader**。若包有自定义 `AppComponentFactory` 造出不同 CL，则与 `getDefaultClassLoader()` 不同 | — |
| | `@NonNull AppComponentFactory getAppComponentFactory()` | | **`@RequiresApi(P)`（Android 28+）** |
| `SystemServerStartingParam` | `@NonNull ClassLoader getClassLoader()` | system_server 的 classloader | — |
| `HotReloadingParam` **@SinceApi(102)** | `@Nullable Bundle getExtras()` | 模块 App 触发热重载时传入的数据。App 传 `null` 或由 App 更新触发时为 `null`。**Bundle 只能放不依赖模块 classloader 就能反序列化的值** | @SinceApi(102) |
| | `void setSavedInstanceState(@Nullable Object)` | 传给新代的状态。**不得**包含在旧模块 classloader 下创建的对象；框架检测到抛 `IllegalArgumentException` | @SinceApi(102) |
| `HotReloadedParam extends ModuleLoadedParam` **@SinceApi(102)** | `@Nullable Bundle getExtras()` | 同 `HotReloadingParam` | @SinceApi(102) |
| | `@Nullable Object getSavedInstanceState()` | `onHotReloading` 存下的状态 | @SinceApi(102) |
| | `@NonNull List<XposedInterface.HookHandle> getOldHookHandles()` | 上一代创建的 hook handle 列表 | @SinceApi(102) |

【一手·源码】

> ⚠️ `HotReloadedParam extends ModuleLoadedParam` —— 所以热重载回调里**也有** `getProcessName()` 与 `isSystemServer()`。
> ⚠️ 多包进程（`sharedUserId`、`createPackageContext(..., CONTEXT_INCLUDE_CODE)`）会对**每个包**触发一次回调 → `if (!param.isFirstPackage()) return;` 防重复安装。

---

## 14. `io.github.libxposed.annotation` —— `@SinceApi` / `@InternalApi`

> ⚠️ **这两个注解在 `libxposed/lint` 仓库，`不在 api 仓库`**。API 102 发布时 annotation 库未同步发布到 Maven Central（`libxposed/api#63`，`Dr-TSNG`："The annotation library will be published to maven central together with api 102. I don't suggest you use it now because it's unstable."）→ **不要主动依赖它**，用 `-dontwarn` 掉。

| 注解 | 保留策略 | 作用 | 配套 lint 检查 |
|---|---|---|---|
| `@SinceApi(int value)` | `CLASS`，`@IntRange(from = 101)` | 标注该元素自哪个 API 版本引入 | `SinceApiDetector`：当 `minApiVersion` 低于标注版本且无 `getApiVersion()` 守卫时报错（issue id `XposedNewApi`，**Severity.ERROR**） |
| `@InternalApi` | `CLASS` | 标注**仅供框架实现使用**的 API，**模块禁止调用** | `InternalApiDetector` |

【一手·源码】

**受 `@SinceApi(102)` 约束的 API（用了就要么 `minApiVersion=102`，要么 `getApiVersion() >= 102` 守卫）**：

`HookBuilder#setId` · `HookHandle#getId` · `HookHandle#replaceHook` · `XposedInterfaceWrapper#detach` · `onHotReloading` / `onHotReloaded` · `XposedService#getRunningTargets` · `XposedService#hotReloadModule`

```java
// ✅ 三种通过 lint 的写法（三选一）
minApiVersion=102                                              // ① 最简单，推荐
if (getApiVersion() >= XposedInterface.API_102) { ... }        // ② 运行时守卫
@Override public boolean onHotReloading(...) { ... }           // ③ 重写更高 @SinceApi 的父类方法可豁免
```

> 接入方式：`libxposed:service` 通过 `lintPublish(io.github.libxposed:lint)` 自动下发 lint 规则。**接入后可在编译期自动拦截"用了高于 `minApiVersion` 的 API"与"调用了框架内部 API"两类错误。**

---

## 15. ⚠️ 已移除 / 已变更（防止被旧教程误导）

> 本节**只点名、不教学**。如果你在某处看到下面的名字，那是 API 100 / Legacy 的写法，102 已删除或已改名 —— 请改用右列，不要照抄旧写法。

### 15.1 API 100 的 Hook 模型（101 起彻底删除）

| 如果你看到 | 状态 | 改用 |
|---|---|---|
| `BeforeHookCallback<T>` / `AfterHookCallback<T>` | **API 101 起删除** | `Chain` + `Hooker`（→ §4） |
| `BeforeHooker<T>` / `AfterHooker<T>` / `Hooker<T>`（泛型） | **删除** | `Hooker`（非泛型，SAM：`Object intercept(Chain)`） |
| `MethodUnhooker<T,U>` | **删除** | `HookHandle`（→ §4.4） |
| `hookBefore` / `hookAfter` / 12 个 `hook*` 重载 | **删除** | 唯一的 `hook(Executable)`（→ §3.3） |
| `invokeOrigin` / `newInstanceOrigin` | **删除** | `getInvoker(...).setType(Invoker.Type.ORIGIN)`（→ §5） |
| `parseDex` / `DexParser` / `featuredMethod` | **删除** | 无对应。自行 JDK 反射 |
| `getFrameworkPrivilege()` | **删除** | `getFrameworkProperties()` 位掩码（→ §2.3） |
| `XposedContext` / `XposedContextWrapper` | **删除** | `XposedInterface` / `XposedInterfaceWrapper` |
| `getSharedPreferences` / `openFileInput` / `fileList` / `getResources` / `getClassLoader` / `getApplicationInfo`（Context 风格） | **删除** | `getRemotePreferences` / `openRemoteFile` / `listRemoteFiles` / `getModuleApplicationInfo`（→ §8） |
| `log(String)` / `log(String, Throwable)` | **删除** | `log(int priority, String tag, String msg[, Throwable])`（→ §7） |
| `XposedModule(XposedContext base, ModuleLoadedParam param)` 构造器 | **101 起移除** | **公开无参构造器** + 框架 `attachFramework()` 注入（→ §10.1） |
| `onSystemServerLoaded` / `SystemServerLoadedParam` | **改名** | `onSystemServerStarting` / `SystemServerStartingParam` |
| `PackageLoadedParam#getAppInfo()` | **改名** | `getApplicationInfo()` |
| `io.github.libxposed.api.errors`（复数） | **改成单数 `error`** | `io.github.libxposed.api.error`（→ §14） |
| `PRIORITY_LOWEST/HIGHEST = ±10000` | **改成 `Integer.MIN/MAX_VALUE`** | 用常量，别硬编码（→ §2.2） |
| `@XposedHooker` 注解 | **从未存在** | 102 的 hook **不需要任何注解** |
| `io.github.libxposed.api.annotations.*` | **该包从未存在** | 注解实际在 `io.github.libxposed.annotation`（lint 仓库） |

### 15.2 Legacy `de.robv.android.xposed` API

> **可判定规则**：`targetApiVersion ≥ 102` 的模块**禁止**调用任何 legacy `de.robv.android.xposed` API（Javadoc 明文行为变更）。这是"承诺"不是"能力" —— 即使框架只实现 101，`target=102` 也会单方面施加该限制。

| Legacy | 102 的对应物 |
|---|---|
| `IXposedHookLoadPackage` / `handleLoadPackage` | `XposedModule` / `onPackageReady` |
| `assets/xposed_init` | `META-INF/xposed/java_init.list` |
| `xposedmodule` / `xposeddescription` / `xposedminversion` meta-data | `android:label` / `android:description` / `module.prop` 的 `minApiVersion` |
| `XposedBridge.hookMethod` | `hook(exec).setPriority(p).setExceptionMode(m).setId(id).intercept(hooker)` |
| `XC_MethodReplacement` | 拦截器里**不调 `proceed()`**，直接 `return 值` |
| `param.setResult(v)` / `param.returnEarly` | 不调 `proceed()` / 返回新值 |
| `param.setThrowable(t)` | `throw t;` |
| `XposedBridge.invokeOriginalMethod` | `getInvoker(m).setType(Invoker.Type.ORIGIN).invoke(...)` |
| `XposedBridge.log(String)` | `log(Log.INFO, tag, msg)` |
| `XSharedPreferences`（含 New XSharedPreferences） | `getRemotePreferences(group)`；大块数据用 `openRemoteFile(name)` |
| `XposedHelpers.findAndHookMethod` 等 | JDK 反射 + `hook(...)`；**不要用 `io.github.libxposed:helper`** |

> ⚠️ **`io.github.libxposed:helper` 禁止使用**：停在 `100.0.1`、最后提交 2023-02-12、依赖 `compileOnly("io.github.libxposed:api:100")`。它的 `HookBuilder` 建立在已被删除的 API 之上，**与 102 二进制不兼容**。替代：JDK 反射 + `getInvoker()`。【一手·源码】

---

## 未验证 / 未获取到

- **LSPosed v2.x 官方 release notes 全部未获取到**：`GET /repos/LSPosed/LSPosed/releases` 与 `/tags` 最新条目仍是 `v1.9.2`（2023-10-11），`v2.0.2` / `v2.1.1` / `v2.2.0` / `v2.3.0` 均 404。本册**不引用任何 v2.x changelog 作为硬约束**。
- **`deoptimize()` 的内部实现**（ART JIT 层还是 dex2oat 层、是否对已编译的 oat 生效）未从源码确认。
- **同优先级 hook 的 tie-break 规则**未见任何官方明文，§2.2 中的"顺序未定义"为【推断】。
- **`log()` 的输出是否同时进 logcat** 未确认（Javadoc 只说 "Writes a message to the Xposed log"）。
- **宿主侧对 `getRemotePreferences` 写入的具体失败形态**（静默失败 vs 抛异常）未取到一手证据，社区两说并存 → 本册一律按「只读」处理。
- **`module.prop` 的 `autoHotReload` 与 `exceptionMode` 的框架侧解析代码行**未取到；语义来自 `package-info.java` 与官方示例。
- **`proceedWith` 的 Javadoc 澄清**（commit `495ca5d`，2026-08-27）晚于 102.0.0 tag，§4.6 采用的是 master 措辞；102.0.0 原文更简略，行为一致但**措辞差异未逐字比对**。
- **dokka Javadoc 主站** `libxposed.github.io/api/` 不可达（`fetch failed`）。本册全部 API 事实取自 GitHub 源码原文，可信度 ≥ dokka。
- **`IXposedService.aidl` 的完整方法定义**仅从 `XposedService` 使用侧反推（不影响本册结论）。
- **每个方法的最大回调数量上限的具体数值**未获取到（只有镜像站的定性描述）。
