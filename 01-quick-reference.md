# 01 · libxposed API 102 速查卡

> **本册定位**：高频速查表。Agent 写代码时手边放着的那一页，加载成本最低，几乎无段落。
> **何时读这一册**：任何正在写 / 改 libxposed API 102 模块代码的时刻；只需查签名、常量、文件名时。
> **前置依赖**：无（可独立加载）。深挖任何一行 → 见 `03-api-reference.md §N`；进阶主题 → 见 `08-service-and-advanced.md`。
> **交叉引用**：→ 见 `03-api-reference.md`、`08-service-and-advanced.md`、`02-project-scaffold.md`、`05-pitfalls.md`

**基准**：`io.github.libxposed:api:102.0.0` / `io.github.libxposed:service:102.0.0`。本册只写 API 102；一切环境判断走运行时探测，禁止比较框架版本字符串。

**可信度声明（适用于本册全部表格行）**：除显式标注外，本册每一行均为 `【一手·源码】`（`libxposed/api`、`libxposed/service`、`libxposed/example`、`libxposed/lint` 源码与 Javadoc）或 `【一手·文档】`（LSPosed 官方 Wiki）。逐行的来源举证与 Javadoc 原文 → 见 `03-api-reference.md`、`08-service-and-advanced.md`。标注为 `【推断】` 的条目：本册 §4 的"同优先级 hook 顺序未定义"注、本册 §5 的"回调异常被静默吞"注。本册基准为 libxposed 源码与官方 Wiki；涉及框架行为的版本事实引用官方 changelog（v2.0.2 ~ v2.2.0）。**公开 GitHub tag（v1.9.2）不代表框架当前能力**，禁止据此判断。

---

## 1. 依赖坐标与版本

| 工件 | 坐标 | Gradle 写法 | 为什么 | → |
|---|---|---|---|---|
| 模块 API | `io.github.libxposed:api:102.0.0` | `compileOnly(...)` | 框架运行时注入实现。写成 `implementation` → 日志 `The Xposed API classes are compiled into the module's APK.` + 模块静默失效 | `03-api-reference.md §1.1` |
| 模块服务 | `io.github.libxposed:service:102.0.0` | `implementation(...)` | 模块 App 自己要用的 Binder 客户端，必须打进 APK | `08-service-and-advanced.md §1.1` |
| 注解 | 无（**不要引**） | — | 位于 `libxposed/lint` 仓库，`api` 以 `compileOnly` 引用，APK 里不存在 → 只能 `-dontwarn` | `03-api-reference.md §14` |
| helper | `io.github.libxposed:helper:100.0.1` | ❌ **禁止** | 停在 API 100、2023 停更，与 102 的 Chain 模型二进制不兼容 | `03-api-reference.md §15` |
| 编译环境 | `minSdk ≥ 26`、`Java 17+`（`Invoker.Type` 是 `sealed interface`） | `sourceCompatibility = VERSION_17` | 低于 17 编译失败；`minSdk < 26` 时 service 库 manifest merger 直接失败 | `02-project-scaffold.md` |

```kotlin
// app/build.gradle.kts
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")          // 必须 compileOnly
    implementation("io.github.libxposed:service:102.0.0")   // 必须 implementation
}
// 另需 packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }（两条成对）
```

---

## 2. `XposedInterface` 方法速查（16 个顶层方法）

