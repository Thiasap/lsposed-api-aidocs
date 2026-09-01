# 08 · XposedService、热重载与 Native Hook（进阶）

> **本册定位**：`io.github.libxposed:service:102.0.0` 完整参考 + API 102 热重载 + Native Hook 的官方路径与 ABI 风险。
> **何时读这一册**：① 模块 App（UI 侧）要与框架通信、申请作用域、读写远程配置时；② 要做热重载时；③ 需要做 native hook 时。
> **前置依赖**：→ 见 `03-api-reference.md`（先理解 `XposedInterface` 与 Hook 链）、`01-quick-reference.md §9`。
> **交叉引用**：→ 见 `03-api-reference.md §8`（宿主侧的远程数据三方法）、`02-project-scaffold.md`、`04-lifecycle-and-patterns.md`

**基准**：`io.github.libxposed:service:102.0.0`（`libxposed/service` master，最后提交 `3318940` "RFC for API 102 (#15)"，2026-06-13）。
**可信度标注**：`【一手·源码】` / `【一手·文档】` / `【二手】` / `【推断】`。

> **两条贯穿全册的提醒**：
> 1. `XposedService.getApiVersion()` 是 **service IPC 版本**，`XposedInterface.getApiVersion()` 是 **libxposed 模块 API 版本**。数值都是 101/102，但**语义独立、常量分属两个类**，不要交叉使用。
> 2. **LSPatch 免 Root 不在本册范围**。`XposedService` 的可用性前提是"存在一个独立的框架/管理器进程向你推 binder"；embedded 形态（模块被打包进目标 APK）缺少推送方，相关内容仅作为降级提示一句带过（→ §1.3.4）。

---

## 1. `XposedService` 完整参考

### 1.1 坐标与定位

| 角色 | 坐标 | 依赖方式 |
|---|---|---|
| **模块开发者（你）** | `io.github.libxposed:service:102.0.0` | `implementation(...)`（必须打进 APK） |
| 框架开发者 | `io.github.libxposed:interface:102.0.0` | `implementation(...)` |

【一手·源码】

```kotlin
// app/build.gradle.kts
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")          // 模块侧 API：compileOnly
    implementation("io.github.libxposed:service:102.0.0")   // service：implementation
}
```

类声明：`public final class XposedService` —— **不可继承**，构造为**包私有**，实例**只能**由 `XposedServiceHelper` 通过回调交付（→ §2）。

包结构（`service/src/main/java/io/github/libxposed/service/` 下**只有 6 个 java 文件**）：

```
XposedService.java          模块使用的门面类（final）
XposedServiceHelper.java    注册入口 + OnServiceListener
XposedProvider.java         exported ContentProvider，接收框架推送的 IBinder
RemotePreferences.java      SharedPreferences 实现（模块 App 侧可读可写）
HookedTarget.java           @SinceApi(102) 被 hook 进程快照
HotReloadResult.java        @SinceApi(102) record(Status, String)
```

### 1.2 常量

| 常量 | 类型 | 值 | 语义 |
|---|---|---|---|
| `XposedService.API_101` | `int` | `101` | service IPC 版本 101 |
| `XposedService.API_102` | `int` | `102` | service IPC 版本 102 |
| `XposedService.PROP_CAP_SYSTEM` | `long` | `1L` | 框架可 hook system_server 等系统进程 |
| `XposedService.PROP_CAP_REMOTE` | `long` | `1L << 1` | 框架支持 remote preferences / remote files |
| `XposedService.PROP_RT_API_PROTECTION` | `long` | `1L << 2` | 框架禁止通过反射 / 动态加载代码访问 Xposed API |

【一手·源码】

> ⚠️ `XposedService` **未暴露 `LIB_API`**。该常量只存在于 AIDL 侧（`IXposedService.LIB_API = API_102`）与 `XposedInterface.LIB_API`。
> ⚠️ 用 `service.getFrameworkProperties()` 判断时，必须用 **`XposedService.PROP_CAP_*`**，不要用 `XposedInterface` 的同名常量（值虽相同，但语义归属不同，混用会被 lint / 阅读者质疑）。

### 1.3 15 个方法逐个详解

#### 1.3.1 框架信息（5 个）

| 签名 | 返回 | 异常 |
|---|---|---|
| `int getApiVersion()` | 当前实现的 **service IPC 版本** | `ServiceException` |
| `@NonNull String getFrameworkName()` | 框架名（`LSPosed` / `Vector`） | `ServiceException` |
| `@NonNull String getFrameworkVersion()` | 框架版本字符串 | `ServiceException` |
| `long getFrameworkVersionCode()` | 框架版本号（数值） | `ServiceException` |
| `long getFrameworkProperties()` | 能力位掩码 | `ServiceException` |

【一手·源码】

**注意**：`getApiVersion()` 内部被 `requireApi(int)` 调用，因此 `getRunningTargets()` / `hotReloadModule()` 在 **service 已死时会先抛 `ServiceException`** 而不是 `UnsupportedOperationException`。

```kotlin
// app/src/main/java/com/example/ui/MainActivity.kt
val api = service.apiVersion                      // 101 或 102
val props = service.frameworkProperties
val hasRemote = (props and XposedService.PROP_CAP_REMOTE) != 0L
val hasSystem = (props and XposedService.PROP_CAP_SYSTEM) != 0L
if (api >= XposedService.API_102) { /* 可用 102 能力 */ }
```

> **必须**：`PROP_RT_` 前缀的属性**每次启动都可能变化**（Javadoc 原文）→ **每会话重读，禁止缓存到磁盘当永久能力判断**。

#### 1.3.2 作用域（3 个）

**`getScope()`**

```java
@NonNull public List<String> getScope() throws ServiceException;
```
- 返回当前模块的作用域包名列表。
- ⚠️ **同步 Binder 调用，不得在主线程调用**（会阻塞主线程 → ANR）。
- ⚠️ **不存在作用域变更推送通道**。想感知变化只能主动轮询 `getScope()`。

**`requestScope(List<String>, OnScopeEventListener)`** ⭐

```java
public void requestScope(@NonNull List<String> packages,
                         @NonNull OnScopeEventListener callback) throws ServiceException;
```

| 维度 | 事实 |
|---|---|
| 异步性 | **AIDL 声明为 `oneway`** —— 方法立即返回，**不阻塞**，结果通过 `callback` 送达 |
| 参数 | **只有两个**：包名列表 + 回调。**没有 `optional` / `required` 语义**（legacy 管理器 UI 上的概念未进入 libxposed 契约） |
| 回调 | `onScopeRequestApproved(List<String> approved)` / `onScopeRequestFailed(String message)`，两者都是 `default` 空实现 |
| **一次性** | ⚠️ 内部的 `IXposedScopeCallback.Stub` 在任一回调触发后会 `scopeCallbacks.remove(listener)` —— **一个 listener 实例用完即弃，第二次请求必须传新实例** |
| 回调线程 | **Binder 线程** → 触碰 UI 必须 `runOnUiThread` |
| 异常 | `ServiceException` |

【一手·源码】

**`removeScope(List<String>)`**

```java
public void removeScope(@NonNull List<String> packages) throws ServiceException;
```
- **同步**（AIDL 非 oneway），**无回调**。
- 与 `requestScope` 的不对称是设计意图：移除是幂等的管理操作，不需要用户确认流程。

```kotlin
// app/src/main/java/com/example/ui/MainActivity.kt
// ⚠️ 每次 requestScope 都要传新的 listener 实例（一次性语义）
binding.requestScope.setOnClickListener {
    service.requestScope(listOf("com.android.settings"), object : OnScopeEventListener {
        override fun onScopeRequestApproved(approved: List<String>) {
            runOnUiThread { binding.scope.text = "Approved: $approved" }   // Binder 线程 → 切主线程
        }
        override fun onScopeRequestFailed(message: String) {
            runOnUiThread { binding.scope.text = "Failed: $message" }
        }
    })
}

// 移除：同步、无回调。放后台线程执行
executor.execute {
    try { service.removeScope(listOf("com.android.settings")) }
    catch (e: XposedService.ServiceException) { Log.e(TAG, "removeScope failed", e) }
}
```

#### 1.3.3 热重载（2 个，`@SinceApi(API_102)`）

**`getRunningTargets()`**

```java
@SinceApi(API_102)
@NonNull public List<HookedTarget> getRunningTargets()
        throws UnsupportedOperationException, ServiceException;
```
- 返回当前被本模块 hook 的**运行中进程**列表（不可修改的 `List<HookedTarget>`），可原样传给 `hotReloadModule(...)`。
- Javadoc 原文：**`pid`、`uid`、`processName`、`loadedVersionCode` 仅用于展示与诊断，不作为目标身份**。
- 异常：`UnsupportedOperationException`（框架 service API < 102，由 `requireApi(API_102)` 抛出）、`ServiceException`。
- ⚠️ **同步 Binder 调用，不得在主线程**。

**`hotReloadModule(HookedTarget, Bundle, HotReloadCallback)`**

```java
@SinceApi(API_102)
public void hotReloadModule(@NonNull HookedTarget target,
                            @Nullable Bundle data,
                            @NonNull HotReloadCallback callback)
        throws SecurityException, ServiceException, UnsupportedOperationException;
```
- 方法**只做校验与提交**，真实结果**异步**通过 `callback` 送达。
- Javadoc 明文约束（逐条）：
  1. `data` 只能放 **classloader-neutral** 的值（能脱离模块 classloader 反序列化）；**禁止**放模块自定义的 `Parcelable` / `Serializable`。
  2. 用途限定为「模块 APK 更新后加载新代码」，**不应**用于传播配置变更 —— 配置变更请用 `getRemotePreferences(...)` + `OnSharedPreferenceChangeListener`。
  3. 旧模块从 `onHotReloading` 返回 `false` → `Status.FAILED` 且 `message == null`。
  4. 重载抛异常 → `Status.FAILED` 且 `message` 为框架诊断串。
  5. 框架无法为该目标提供有效新代 → `Status.UNSUPPORTED`。
- `target` / `callback` 为 `null` 时：`Objects.requireNonNull` → `NullPointerException`（在 `requireApi` 之后）。
- 异常：`SecurityException`（目标非法或不再属于本模块）、`ServiceException`（service 已死）、`UnsupportedOperationException`（service API < 102）。
- 回调线程：**Binder 线程**（Javadoc："This callback may run on a Binder thread. Dispatch to the main thread before touching UI."）

【一手·源码】

```kotlin
// app/src/main/java/com/example/ui/MainActivity.kt
if (service.apiVersion >= XposedService.API_102) {
    executor.execute {                                     // 同步 Binder → 后台线程
        val targets = try { service.runningTargets } catch (e: Exception) { emptyList(); return@execute }
        runOnUiThread {
            for (target in targets) {
                service.hotReloadModule(target, null) { p, r -> // data = null
                    runOnUiThread {                             // 回调在 Binder 线程
                        Toast.makeText(this, "Reload ${p.processName} -> ${r.status} ${r.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
```

**`XposedService.HotReloadCallback`**

```java
@SinceApi(XposedService.API_102)
public interface HotReloadCallback {
    void onHotReloadResult(@NonNull HookedTarget target, @NonNull HotReloadResult result);
}
```
- **非 `default`，必须实现**。

**`HookedTarget`（final，@SinceApi(102)）**

```java
public final class HookedTarget {
    public enum State { UP_TO_DATE, STALE, RELOADING, FAILED }
    public int getUid();                // 仅展示/诊断
    public int getPid();                // ⚠️ 仅诊断用，不得作为目标身份
    @NonNull public String getProcessName();
    @NonNull public State getState();
    public long getLoadedVersionCode(); // 仅诊断值
}
```

| `State` | 含义 |
|---|---|
| `UP_TO_DATE` | 正在运行当前已安装的模块代码 |
| `STALE` | 仍在跑旧代码，**可被热重载** |
| `RELOADING` | 正在重载中 |
| `FAILED` | 上次重载失败（旧模块拒绝 或 抛异常） |

- 类 Javadoc：**"Instances are snapshot handles and do not define stable value equality."** → 不要依赖 `equals`，**不要跨调用长期缓存**，只能整对象回传给 `hotReloadModule`。
- `targetId` 是**包私有**字段，模块不可见 —— 印证了"只能原样回传"的设计。

**`HotReloadResult`（record，@SinceApi(102)）**

```java
public record HotReloadResult(@NonNull Status status, @Nullable String message) {
    public enum Status { SUCCEEDED, FAILED, UNSUPPORTED, IN_PROGRESS, PROCESS_DIED }
}
```

| Status | 含义 | `message` |
|---|---|---|
| `SUCCEEDED` | 重载成功 | — |
| `FAILED` | 旧模块拒绝重载，或重载过程抛异常 | `null` = 旧模块 `onHotReloading` 返回 `false`；非 null = 框架诊断串 |
| `UNSUPPORTED` | 目标不支持热重载（如模块未声明**恰好一个** Java 入口类，或框架无法提供有效新代） | — |
| `IN_PROGRESS` | 该目标已在重载中 | — |
| `PROCESS_DIED` | 目标进程在重载完成前死亡 | — |

【一手·源码】

#### 1.3.4 远程存储（5 个）

**`getRemotePreferences(group)`** ⭐

```java
@NonNull public synchronized SharedPreferences getRemotePreferences(@NonNull String group)
        throws ServiceException, UnsupportedOperationException;
```
- 取得（**不存在则创建**）指定 group 的远程 preferences。**模块 App 侧可读可写**（返回 `RemotePreferences`，实现完整 `SharedPreferences.Editor`）。
- 按 group 缓存在 `XposedService.mRemotePrefs`（`HashMap`，方法级 `synchronized`）。
- 异常：`ServiceException`（service 已死 / 框架返回 null）、`UnsupportedOperationException`（框架不具备 remote 能力，即 `PROP_CAP_REMOTE` 未置位；embedded 形态即属此类）。

**`deleteRemotePreferences(group)`**

```java
public synchronized void deleteRemotePreferences(@NonNull String group)
        throws ServiceException, UnsupportedOperationException;
```
- 删除整组远程 preferences；本地缓存同步清空。

**`listRemoteFiles()`**

```java
@NonNull public String[] listRemoteFiles()
        throws ServiceException, UnsupportedOperationException;
```
- 列出模块共享数据目录下的所有文件。⚠️ 同步 Binder，**不得在主线程**。

**`openRemoteFile(name)`** ⭐