| 方法签名 | 一句话作用 | → |
|---|---|---|
| `default int getApiVersion()` | **运行时** Xposed API 版本。能力探测的唯一正解，框架不得重写 | §3.2 |
| `@NonNull String getFrameworkName()` | 框架名（`LSPosed` / `Vector` / `LSPatch`） | §3.2 |
| `@NonNull String getFrameworkVersion()` | 框架版本字符串（**只用于展示，禁止解析判断**） | §3.2 |
| `long getFrameworkVersionCode()` | 框架版本号（数值） | §3.2 |
| `long getFrameworkProperties()` | 能力位掩码。`PROP_RT_*` 每次启动可能变，**每会话重读，禁止缓存** | §2.3 |
| `@NonNull ApplicationInfo getModuleApplicationInfo()` | **模块自己**的 ApplicationInfo（`.sourceDir` = 模块 APK 路径，不是宿主的） | §3.2 |
| `@NonNull HookBuilder hook(@NonNull Executable origin)` | 起一条 hook 链。**返回 `HookBuilder` 不是 `HookHandle`**，必须再 `.intercept(hooker)` | §3.3 |
| `@NonNull HookBuilder hookClassInitializer(@NonNull Class<?> origin)` | hook `<clinit>`，当 `static void()` 处理。**类已初始化则永不回调** | §3.3 |
| `@NonNull Invoker<?, Method> getInvoker(@NonNull Method m)` | 取方法调用器。**默认 type = `Chain.FULL`（会重进自己的 hook）= 无限递归首因** | §5 |
| `@NonNull <T> CtorInvoker<T> getInvoker(@NonNull Constructor<T> c)` | 取构造器调用器，默认 type 同样是 `Chain.FULL` | §5 |
| `boolean deoptimize(@NonNull Executable executable)` | 去优化的是**调用方**不是被 hook 的目标方法。解决内联 | §6 |
| `void log(int priority, @Nullable String tag, @NonNull String msg)` | 写框架日志。`priority` 用 `android.util.Log.*` 常量 | §7 |
| `void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr)` | 同上，带堆栈 | §7 |
| `@NonNull SharedPreferences getRemotePreferences(@NonNull String group)` | 读框架侧配置。**在宿主进程内只读**；embedded 框架抛 `UnsupportedOperationException` | §8 |
| `@NonNull String[] listRemoteFiles()` | 列出模块共享数据目录下的文件 | §8 |
| `@NonNull ParcelFileDescriptor openRemoteFile(@NonNull String name)` | **只读**打开；不存在抛 `FileNotFoundException`；name 不得含 `/`、`.`、`..` | §8 |

---

## 3. `Chain` 方法速查（8 个）

| 方法 | 作用 | 硬约束 / 异常 | → |
|---|---|---|---|
| `@NonNull Executable getExecutable()` | 当前被 hook 的方法/构造器 | `proceedWith` **不会**改变它 | §4.5 |
| `Object getThisObject()` | 接收者；静态方法为 `null` | `<clinit>` hook 恒为 `null` | §4.5 |
| `@NonNull List<Object> getArgs()` | 参数列表 | ⚠️ **返回不可变 List**，`getArgs().set(...)` → `UnsupportedOperationException` | §4.5 |
| `Object getArg(int index)` | 取单个参数 | `IndexOutOfBoundsException` / `ClassCastException` | §4.5 |
| `Object proceed() throws Throwable` | 交给链下一环 / 原方法 | 不调 = 跳过原方法。`proceed()` 抛的异常**永远向上传播**（PROTECTIVE 也不吞） | §4.5 |
| `Object proceed(@NonNull Object[] args)` | 用新参数继续 | **这是改参数的唯一正解** | §4.5 |
| `Object proceedWith(@NonNull Object thisObject)` | 换接收者继续 | ⚠️ **不做虚派发**；`thisObject` 必须是声明类的实例，否则 `IllegalArgumentException`；**静态方法的拦截器禁止调用** | §4.6 |
| `Object proceedWith(@NonNull Object thisObject, @NonNull Object[] args)` | 同时换接收者与参数 | 同上 | §4.6 |

> ⚠️ **全局硬约束**：`Chain` 对象**不可跨线程共享**，`Hooker.intercept(Chain)` 返回后**不可复用**。要异步就先把 `getArgs()` / `getThisObject()` 拷出来，绝不带 `chain` 进 `Runnable`。 → §4.5

---

## 4. 常量表

| 常量 | 类型 | 值 | 语义 / 注意 | → |
|---|---|---|---|---|
| `API_101` | int | `101` | API 版本 101 | §2.1 |
| `API_102` | int | `102` | API 版本 102。守卫写法：`if (getApiVersion() >= API_102)` | §2.1 |
| `LIB_API` | int | `102` | **编译期**库版本。运行时一律用 `getApiVersion()` | §2.1 |
| `PRIORITY_DEFAULT` | int | `50` | 默认优先级 | §2.2 |
| `PRIORITY_LOWEST` | int | `Integer.MIN_VALUE` | 链尾执行 | §2.2 |
| `PRIORITY_HIGHEST` | int | `Integer.MAX_VALUE` | 链首执行 | §2.2 |
| `PROP_CAP_SYSTEM` | long | `1L` | 框架可 hook system_server 等系统进程 | §2.3 |
| `PROP_CAP_REMOTE` | long | `1L << 1` | 框架支持 remote preferences / remote files。**未置位时远程三方法抛 `UnsupportedOperationException`** | §2.3 |
| `PROP_RT_API_PROTECTION` | long | `1L << 2` | 框架禁止通过反射 / 动态加载代码访问 Xposed API → 模块必须静态引用 API | §2.3 |