```java
@NonNull public ParcelFileDescriptor openRemoteFile(@NonNull String name)
        throws ServiceException, UnsupportedOperationException;
```
- 打开模块共享数据目录中的文件，**不存在则创建**，可写。
- `name` 约束：**不得包含路径分隔符，不得包含 `.` 或 `..`**。

**`deleteRemoteFile(name)`**

```java
public boolean deleteRemoteFile(@NonNull String name)
        throws ServiceException, UnsupportedOperationException;
```
- 删除文件；成功 `true`，文件不存在 `false`。

【一手·源码】

### 1.4 ⭐ `getRemotePreferences` 读写差异 —— 对照代码

**这是本册最容易被搞错的一点。同一个方法名，在两个进程里权限不同。**

```kotlin
// ─────────────────────────────────────────────────────────────────
// A. 模块 App 进程（你的 UI / Application）—— 可读可写
// app/src/main/java/com/example/ui/MainActivity.kt
// ─────────────────────────────────────────────────────────────────
val prefs = service.getRemotePreferences("conf")      // 不存在则创建
prefs.edit().putBoolean("enabled", true).apply()      // ✅ 写：只有这里能写
val v = prefs.getBoolean("enabled", false)            // ✅ 读
```

```kotlin
// ─────────────────────────────────────────────────────────────────
// B. 被 hook 的宿主进程（XposedModule 子类里）—— 只读
// app/src/main/java/com/example/ModuleMain.kt
// ─────────────────────────────────────────────────────────────────
override fun onPackageReady(param: PackageReadyParam) {
    if (!param.isFirstPackage) return
    if ((frameworkProperties and PROP_CAP_REMOTE) == 0L) return   // 先探测能力位

    val prefs = getRemotePreferences("conf")                      // 同一个 group
    val v = prefs.getBoolean("enabled", false)                    // ✅ 读
    // ❌ prefs.edit().putBoolean(...).apply()  → 宿主进程内只读，写了不生效

    prefs.registerOnSharedPreferenceChangeListener { p, key ->     // ✅ 感知变更（有框架推送通道）
        log(Log.INFO, TAG, "conf changed: $key")
    }
}
```

| 能力 | `XposedService`（模块 App 进程） | `XposedInterface`（被 hook 的宿主进程） |
|---|---|---|
| `getRemotePreferences(group)` | **可读可写**；group 不存在则创建 | **只读**（Javadoc 原文："those are read-only in hooked apps"） |
| 变更监听 | ⚠️ **只能感知本地编辑**，**没有跨进程推送通道** | ✅ 有框架推送通道，能感知外部变更 |
| `deleteRemotePreferences` | ✅ 有 | ❌ 无 |
| `listRemoteFiles()` | ✅ | ✅ |
| `openRemoteFile(name)` | ✅ **不存在则创建，可写** | ✅ **只读**；不存在抛 `FileNotFoundException` |
| `deleteRemoteFile(name)` | ✅ 有 | ❌ 无 |
| hook / deoptimize / log | ❌ | ✅ |

【一手·源码】

> ⚠️ **模块 App 侧的 `RemotePreferences` 没有跨进程推送**：被 hook app / 管理器改了远端值，模块 App 的 listener **不会**收到通知（`RemotePreferences.java` 中没有任何来自框架的回调入口）。需要感知外部变更时，必须自行在合适时机（如 `onStart`）重新 `getRemotePreferences(group)` 读值。
> ⚠️ 官方 Wiki 的对照表把 Remote Preferences 的「变更监听器」标为 ✅，那是**指被 hook app 侧**。这张表极易被误读成双向。

**`RemotePreferences` 的行为细节**（【一手·源码】）：

| 行为 | 事实 |
|---|---|
| `getAll()` | 返回 `new TreeMap<>(mMap)`（有序副本） |
| `getStringSet(key, def)` | **返回内部 Set 本身**（未做防御性拷贝）—— 不要就地 mutate 期望其持久化 |
| `putString(key, null)` / `putStringSet(key, null)` | **等价于 `remove(key)`** |
| `commit()` | 先本地 `doUpdate()`，再**同步** `updateRemotePreferences(group, bundle)`；失败仅 `Log.e` 并返回 `false` |
| `apply()` | 先本地 `doUpdate()`，再在**单线程 Executor** 上异步提交；**无结果回调**，失败只打日志 |
| `clear()` | 在 `SDK_INT >= R && targetSdk >= R` 时会向 listener 发 `onSharedPreferenceChanged(prefs, null)`（key 为 null） |

### 1.5 `XposedService.ServiceException`

```java
public static final class ServiceException extends RuntimeException
```

| 维度 | 事实 |
|---|---|
| 构造器 | **包私有** —— 模块只能 `catch`，**不能 `new`** |
| 两种成因 | `ServiceException(String message)`（框架返回非法值）、`ServiceException(RemoteException e)`（message 固定为 `"Xposed service error"`，cause 为原始 `RemoteException`） |
| 基类 | `RuntimeException` —— **编译器不强制你 catch**，所以必须自己记得加 |

【一手·源码】

**可判定规则**：**每一个 `XposedService` 调用都要套 `try/catch (XposedService.ServiceException)`**。

> ⚠️ 官方 example 的 `MainActivity.kt` **没有任何一处 catch `ServiceException`** —— service 在两次调用之间死亡会让整个 Activity 崩溃。**不要照抄这一处**。

### 1.6 不存在的能力（明确否定，穷举后结论）

逐行核对 `XposedService.java` 全文后，API 102 中**不存在**：

| 你可能期待的 | 实际情况 |
|---|---|
| `IServiceCallback` 注册 / 注销 | ❌ 不存在。同名标识符出现在 Vector **内部** IPC 重构（canary 3103 / PR #926），**不是** `libxposed/service` 的东西。模块代码不需要、也不应该引用 |
| 作用域变更事件订阅 | ❌ 不存在。只能主动 `getScope()` 轮询 |
| 模块启用 / 禁用状态查询 | ❌ 不存在 |
| 已安装模块列表 | ❌ 不存在 |
| 资源（resources）相关事件 | ❌ 不存在（资源 Hook 已被官方移除，→ 见 `03-api-reference.md §9`） |
| 主动 `connect()` / `disconnect()` | ❌ 不存在。连接由框架推送，断开由 `linkToDeath` 感知 |
| 连接失败 / 超时回调 | ❌ 不存在。只有 `onServiceBind` / `onServiceDied` |
| `getServiceVersion()` | ❌ 不存在。对应物是 `getApiVersion()`（service API 版本）+ `getFrameworkVersion()`（框架版本） |

【一手·源码】

---

## 2. `XposedServiceHelper` 与 Binder 推送机制

### 2.1 架构：谁连谁（**模块不发起 bind**）

方向是 **框架 → 模块** 的**单向推送**。

```
┌──────────────────────┐                        ┌────────────────────────────┐
│  Xposed 框架 / 管理器  │                        │   模块 App（你写的那个 APK） │
│  (daemon / manager)   │                        │                            │
└──────────┬───────────┘                        └─────────────┬──────────────┘
           │                                                  │
           │  1. 模块启动，Application.onCreate() 里            │
           │     XposedServiceHelper.registerListener(this)    │
           │                                    ◄─────────────┤
           │                                                  │
           │  2. 框架查询 authority = <applicationId>.XposedService
           │     并调用 ContentProvider.call("SendBinder", null, extras)
           │     extras 中 key "binder" 携带 IXposedService 的 IBinder
           ├─────────────────────────────────────────────────►│
           │                                                  │  XposedProvider.call()
           │                                                  │  → XposedServiceHelper.onBinderReceived(binder)
           │                                                  │  → new XposedService(IXposedService.Stub.asInterface(binder))
           │                                                  │  → binder.linkToDeath(...)
           │                                                  │  → listener.onServiceBind(service)
           │                                                  │
           │  3. 模块持有 XposedService，调用 AIDL 方法（同步 Binder 调用）
           │◄─────────────────────────────────────────────────┤
           │                                                  │
           │  4. binder 死亡 → linkToDeath → listener.onServiceDied(service)
           ├─────────────────────────────────────────────────►│
```

关键常量（`IXposedService.aidl`，【一手·源码】）：

```aidl
const String AUTHORITY_SUFFIX = ".XposedService";
const String SEND_BINDER      = "SendBinder";
```

`XposedProvider.call()` 源码原文：

```java
@Nullable @Override
public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
    if (method.equals(IXposedService.SEND_BINDER) && extras != null) {
        IBinder binder = extras.getBinder("binder");
        if (binder != null) {
            Log.d(TAG, "binder received: " + binder);
            XposedServiceHelper.onBinderReceived(binder);
        }
        return new Bundle();
    }
    return null;
}
```

> **推论（架构师判断，【推断】）**：因为方向是"框架呼叫模块的 exported Provider"，所以模块**不需要** `<queries>`、不需要任何权限、也不需要声明 `<service>`。这与 legacy LSPosed 时代"模块主动 bind 框架服务"的直觉相反。

### 2.2 `registerListener` 的使用契约

```java
public final class XposedServiceHelper {
    public interface OnServiceListener {
        void onServiceBind(@NonNull XposedService service);   // 可能被多次调用（多框架共存）
        void onServiceDied(@NonNull XposedService service);
    }
    public static void registerListener(OnServiceListener listener);
    // 包私有：static void onBinderReceived(IBinder binder)
}
```

| 维度 | 事实（**可判定规则**） |
|---|---|
| **在哪里注册** | 模块 `Application.onCreate()`。**官方 example 就是这么做的** |
| **调用几次** | ⚠️ **只应调用一次**（javadoc 原文："This method should only be called once."）。`mListener` 是**单个 static 字段**，第二次调用会**覆盖**第一次，**旧 listener 永久失联** |
| 是否异步 | 是。`registerListener()` 立即返回（void），`onServiceBind` 在之后由框架推送时触发 |
| 缓冲机制 | 若 binder 先到、listener 后注册，service 会暂存在 `mCache`（`HashSet`），`registerListener` 时统一回放并 `linkToDeath`。**因此不存在"先 onCreate 再 register"的竞态** |
| 线程安全 | `onBinderReceived` 与 `registerListener` 均在 `synchronized (mCache)` 内 —— 线程安全 |
| ⚠️ **回调线程** | **回调本身在 Binder 线程**。触碰 UI **必须** `runOnUiThread` / `Handler(Looper.getMainLooper()).post` |
| 连接失败处理 | **没有**失败回调、**没有**超时、**没有**重试。若没有框架推送 binder，`onServiceBind` **永远不会被调用** → 模块必须持有 `XposedService? = null` 的"未连接"态并做降级 |
| 断开处理 | `binder.linkToDeath(...)` → `onServiceDied(service)`，应把引用置 null |
| 多框架 | javadoc："This method could be called multiple times if multiple Xposed frameworks exist." → **需要考虑重复 `onServiceBind`** |
| 运行时异常 | 每个 `XposedService` 方法都可能抛 `ServiceException`，必须 catch |

【一手·源码】

### 2.3 推荐模式（可直接复制）

> 把「可能为空的 service」包成**可观察状态**，Activity 在 `onStart` 注册、`onStop` 注销，并用 `notifyImmediately = true` 处理「service 比 Activity 先到」的情况。

```kotlin
// app/src/main/java/com/example/App.kt
package com.example

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        @Volatile
        var mService: XposedService? = null
            private set

        private val serviceStateListeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            serviceStateListeners.add(listener)
            if (notifyImmediately) listener.onServiceStateChanged(mService)
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            serviceStateListeners.remove(listener)
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)   // ⚠️ 只在 Application.onCreate 调一次
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        serviceStateListeners.forEach { it.onServiceStateChanged(service) }
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        serviceStateListeners.forEach { it.onServiceStateChanged(null) }
    }
}
```

```kotlin
// app/src/main/java/com/example/ui/MainActivity.kt（片段）
private val stateListener = App.ServiceStateListener { service ->
    runOnUiThread {
        if (service == null) { binding.binder.text = "Binder is null"; return@runOnUiThread }
        binding.binder.text = "Binder acquired"
        // ⚠️ 下面这些是同步 Binder 调用 —— 生产代码应放后台线程，只在拿到结果后切回主线程
        binding.api.text = "API " + service.apiVersion
    }
}

override fun onStart() {
    super.onStart()
    App.addServiceStateListener(stateListener, notifyImmediately = true)
}

override fun onStop() {
    App.removeServiceStateListener(stateListener)
    super.onStop()
}
```

> ⚠️ **官方 example 自身有两个隐患，建议不要照抄这两处**：
> 1. `service.runningTargets` / `service.scope` / `service.openRemoteFile(...)` 在 `runOnUiThread { }` 内部被**同步调用** —— 这些是非 oneway 的 Binder 调用，**阻塞主线程**。
> 2. 官方 example **没有任何一处 `try/catch (XposedService.ServiceException)`**。

---

## 3. AndroidManifest 声明

### 结论：**什么都不用手写**

library 自带 `service/src/main/AndroidManifest.xml`（【一手·源码】）：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application>
        <provider
            android:name=".XposedProvider"
            android:authorities="${applicationId}.XposedService"
            android:exported="true"
            tools:ignore="ExportedContentProvider" />
    </application>