`ExceptionMode`（三值，→ §2.4；使用示例 → §4.7）：

| 枚举 | 行为 | 何时用 |
|---|---|---|
| `DEFAULT` | 跟随 `module.prop` 的 `exceptionMode`，未配置时等同 `PROTECTIVE` | 不显式设置时的默认，**推荐保持** |
| `PROTECTIVE` | hooker 自己抛的异常被捕获记日志，调用"当作无此 hook"继续；**`proceed()` 抛的异常仍传播** | 生产默认 |
| `PASSTHROUGH` | hooker 的异常照常抛给调用方 | **仅调试期** |

> ⚠️ API 100 的 `PRIORITY_LOWEST/HIGHEST` 是 `±10000`、`PRIORITY_DEFAULT` 之外另有 `getFrameworkPrivilege()`。**不要硬编码数字**，也不要照抄 ±10000。 → §15

---

## 5. 生命周期回调速查（6 个）

| 回调 | 触发时机 | 能拿到什么 | 该干什么 / 不该干什么 | → |
|---|---|---|---|---|
| `onModuleLoaded(ModuleLoadedParam)` | 模块代被载入进程 | `isSystemServer()`、`getProcessName()` | ✅ 打诊断日志、能力探查。❌ **没有任何 ClassLoader，禁止 hook**；❌ 热重载时不重放 | §12 |
| `onPackageLoaded(PackageLoadedParam)`<br>**`@RequiresApi(Q)`** | 默认 classloader 就绪、`AppComponentFactory` 实例化**之前** | `getPackageName()`、`getApplicationInfo()`、`isFirstPackage()`、`getDefaultClassLoader()` | ⚠️ Android < 29 **不会被回调**。❌ 没有 `getClassLoader()` | §12 |
| `onPackageReady(PackageReadyParam)` | `AppComponentFactory` 造好真实 classloader、准备创建 `Application` | 上面全部 + `getClassLoader()`、`getAppComponentFactory()` | ✅ **绝大多数 hook 的安装点**。第一行先过滤 `getPackageName()` + `isFirstPackage()` | §12 |
| `onSystemServerStarting(SystemServerStartingParam)` | system_server 准备启动关键服务 | `getClassLoader()` | ✅ system_server 的唯一 hook 点。❌ 此处 `isFirstPackage()` **永不为 true** | §12 |
| `boolean onHotReloading(HotReloadingParam)`<br>**`@SinceApi(102)`** | 热重载即将发生，**运行在旧代码中** | `getExtras()`；`setSavedInstanceState(Object)` | 返回 `true` = 放行（**默认 `false` = 拒绝**）。返回 `true` 前**必须**停掉模块自有线程、注销 native hook、释放 JNI 全局引用 | `08-service-and-advanced.md §4.2` |
| `void onHotReloaded(HotReloadedParam)`<br>**`@SinceApi(102)`** | 重载完成，**运行在新代码中** | `getExtras()`、`getSavedInstanceState()`、`getOldHookHandles()` | ⚠️ **默认实现只是把所有旧 hook 全部 unhook**，且包生命周期回调**不重放** → 必须显式 `replaceHook` / 重装，否则功能静默消失 | `08-service-and-advanced.md §4.3` |

> 所有回调抛出的异常都被框架捕获并记日志（不会崩宿主，但你的逻辑静默失效）。 → §12

---

## 6. `module.prop` 字段表

文件：`src/main/resources/META-INF/xposed/module.prop`，**Java Properties 格式**。

| 字段 | 类型 | 必须 | 默认值 | 大小写敏感性 | 语义 | → |
|---|---|---|---|---|---|---|
| `minApiVersion` | int | **是** | `100`（prop 文件不存在时） | 键名**严格区分大小写**；写 `minapiversion` = 缺失 | 运行所需最低 Xposed API 版本。框架 API < 此值则模块不加载 | §11.4 |
| `targetApiVersion` | int | **是** | `100` | 同上 | 针对哪个 API 构建，决定启用哪些行为变更。**≥102 ⇒ 禁止调用 legacy `de.robv.android.xposed`** | §11.4 |
| `staticScope` | boolean | 否 | `false` | 键名区分大小写；值是**全等字符串比较** `"true"`，写 `True`/`TRUE`/`1`/`yes` **一律不生效** | 禁止用户把模块用到 scope list 之外的 App | §11.4 |
| `autoHotReload` | boolean | 否 | 无（等同不启用） | 同 `staticScope` | App 更新时自动热重载。**仍以 `onHotReloading()` 返回 `true` 为前提** | §11.4 |