</manifest>
```

经 AGP **manifest merger** 自动合并进模块 APK，`${applicationId}` 占位符由 merger 替换。

### 逐项对照表

| 声明项 | 是否需要 | 说明 |
|---|---|---|
| `<provider .XposedProvider exported=true>` | **自动** | library manifest 合并；authority = `<applicationId>.XposedService` |
| `<uses-permission>` | ❌ 不需要 | 通信由框架发起，模块是**被动接收方** |
| `<queries>` | ❌ 不需要 | 模块不主动查询 / 绑定其他包 |
| `<service>` | ❌ 不需要 | 走 ContentProvider，不是 bound service |
| `android:exported`（provider） | **必须为 `true`** | 否则框架无法 `call()` 进来（已由 library 设置，**勿覆盖**） |
| ⚠️ `minSdk` | **必须 ≥ 26** | library `minSdk = 26`，低于此值 merger **直接失败**：`uses-sdk:minSdkVersion X cannot be smaller than version 26 declared in library` |
| `compileSdk` / `buildTools` | 建议 ≥ 37 / `37.0.0` | library 以此构建 |
| Java 版本 | ≥ 17 | library `sourceCompatibility/targetCompatibility = VERSION_17`（example 用 21） |

### 硬约束与易漏点

1. ⚠️ **不要**在自己的 manifest 里重复声明该 provider，**不要** `tools:replace="android:authorities"`，**不要** `tools:node="remove"`，**不要**用 `android:exported="false"` 覆盖。
   **写错 = 永远收不到 binder = `mService` 永远是 null，且没有任何错误提示。**
2. authority 必须与框架查找规则 `<applicationId>.XposedService`（`AUTHORITY_SUFFIX`）一致。
3. applicationId 与包名不一致时（`applicationIdSuffix`、productFlavors），authority 自动跟随 `applicationId` —— 框架查找也用 applicationId，**debug/release 变体无需特殊处理**。
4. 开启 R8/minify 的模块：library 通过 `consumerProguardFiles` 带入 `-dontwarn io.github.libxposed.annotation.**`。`XposedProvider` 是四大组件，AGP 默认会保留 manifest 中声明的组件；若你用了激进的自定义规则，请确认 provider 未被移除。
5. 模块 App **自身不再被 hook**（官方 Wiki 原文："module apps are no longer hooked by themselves."）—— **不要**指望在模块自己的进程里用 `XposedInterface` 做 hook。
6. 模块名取 `android:label`，描述取 `android:description`。**不要**写 `xposedmodule` / `xposeddescription` / `xposedminversion` meta-data（Legacy 残留，且会让管理器把你判为 Legacy 模块）。

---

## 4. 热重载（API 102）

### 4.1 生命周期

```
旧代码                                    框架                              新代码
───────                                  ──────                            ───────
onHotReloading(HotReloadingParam)  ◄────── 请求重载
   ├─ 运行在「旧」代码中
   ├─ 可 setSavedInstanceState(...)
   ├─ return false → 拒绝（Status.FAILED, message=null）
   └─ return true  → 放行（清理义务见 §4.2）
                                    冻结旧代码（此后旧代码再注册 hook 会失败）
                                    捕获 oldHookHandles
                                    卸载旧代 / 加载新代
                                    实例化新入口类 → attachFramework
                                          ──────────────────────────────►
                                                                    onHotReloaded(HotReloadedParam)
                                                                       ├─ 运行在「新」代码中
                                                                       ├─ getExtras() / getSavedInstanceState()
                                                                       ├─ getOldHookHandles()
                                                                       └─ 默认实现：全部 unhook
                                    框架在此回调结束前保持旧代强可达
```

Javadoc 要点（【一手·源码】）：

- `onHotReloading`："<p>This callback runs in **old** code.</p>"；"Hot reloads are serialized per target. Before the old hook handle list is captured, the framework **freezes old code so further hook registrations from old code fail**. In-flight hook calls keep using the hook chain snapshot that was active when they started."
- `onHotReloaded`："<p>This callback runs in **new** code.</p>"；"The framework keeps the previous module generation strongly reachable until this callback finishes."

### 4.2 `onHotReloading` 返回 `true` / `false` 的后果

| 返回 | 后果 |
|---|---|
| **`false`（默认实现）** | **拒绝热重载**。Javadoc 明文："Returning `false` rejects the hot reload request. For service-triggered requests, this is reported as `HotReloadResult.Status.FAILED` with a **null message**."<br>另：**"App-update hot reloading still proceeds only when `onHotReloading()` returns `true`."** —— 即 `autoHotReload=true` 也不够 |
| `true` | 放行。**返回前必须完成强制清理（见下）**，否则是模块 bug |

**返回 `true` 前的强制清理义务**（Javadoc 逐条，【一手·源码】）：

1. stop all module-owned **Java and native threads**；
2. **unregister native hooks** and external callbacks；
3. release **JNI global references** to module-classloader objects；
4. clear references to module objects stored by system or app classes。

> ⚠️ 反模式：只 `return true` 不清理 → 旧代被新代替换后，旧线程继续跑在已卸载的 ClassLoader 上 → 崩溃。
> Javadoc 还明确："The framework does **not** call `UnregisterNatives`, `JNI_OnUnload`, or `dlclose` as part of hot reload. If all old Java references are removed while old native threads, callbacks, hooks, or JNI global references are still active, **any resulting crash or undefined behavior is a module bug**."

```kotlin
// app/src/main/java/com/example/ModuleMain.kt
override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
    log(Log.INFO, TAG, "onHotReloading, extras=" + param.extras)
    // ① 停掉模块自有的线程
    workerThread?.quitSafely(); workerThread = null
    // ② 注销 native hook / 外部回调（若用了 native，见 §5.5）
    // ③ 释放 JNI 全局引用
    // ④ 清掉被 system / app 类持有的模块对象引用
    param.savedInstanceState = null        // 只能放 classloader 中立的值
    return true                            // ⚠️ 不重写 = 默认 false = 永远拒绝
}
```

### 4.3 ⭐ 生命周期回调不重放（**最大陷阱**）

Javadoc 明文（【一手·源码】）：

- `onModuleLoaded`："Hot reload does **not automatically replay** this callback or package lifecycle callbacks; modules that opt into hot reload should override `onHotReloaded(...)` and explicitly install or replace the hooks they need."
- `onHotReloaded`："Package lifecycle callbacks are **not automatically replayed** after hot reload."；"The **default implementation only unhooks all old hooks**."

> ⚠️ **症状**：热重载成功（日志显示 `onHotReloaded`），但模块功能**全部消失** —— 因为默认实现把你所有的旧 hook 都 unhook 了，而 `onPackageReady` 不会再跑一次。

**正确写法：用 hook id 做原子替换**（可直接复制）：

```java
// app/src/main/java/com/example/ModuleMain.java
package com.example.mymodule;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ModuleMain extends XposedModule {

    private static final String TAG = "MyModule";

    /** hook id -> 该 id 对应的「当前」Hooker。register 时写入，热重载时用于替换。 */
    private final Map<String, XposedInterface.Hooker> mHookers = new HashMap<>();

    /** 统一注册入口：始终带 id，并记住 hooker 以便热重载替换。 */
    private void register(@NonNull String id,
                          @NonNull Executable exec,
                          @NonNull XposedInterface.Hooker hooker) {
        mHookers.put(id, hooker);
        hook(exec)
                .setId(id)                                              // 带 id → 重复注册自动原子替换
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker);
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        log(Log.INFO, TAG, "onHotReloaded: " + param.getProcessName()
                + ", " + param.getOldHookHandles().size() + " old hooks");

        // ⚠️ 生命周期回调不重放 —— 必须自己把旧 hook 换成新代码
        for (XposedInterface.HookHandle old : param.getOldHookHandles()) {
            String id = old.getId();
            XposedInterface.Hooker fresh = (id == null) ? null : mHookers.get(id);
            try {
                if (fresh != null) {
                    // 原子替换：保留 executable / priority / exceptionMode / id；成功后 old 失效
                    old.replaceHook(fresh);
                } else {
                    old.unhook();      // 新代码不再需要的 hook
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "replace hook failed: " + id, t);
                old.unhook();          // 兜底：移除，避免残留
            }
        }
    }
}
```

**旧 HookHandle 的四种处理方式对比**：

| 做法 | 效果 |
|---|---|
| 什么都不做（用默认实现） | 全部 `unhook()` → **功能消失** |
| `old.unhook()` | 移除该 hook（幂等） |
| `old.replaceHook(newHooker)` | ⭐ **原子替换**：保留 executable / priority / exceptionMode / id，只换 hooker；成功后 `old` 失效 |
| `hook(exec).setId(sameId).intercept(newHooker)` | 等价效果（带 id 时目标就是同一个 hook） |

> Hook 链是**快照式**的：替换 hook 时正在飞行中的那次调用不受影响。

### 4.4 `setSavedInstanceState` 的约束

Javadoc（【一手·源码】）：

> "The saved state **must not contain objects created under the old module classloader** because retaining them in the new generation can keep the old generation strongly reachable after hot reload. Use classloader-neutral values to transfer state."
> `@throws IllegalArgumentException if outState contains an object detected as being created under the old module classloader`

**可判定规则**：只放 primitive / `String` / 数组 / framework `Bundle`。

```java
// ✅ 合法
param.setSavedInstanceState("Hello from last generation");
param.setSavedInstanceState(null);
Bundle b = new Bundle(); b.putInt("count", 42); param.setSavedInstanceState(b);