**第 5 个合法字段**（官方示例未写，`package-info.java` 记载）：`exceptionMode=protective|passthrough`，默认 `protective`。它是 `ExceptionMode.DEFAULT` 的全局来源。

```properties
# 推荐配置（需 102-only API 时）
minApiVersion=102
targetApiVersion=102
staticScope=true
# 需要热重载再加：autoHotReload=true（且 java_init.list 只能有一行）
```

> ⚠️ 键缺失不会回退到默认值 `100`，而是走 `null` 处理路径。**三个键全部显式写出。**
> ⚠️ **不要在 `AndroidManifest.xml` 里写 `xposedminversion`** —— 管理器会据此把你判为 Legacy 模块。

---

## 7. `META-INF/xposed/` 下的文件（解析规则各不相同）

| 文件 | 内容 | 解析规则 | 缺失 / 空文件的后果 | → |
|---|---|---|---|---|
| `java_init.list` | Java 入口类**全限定名**，一行一个 | 逐行 `trim()`；**空行跳过**；**`#` 开头视为注释跳过**；支持多行多入口 | **空/全注释 ⇒ 框架把模块判为 Legacy**，回退读 `assets/xposed_init`（也不存在则加载失败，无任何报错） | §11.1 |
| `module.prop` | Java Properties 键值对 | `new Properties().load(...)`；键区分大小写 | 不存在 ⇒ `min=100 / target=100 / staticScope=false` | §11.4 |
| `scope.list` | 作用域包名，一行一个 | ⚠️ `reader.lines().collect(toList())` —— **原样收集，不 trim、不跳空行、不过滤注释** | 缺失 ⇒ 空作用域，**不报错** | §11.1 |
| `native_init.list` | native 库名（如 `libexample.so`） | 同 `java_init.list` 的 `readName()` | 不写则框架不会给你的 so 注入 `native_init` | `08-service-and-advanced.md §5` |

> ⚠️ **`java_init.list` 与 `assets/xposed_init` 是二选一开关**：`java_init.list` 非空时框架**根本不读** `assets/xposed_init`。想同时兼容新旧框架，不能靠放两个文件。
> ⚠️ `scope.list` 里**不要留空行、不要写注释、行尾不要带空格**。
> ⚠️ 热重载要求 `java_init.list` **恰好一行**（0 行或多行 ⇒ `HotReloadResult.Status.UNSUPPORTED`）。

---

## 8. ProGuard / R8 三件套（缺一不可）

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

| 规则 | 作用 | 缺了它的后果 | → |
|---|---|---|---|
| `-dontwarn io.github.libxposed.annotation.**` | 注解类是 `compileOnly`，APK 里不存在，R8 会警告找不到引用类 | release 构建报 warning；`abortOnError=true` 时**直接构建失败** | §11.2 |
| `-adaptresourcefilecontents META-INF/xposed/java_init.list` | 入口类被混淆后**同步重写清单里的类名** | ⚠️ **经典症状：debug 一切正常，release 完全失效**（debug 不混淆，名字还对得上） | §11.2 |
| `-keep,allowoptimization,allowobfuscation public class * extends XposedModule { public <init>(); }` | 入口类不被移除、保留**公开无参构造器**（框架靠反射 `newInstance()`），但允许重命名（由上一行兜底） | 入口被 shrink ⇒ 静默失效；构造器被内联/降权 ⇒ `NoSuchMethodException` / `IllegalAccessException` | §11.2 |

**自检命令**：`unzip -l app-release.apk | grep xposed` —— `java_init.list` 必须存在，且解压出来的内容是**混淆后的类名**（证明第二条规则生效）。

---

## 9. `XposedService` 方法速查（模块 App 侧，15 个）

> 前置：`XposedServiceHelper.registerListener(this)` 应在 `Application.onCreate()` 调用，且只调**一次**。所有方法都可能抛 `XposedService.ServiceException`（`RuntimeException` 子类），**每次调用都要 catch**。本表 → 列均指 `08-service-and-advanced.md` 的章节。 → `08-service-and-advanced.md §1`

| 方法 | 作用 | 关键约束 | → |
|---|---|---|---|
| `int getApiVersion()` | service IPC 版本 | ⚠️ 与 `XposedInterface.API_102` 是**不同类的不同常量**，别混用 | `08 §1.3.1` |
| `@NonNull String getFrameworkName()` | 框架名 | | `08 §1.3.1` |
| `@NonNull String getFrameworkVersion()` | 框架版本字符串 | | `08 §1.3.1` |
| `long getFrameworkVersionCode()` | 框架版本号 | | `08 §1.3.1` |
| `long getFrameworkProperties()` | 能力位掩码 | 用 `XposedService.PROP_CAP_*`（注意不是 `XposedInterface` 的） | `08 §1.3.1` |
| `@NonNull List<String> getScope()` | 当前作用域包名列表 | **同步 Binder 调用，禁止主线程**；无作用域变更推送，只能轮询 | `08 §1.3.2` |
| `void requestScope(List<String> packages, OnScopeEventListener cb)` | 动态申请作用域 | ⚠️ **AIDL `oneway` + 一次性回调**：listener 触发一次即从 map 移除，第二次请求必须传**新实例**。❌ 无 `optional`/`required` 语义 | `08 §1.3.2` |
| `void removeScope(List<String> packages)` | 移除作用域 | **同步、无回调** | `08 §1.3.2` |
| `@NonNull List<HookedTarget> getRunningTargets()` **@SinceApi(102)** | 当前被本模块 hook 的进程快照 | service API < 102 抛 `UnsupportedOperationException`；`getPid()` **仅诊断用，不得当身份** | `08 §1.3.3` |
| `void hotReloadModule(HookedTarget target, @Nullable Bundle data, HotReloadCallback cb)` **@SinceApi(102)** | 请求热重载 | `data` 只能放 classloader 中立值；**不得用于传播配置变更**；回调在 **Binder 线程**；`SecurityException` = 目标非法 | `08 §1.3.3` |
| `@NonNull SharedPreferences getRemotePreferences(String group)` | 取（**不存在则创建**）远程配置 | ⚠️ **模块 App 进程内可读可写**；无 `PROP_CAP_REMOTE` 抛 `UnsupportedOperationException` | `08 §1.3.4` |
| `void deleteRemotePreferences(String group)` | 删除整组配置 | 同步清空本地缓存 | `08 §1.3.4` |
| `@NonNull String[] listRemoteFiles()` | 列出共享数据目录文件 | 同步 Binder | `08 §1.3.4` |
| `@NonNull ParcelFileDescriptor openRemoteFile(String name)` | 打开共享文件 | ⚠️ **不存在则创建、可写**（与宿主侧同名方法相反）；name 不得含 `/`、`.`、`..` | `08 §1.3.4` |
| `boolean deleteRemoteFile(String name)` | 删除文件；不存在返回 `false` | | `08 §1.3.4` |

**`XposedService` vs `XposedInterface` 同名方法差异（最易错）**：

| 能力 | `XposedService`（模块 App 进程） | `XposedInterface`（被 hook 的宿主进程） |
|---|---|---|
| `getRemotePreferences(group)` | **可读可写** | **只读** |
| `openRemoteFile(name)` | 不存在则**创建**，可写 | 只读，不存在抛 `FileNotFoundException` |
| `deleteRemotePreferences` / `deleteRemoteFile` | ✅ 有 | ❌ 无 |

---

## 10. "我想做 X，该用哪个 API" 反向索引