// ❌ 抛 IllegalArgumentException
param.setSavedInstanceState(new MyModuleState());      // 旧 classloader 下创建的对象
```

同理，`onHotReloading` 收到的 `getExtras()` Bundle 也"should contain only values that can be unmarshalled without the module's class loader"。

> ⚠️ 跨代状态**不要**用 `static` 字段：每个模块代有自己的 ClassLoader，`static` 是新代里的新字段，不是共享的。

### 4.5 `HotReloadResult.Status` 五种状态

→ 见 §1.3.3 的表格。

**诊断规则**：
- `FAILED` + `message == null` ⇒ 旧模块 `onHotReloading` 返回 `false`（**很可能你根本没重写它**）。
- `FAILED` + `message != null` ⇒ 重载过程抛异常，message 是框架诊断串。
- `UNSUPPORTED` ⇒ 检查 `java_init.list` 是否**恰好一行**（最常见问题）。

### 4.6 唯一 Java 入口类约束

Javadoc 明文（【一手·源码】）："Hot reload is supported only for modules that declare **exactly one Java entry class**. Modules with **zero or multiple** Java entry classes are **not hot-reloadable**."

- 违反 ⇒ `HotReloadResult.Status.UNSUPPORTED`。
- 官方 example 虽然同时提供了 `ModuleMain.java` 与 `ModuleMainKt.kt` 两个 `XposedModule` 子类，**但 `java_init.list` 里只写了一行**（Kotlin 那个），正是为了满足这条约束。

### 4.7 `autoHotReload=false` 的建议场景

| 场景 | 建议 | 理由 |
|---|---|---|
| **模块用了 native hook / native 线程** | `autoHotReload=false`（或不实现 `onHotReloading`，默认返回 `false`） | 框架**不会**调 `JNI_OnUnload` / `dlclose`。旧 native 状态必须自己退役，漏一项就是崩溃且难定位（→ §5.5） |
| 模块持有难以清理的全局状态（长生命周期线程池、注册到系统的回调） | 同上 | 清理义务（§4.2）难以完备实现 |
| 模块在 system_server 中工作 | 慎用 | 重载 system_server 中的模块代影响面大 |
| 纯 Java、无线程、hook 都带 id 且实现了 `onHotReloaded` | 可 `true` | 风险最低 |
| 只是想传播配置变更 | ❌ **不要用热重载** | Javadoc 明确反对；用 `getRemotePreferences` + 监听器 |

> `autoHotReload` 只是让"App 更新"**触发**热重载；最终是否重载仍以 `onHotReloading()` 返回 `true` 为准。

---



### 4.5 XposedService 热重载 API（API 102，【一手·PR libxposed/api#62】）

> 以下 API 在 `io.github.libxposed:service:102.0.0` 中新增，仅在模块 App 侧（非宿主进程）可用。

#### 4.5.1 运行目标发现

```java
// XposedService 新增方法
List<HookedTarget> getRunningTargets();
```

- 返回当前模块已注入的所有正在运行的进程列表。
- `HookedTarget` 封装了 AIDL `HookedProcess`，隐藏了不透明的 target id，模块 App 无需关心底层标识。
- **典型用途**：在模块 App 的 UI 中展示"当前注入到哪些进程"，或判断是否需要触发热重载。

#### 4.5.2 热重载请求

```java
// XposedService 新增方法
void hotReloadModule(HookedTarget target, Bundle extras, HotReloadCallback callback);
```

- 向指定进程请求热重载当前模块。
- `extras` 会传递到新代入口类的 `onHotReloaded(HotReloadedParam)` 的 `param.getExtras()` 中。
- `HotReloadCallback` 提供异步结果回调：

```java
public interface HotReloadCallback {
    void onResult(HotReloadResult result);
}

public final class HotReloadResult {
    public enum Status {
        SUCCESS,           // 热重载成功
        UNSUPPORTED,       // 框架不支持或拒绝（如多入口类、进程正在退出）
        MODULE_NOT_LOADED, // 目标进程未加载该模块
        INTERNAL_ERROR     // 框架内部错误
    }
    
    public Status getStatus();
    public String getMessage(); // 详细错误信息（Status != SUCCESS 时）
}
```

- **关键约束**（【一手·PR libxposed/api#62】）：
  - `hotReloadModule` **不是**用于传播配置变更的。配置更新应使用 `RemotePreferences` 和监听器。
  - 此 API 仅用于模块 App 更新后（如 APK 升级）主动通知框架加载新代模块代码。
  - 框架可能因多种原因拒绝请求（如目标进程正在退出、内存不足），模块应处理 `UNSUPPORTED` 状态。

#### 4.5.3 完整使用示例

```java
// 模块 App 侧：在用户点击"应用更新"按钮后触发
public void applyModuleUpdate() {
    XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
        @Override
        public void onServiceBind(@NonNull XposedService service) {
            // 1. 获取当前运行目标
            List<HookedTarget> targets = service.getRunningTargets();
            
            // 2. 逐个触发热重载
            for (HookedTarget target : targets) {
                Bundle extras = new Bundle();
                extras.putString("update_reason", "user_triggered");
                
                service.hotReloadModule(target, extras, new XposedService.HotReloadCallback() {
                    @Override
                    public void onResult(XposedService.HotReloadResult result) {
                        switch (result.getStatus()) {
                            case SUCCESS:
                                Log.i(TAG, "Hot reload succeeded for " + target.getProcessName());
                                break;
                            case UNSUPPORTED:
                                Log.w(TAG, "Hot reload unsupported: " + result.getMessage());
                                // 降级：提示用户手动重启目标应用
                                break;
                            default:
                                Log.e(TAG, "Hot reload failed: " + result.getStatus());
                                break;
                        }
                    }
                });
            }
        }
    });
}
```

### 4.6 detach() API（API 102，【一手·PR libxposed/api#62】）

> 允许当前入口类停止接收后续生命周期回调，同时保留已安装的 hook。

```java
// XposedInterfaceWrapper 新增方法
void detach();
```

- **语义**：调用后，框架不再向该入口类实例发送 `onPackageReady`、`onHotReloading` 等后续回调。
- **不影响的**：
  - 已安装的 hook 仍然生效（hook 不依赖于入口类实例）。
  - `XposedInterface` 的其他 API（如 `hook`、`log`）仍然可用。
  - 框架对该入口实例的引用被释放（有助于垃圾回收）。
- **典型用途**：
  - 模块检测到自身不应在当前进程中继续运行（如包名不匹配、环境不兼容），主动 detach 以避免后续回调的无效开销。
  - 配合热重载：旧代入口类在 `onHotReloading` 中调用 `detach()`，确保旧代码不再接收回调。

```java
@Override
public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    // 探测环境，若不支持则 detach
    if (getApiVersion() < 102) {
        log(Log.ERROR, TAG, "API too old, detaching");
        detach(); // 停止接收后续回调
        return;
    }
    // 正常初始化...
}
```


## 5. Native Hook

### 5.1 ⭐ 结论：**libxposed API 102 没有 native hook 接口**

三条硬证据（【一手·源码】）：

1. `io.github.libxposed.api` 包下只有 `XposedInterface` / `XposedInterfaceWrapper` / `XposedModule` / `XposedModuleInterface` / `package-info` / `error/`，**无任何 native 相关类型**。
2. `XposedInterface.java` 全文中：**无**任何 `native` 方法；**无** `loadLibrary` / `registerNativeLib` / `hookNative` / `NativeAPIEntries` 之类的方法或常量；常量只有 `API_101` / `API_102` / `LIB_API` / 三个 `PROP_*` / 三个 `PRIORITY_*`。
3. `libxposed` 组织下 5 个仓库全部是 Java / Kotlin，**无任何 C/C++/CMake/Rust 仓库**；native 侧也**没有** `helper` 的替代品。

→ **可判定规则：native hook 不在 libxposed API 契约内。任何"用 libxposed API 做 native hook"的说法都是错的。**

API 102 **唯一提到** native hook 的地方，是把责任推给模块（`XposedModuleInterface.onHotReloading` / `onHotReloaded` 的 Javadoc，→ §4.2）。

### 5.2 官方路径（框架私有 ABI，非 API 契约）

```
                    ┌─────────────────────────────────────────────┐
                    │  你的模块 APK                                 │
                    ├─────────────────────────────────────────────┤
  Java 侧            │  XposedModule 子类                           │
                    │  └─ onPackageReady(...)                      │
                    │       └─ System.loadLibrary("yourmod")  ← 必须手动调用
                    ├─────────────────────────────────────────────┤
  声明               │  META-INF/xposed/native_init.list            │
                    │    libyourmod.so                             │
                    ├─────────────────────────────────────────────┤
  Native 侧          │  extern "C" [[gnu::visibility("default")]]   │
                    │           [[gnu::used]]                      │
                    │  NativeOnModuleLoaded native_init(            │
                    │      const NativeAPIEntries *entries) {        │
                    │      hookFunc   = entries->hookFunc;          │
                    │      unhookFunc = entries->unhookFunc;        │
                    │      // 系统库 hook 在这里做                    │
                    │      return on_library_loaded;                │
                    │  }                                            │
                    └─────────────────────────────────────────────┘
                                      │
                   框架 hook 了 do_dlopen，检测到 libyourmod.so 后
                   自动调用 native_init 并注入 Dobby 的 hook/unhook 指针
```

**三步（缺一步都不生效）**：

```text
① 声明：src/main/resources/META-INF/xposed/native_init.list，内容一行一个库名
     libyourmod.so

② 加载：在 onPackageReady 里手动 System.loadLibrary("yourmod")（必须 try/catch）
     —— 框架只在你自己加载了 so 之后才会通过 do_dlopen 拦截并调用 native_init

③ 导出：extern "C" [[gnu::visibility("default")]] [[gnu::used]]
        NativeOnModuleLoaded native_init(const NativeAPIEntries *entries);
```

**完整示例（可直接编译）**：

```cpp
// app/src/main/cpp/yourmod.cc
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <jni.h>
#include <string>

// 头文件内容（Vector: native/include/core/native_api.h）
typedef int (*HookFunType)(void *func, void *replace, void **backup);
typedef int (*UnhookFunType)(void *func);
typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

struct NativeAPIEntries {
    uint32_t version;            // The version of this API struct.
    HookFunType hookFunc;        // ⚠️ camelCase，不是 hook_func
    UnhookFunType unhookFunc;    // ⚠️ camelCase，不是 unhook_func
};

typedef NativeOnModuleLoaded (*NativeInit)(const NativeAPIEntries *entries);

static HookFunType hookFunc = nullptr;
static UnhookFunType unhookFunc = nullptr;

int (*backup_target)(int);
int fake_target(int x) { return backup_target(x) + 1; }

FILE *(*backup_fopen)(const char *filename, const char *mode);
FILE *fake_fopen(const char *filename, const char *mode) {
    if (strstr(filename, "banned")) return nullptr;
    return backup_fopen(filename, mode);
}

// 框架在每次有新的 native 库被 dlopen 时调用它
static void on_library_loaded(const char *name, void *handle) {
    if (std::string(name).ends_with("libtarget.so")) {
        void *target = dlsym(handle, "target_fun");
        if (target && hookFunc) hookFunc(target, (void *) fake_target, (void **) &backup_target);
    }
}