| 需求 | 该用的 API | → |
|---|---|---|
| 读方法参数 | `chain.getArgs()`（**不可变**）/ `chain.getArg(i)` | §4.5 |
| **改方法参数** | `chain.proceed(newArgs)` 或 `chain.proceedWith(this, newArgs)` | §4.5 |
| **改返回值** | `Object r = chain.proceed(); return transform(r);` | §4.5 |
| **跳过原方法 / 完全替换**（= legacy `setResult`+`returnEarly` / `XC_MethodReplacement`） | **不调 `proceed()`，直接 `return 值;`** | §4.5 |
| 抛异常给调用方 | `throw new XxxException();`（配 `ExceptionMode.PASSTHROUGH`） | §4.7 |
| 捕获原方法异常（= legacy `getThrowable`） | `try { chain.proceed(); } catch (Throwable t) { ... }` | §4.5 |
| **调原方法**（跳过所有 hook） | `getInvoker(m).setType(Invoker.Type.ORIGIN).invoke(thisObj, args)` | §5 |
| **调 super / 非虚调用** | `getInvoker(m).invokeSpecial(thisObj, args)` | §5.3 |
| **hook 构造函数** | `hook(clazz.getDeclaredConstructor(types)).intercept(...)`；void/构造器要 `return null` | §3.3 |
| 拦截对象创建 | hook **具体构造器**。❌ 不能 hook `Constructor.newInstance`（抛 `IllegalArgumentException`） | §3.3 |
| **hook 静态初始化块 `<clinit>`** | `hookClassInitializer(clazz)`。**类已初始化则永不回调** | §3.3 |
| 创建实例（走 hook 链） | `getInvoker(ctor).newInstance(args)` | §5.4 |
| 创建子类实例但用父类构造器初始化 | `getInvoker(parentCtor).newInstanceSpecial(Sub.class, args)` —— 传**子类 Class** + **父类构造参数**，会留下未初始化状态 | §5.4 |
| **去内联**（hook 不回调） | `deoptimize(callerMethod)` —— 去优化**调用方** | §6 |
| **打印日志** | `log(Log.INFO, TAG, msg)` / `log(Log.ERROR, TAG, msg, tr)`（priority 用 `android.util.Log.*`） | §7 |
| **读模块配置**（宿主侧） | `getRemotePreferences(group).getString(key, def)` —— **只读**；先判 `PROP_CAP_REMOTE` | §8 |
| 写模块配置 | **只能在模块 App 侧** `XposedService#getRemotePreferences(group).edit()` | `08-service-and-advanced.md §1.3.4` |
| 读大块数据（dex / blob / 映射表） | 宿主侧 `openRemoteFile(name)` 只读；App 侧 `XposedService#openRemoteFile(name)` 创建并写 | §8 |
| 感知配置变更（宿主侧） | `prefs.registerOnSharedPreferenceChangeListener(...)` | §8 |
| 拿宿主的真实 ClassLoader | `onPackageReady` 的 `param.getClassLoader()`（+ 三参 `Class.forName(name, true, cl)`） | §11.3 |
| 拿 system_server 的 ClassLoader | `onSystemServerStarting` 的 `param.getClassLoader()` | §11.3 |
| 拿模块自己的 APK 路径 | `getModuleApplicationInfo().sourceDir` | §3.2 |
| 判断框架能力 | `(getFrameworkProperties() & PROP_CAP_XXX) != 0` | §2.3 |
| 判断 API 版本 | `getApiVersion() >= XposedInterface.API_102` —— **禁止比较框架版本字符串** | §2.1 |
| 取消 hook | `HookHandle#unhook()`（**幂等**） | §4.4 |
| 热重载后换新代码 | `oldHookHandle.replaceHook(newHooker)`（原子，保留 priority/exceptionMode/id） | `08-service-and-advanced.md §4.3` |
| 不再接收生命周期回调 | `detach()`（@SinceApi(102)，幂等，**不影响已装的 hook 与其它 API**） | §10.4 |
| 改 `this`（receiver） | `chain.proceedWith(newThis)`。⚠️ **不做虚派发**，要虚派发请用 `Invoker` | §4.6 |
| 改资源（drawable / layout / xml） | ❌ **无 API**。资源 Hook 在 102 已移除，只能 hook `Resources#getXxx` / `LayoutInflater` / `Context#getResources()` 自行改写 | §9 |
| 跨进程通信（自定义） | ❌ libxposed 不提供。用 Remote Preferences / Remote Files，或 Android 原生 Binder / Provider | §8 |
| hook 模块自己的 App | ❌ **禁止**（`module apps are no longer hooked by themselves`）。走 `XposedService` | `08-service-and-advanced.md §1` |

---

## 未验证 / 未获取到

- `module.prop` 的 `autoHotReload` 与 `exceptionMode` 在框架侧的**具体解析代码行**未取到（语义已由 Javadoc「Module Configuration」节与官方指南确证）。
- `scope.list` 是否会导致作用域外回调被过滤：源码只确证它作为初始建议值被读入，与「回调会超出 scope」的 Javadoc 并存，两者的优先级关系未取到一手说明。`getRemotePreferences` 在宿主侧写入时的**具体失败形态**（静默失败 vs 抛异常）也未取到一手证据，社区两说并存 → 一律按「只读」处理。