// ⚠️ 名字、三个 attribute、extern "C" 都必须保持原样
extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    hookFunc = entries->hookFunc;        // ⚠️ 不是 entries->hook_func
    unhookFunc = entries->unhookFunc;
    // 系统库 hook 可以在这里做
    hookFunc((void *) fopen, (void *) fake_fopen, (void **) &backup_fopen);
    return on_library_loaded;            // 返回「库加载」回调
}
```

```kotlin
// app/src/main/java/com/example/ModuleMain.kt
override fun onPackageReady(param: PackageReadyParam) {
    if (!param.isFirstPackage) return
    if (param.packageName != TARGET) return
    try {
        System.loadLibrary("yourmod")     // ⚠️ 必须手动加载，且必须 try/catch
    } catch (t: Throwable) {
        log(Log.ERROR, TAG, "load native failed", t)
    }
}
```

【一手·源码】（Vector `native/include/core/native_api.h` + `native/README.md` + LSPosed Wiki）

三条可选路径对比：

| 路径 | 做法 | 优点 | 缺点 / 风险 |
|---|---|---|---|
| **P1（首选）** 用框架注入的 `NativeAPIEntries` | 声明 `native_init.list`，导出 `native_init`，用 `entries->hookFunc` / `unhookFunc` | 框架提供 hook 实现（Vector 下是 **Dobby**），无需自己引第三方库；能收到「库加载」回调做延迟 hook | **非 libxposed API 契约**，是框架私有 ABI；字段名 LSPosed → Vector 变过（`hook_func` → `hookFunc`）；换框架可能失效 |
| **P2** 自行集成 Dobby / AndHook / LSPlant | 静态链接 hook 库，`onPackageReady` 后自行 hook | 完全自主，跨框架一致性好 | 增大体积；需自己处理架构适配（arm64/x86_64、16KB 页对齐）；框架不帮你做 `do_dlopen` 拦截与「库加载回调」 |
| **P3** 只 hook JNI / 系统函数，走 `JNI_OnLoad` | 在 `JNI_OnLoad` 里拿 `JavaVM` → `JNIEnv` → hook `env->functions->FindClass` | 不需要任何 hook 库 | 只能 hook 通过 JNI 函数表访问的调用，覆盖面窄 |

### 5.3 字段名是 `hookFunc`（不是 `hook_func`）

Vector 现行 ABI（`native/include/core/native_api.h`）原文：

```cpp
struct NativeAPIEntries {
    uint32_t version;          // The version of this API struct.
    HookFunType hookFunc;      // camelCase，不再是 hook_func
    UnhookFunType unhookFunc;  // camelCase，不再是 unhook_func
};
```

`HookFunType` / `UnhookFunType` / `NativeOnModuleLoaded` / `NativeInit` 四个 typedef **保持一致**。

> ⚠️ **如果你抄 LSPosed wiki 的 `entries->hook_func` 去对接 Vector，编译期就会失败。** 以 `native_api.h` 为准。【一手·源码】

### 5.4 Wiki《Native-Hook》页面的过时点清单

来源：<https://github.com/LSPosed/LSPosed/wiki/Native-Hook>（Wang Han 编辑于 2026-08-12，3 次修订）。

> ⚠️ **背景**：`LSPosed/LSPosed` 仓库自 **2024-01-07** 起停止提交（最后提交 `df74d83`），README 仍声明支持 Android 8.1~14。活跃继任者是 **`JingMatrix/Vector`**（支持 Android 8.1~17 Beta）。该 wiki 页描述的是 2024 年冻结时的状态。

| # | 位置 | 过时内容 | 现代正确形式 |
|---|---|---|---|
| N-1 | Tell Entries to Framework | `assets/native_init` | **`META-INF/xposed/native_init.list`** |
| N-2 | Simple Example | `assets/native_init` | 同上 |
| N-3 | Simple Example | `assets/xposed_init` | **`META-INF/xposed/java_init.list`** |
| N-4 | Simple Example | `IXposedHookLoadPackage` / `handleLoadPackage` | **`XposedModule` / `onPackageReady`** |
| N-5 | Header File | `hook_func` / `unhook_func` | **`hookFunc` / `unhookFunc`**（Vector `native_api.h`） |
| N-6 | Entries 正文 | `hook_fun`（与它自己 header 片段的 `hook_func` 都自相矛盾） | **`hookFunc`** |
| — | AndroidManifest | `android:multiArch` / `android:extractNativeLibs` | **未获取到**现代官方要求，需实测 |

**该页仍然有效的部分**（不要一并否掉）：

- ✅ 机制描述："Whenever a new native library loaded, LSPosed will invoke a callback of the module..." —— 与 Vector 现行实现一致。
- ✅ 入口名 `native_init`、三个 attribute（`extern "C"` + `[[gnu::visibility("default")]]` + `[[gnu::used]]`）、返回值语义。
- ✅ "必须手动 `System.loadLibrary`" + 必须 try/catch。
- ✅ 「库加载」回调 `NativeOnModuleLoaded(const char *name, void *handle)` 的语义。
- ✅ JNIEnv Hooks 技巧（通用 NDK 技巧，与框架无关）。

### 5.5 私有 ABI 风险 + 与热重载的交互

**风险（可判定规则）**：

1. `native_init` 是**框架给你的 ABI**，不是 libxposed API 契约。它不由 `libxposed/api` 发布，**换框架可能失效**（字段名已经在 LSPosed → Vector 之间变过一次）。
2. 使用前先做**运行时能力判断**：`(getFrameworkProperties() & PROP_CAP_SYSTEM) != 0` 只说明框架能 hook system_server，**不能**推出框架支持 native 模块。
3. `NativeAPIEntries` 的 `version` 字段要先检查，**不要假设字段布局固定**。
4. ⚠️ **不要在 `native_init` 里修改 `entries` 的内容**（官方原文："Do not try to modify its content. It will crash."）。
5. 换框架 / 升级框架后，**必须重新验证 native 部分**。

**与热重载的交互（⚠️ 这是 native hook 在现代 API 下最大的坑）**：

| 事实 | 出处 |
|---|---|
| 框架**不会**调用 `UnregisterNatives` / `JNI_OnUnload` / `dlclose` | `onHotReloaded` Javadoc 原文 |
| 模块**必须**自己在 `onHotReloading` 里：停止 native 线程、注销 native hook 与外部回调、释放指向模块 classloader 对象的 JNI 全局引用 | `onHotReloading` Javadoc 原文 |
| 若所有 Java 引用都清了但旧 native 线程 / 回调 / hook / JNI 全局引用仍在活动，任何崩溃或未定义行为**都是模块 bug** | 同上 |

> **架构建议**：模块同时用了 native hook 和 `autoHotReload=true` 时，必须在 `onHotReloading` 里**完整实现上述四项**，否则重载后旧 code 的 native 回调仍指向已失效的 JNI 引用 → **崩溃且难以定位**。
> **更保守的选择**：用了 native hook 的模块 → `autoHotReload=false`，或不实现 `onHotReloading`（默认返回 `false`，即拒绝重载）。→ §4.7

---

## 未验证 / 未获取到

- **LSPatch / embedded 形态下 `XposedService` 的可用性**：`LSPatch/LSPatch` 仓库访问返回 **404**（可能已删除 / 更名 / 转私有）。
  **架构师推断（【推断】，非文档结论）**：`XposedService` 的存在前提是"有一个独立的框架/管理器进程向模块的 exported provider 推 binder"。embedded 形态缺少这个推送方，因此 `onServiceBind` 极可能**永远不会被调用**。
  **工程应对**：把 `XposedService` 当作**可选增强**而非硬依赖 —— 所有相关 UI 与逻辑必须有 `mService == null` 的降级路径，且**绝不能**在 `onPackageReady` 等 hook 生命周期回调里等待 service。
  已确证的一手事实只有一条：**embedded 框架下 `XposedInterface` 的 remote preferences / remote files 抛 `UnsupportedOperationException`**（`XposedInterface.java` javadoc 原文）。
- **`libxposed/helper` 的官方废弃声明原文**未获取到（`helper` 仓库 README 返回 404）。且 `libxposed/api` 与 `libxposed/service` 的 README「Related Projects」中**仍然**链接 `libxposed/helper` 并描述为 "Friendly development kit library" —— **README 未同步废弃状态**，属文档滞后。
- **`META-INF/xposed/native_init.list` 的框架侧读取代码**未定位到具体源码行。路径本身有官方 Wiki 直接背书，但**未在框架源码中复验**。
- **现代 API 下 `android:multiArch` / `android:extractNativeLibs` 的官方要求**未获取到 → 标注为**需实测**。
- **Vector v2.2 Release Note 正文中 "IPC 接口移入 Vector 自有命名空间" 的原句**未检索到；实质内容在 PR #757（commit `abae837`）与 PR #861（commit `de6e250`）的 **commit message** 中。**对模块代码影响为零**：改的是 Vector 内部 manager↔daemon 的 AIDL（`org.lsposed.lspd.*` → `org.matrix.vector.ipc`），模块面对的 `io.github.libxposed.service` 命名空间未动。
- **`IXposedService.aidl` 方法编号与 `HookedProcess.aidl`** 已取证，但 AIDL 是框架 ↔ service 的内部线协议，模块不需要接触。
- **LSPosed v2.x 官方 release notes** 在 GitHub 上不存在（最新 tag `v1.9.2`，2023-10-11）。本册不引用任何 v2.x changelog 作为硬约束。
- **`RemotePreferences` 的 `getStringSet` 返回内部 Set 后 mutate 的实际持久化行为**未实测，结论由源码推导。
