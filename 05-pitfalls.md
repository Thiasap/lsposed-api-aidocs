# 05 · 90 条高频坑点清单

> **本册定位**：API 102 模块开发的全部已知高频错误与反模式，按 15 组主题组织。每条：症状 → 根因 → 正确写法。
> **何时读这一册**：写代码前扫一遍对应分组；行为异常时按组检索；生成代码后用它做自查。
> **前置依赖**：无（每条独立成立）。API 语义 → 见 `03-api-reference.md`；排障流程 → 见 `06-debugging-runbook.md`。
> **交叉引用**：→ 见 `03-api-reference.md §4-§6`、`04-lifecycle-and-patterns.md §2`、`06-debugging-runbook.md`、`08-service-and-advanced.md`、`10-agent-rules.md`

---

**约定**：标注省略时均为【一手·源码】（libxposed/api、libxposed/example 源码与 Javadoc + 真实 issue）；标 `【一手·release】` = LSPosed 官方 GitHub release notes；标 `【二手】` = 镜像站/第三方，需谨慎。Service/远程配置/native hook 专属坑位 → 见 `08-service-and-advanced.md`。

## B.1 生命周期（7 条）

1. **构造器/字段初始化里调 `hook()`/`getXxx()`** → `IllegalStateException: Framework not attached`。每个 `XposedInterfaceWrapper` 方法都以 `ensureAttached()` 开头；框架在实例化后才调 `attachFramework()`。**修复**：初始化放 `onModuleLoaded` 及之后。
2. **在 `onPackageLoaded` 里调 `getClassLoader()`** → 编译不过。`getClassLoader()` 只在 `PackageReadyParam` 上；`PackageLoadedParam` 只有 `getDefaultClassLoader()`（@RequiresApi(Q)）。
3. **把 Hook 装在 `onModuleLoaded`** → `ClassNotFoundException` / hook 无效。该回调时没有任何 ClassLoader（`ModuleLoadedParam` 只有 `isSystemServer()`/`getProcessName()`）。App → `onPackageReady`；system_server → `onSystemServerStarting`。
4. **找资源回调 API**（`handleInitPackageResources` 之类） → 不存在。**资源 Hook 已被官方移除**（wiki："Resource hooks are removed"；feature request `libxposed/api#58` closed/not_planned）。只能 hook 方法改 View/Context。
5. **依赖 `onPackageLoaded` 在 Android < 29 触发** → 永远不触发，无报错。`getDefaultClassLoader()` 带 `@RequiresApi(Q)`。低版本逻辑一律放 `onPackageReady`。
6. **在 `onPackageReady` 之外取 `getAppComponentFactory()`** → Android < 28 取不到。带 `@RequiresApi(P)`，加 SDK_INT 守卫。
7. **system_server 里用 `isFirstPackage()` 判断** → 恒为 false。Javadoc："the first callback is replaced by `onSystemServerStarting(...)`, so `param.isFirstPackage()` is never `true` here."

## B.2 Hook 时机与 ClassLoader（6 条）

8. **两参 `Class.forName(name)` 找宿主类** → `ClassNotFoundException`（类名明明正确）。两参用模块自己的 CL。**修复**：`Class.forName(name, true, param.getClassLoader())`。
9. **`onPackageReady` 里用 `getDefaultClassLoader()`** → App 自定义 AppComponentFactory 换 CL 时 hook 的类与运行时不是同一个。hook 应用代码一律用 `getClassLoader()`（Javadoc："may be different from `getDefaultClassLoader()`"）。
10. **以为 hook 每进程只装一次** → 模块注入作用域内**所有常规进程**（`package-info.java`："The framework injects the module into all regular processes declared by those packages"），每进程独立 JVM。按进程名/包名过滤。
11. **同一方法多次 `hook` 不设 id** → 回调执行多次、热重载后越攒越多。同模块同 Executable 同 id → **原子替换**（"Hook ids are isolated between modules"）。**修复**：始终 `setId(...)`。
12. **用 `static` 缓存状态跨越模块代** → 热重载后读旧数据/旧 CL 泄漏。每代有自己的 ClassLoader。跨代状态走 `setSavedInstanceState()`（只放 classloader 中立值）或 Remote Preferences。
13. **目标类延迟加载/加壳，装 hook 时不存在** → `ClassNotFoundException`。修复：① hook 更早的稳定锚点（如 `Application.attach`）再补装；② `hookClassInitializer(Class)`（⚠️ 类已初始化则 hook 永不触发——Javadoc 明文）；③ DexKit 延迟查找。

## B.3 Chain（10 条）

14. **忘记 `proceed()`** → 原方法逻辑消失（页面空白/功能失效）。`proceed()` 才把调用交给下一环。想完全替换才故意不调。
15. **void 方法 lambda 编译错** → `Hooker.intercept` 返回 `Object`。`chain.proceed(); return null;`（官方注释："for void methods, it's needed to return null because Java doesn't support unit type"）。void/构造器的返回值被框架忽略。
16. **`proceed()` 抛异常没接** → 异常冒到宿主，App 崩溃。**PROTECTIVE 只兜 hooker 自己抛的，`proceed()` 的异常永远传播**（Javadoc："Exceptions thrown by proceed will always be propagated"）。
17. **`chain.getArgs().set(i, v)`** → `UnsupportedOperationException`。getArgs() 是**不可变 List**。改参数 → `chain.proceed(newArgs)`。
18. **`getArg(i)` 越界/类型错** → `IndexOutOfBoundsException`/`ClassCastException`。注意 Kotlin `suspend`/默认参数多出参数、内部类方法首参是外部实例、static 方法无 this。用 `chain.getExecutable()` 打印签名自查。
19. **改参数标准姿势**：`Object[] args = chain.getArgs().toArray(); args[0] = v; return chain.proceed(args);`
20. **改返回值标准姿势**：`Object r = chain.proceed(); return transform(r);`（不 return = 丢弃）。void/构造器 `proceed()` 恒返回 null。
21. **Chain 存字段/跨线程/延迟用** → Javadoc 首句："Chain objects cannot be shared among threads or reused after `Hooker.intercept(Chain)` ends."。异步需求：先拷出 `getArgs()`/`getThisObject()`，不带 chain 进 Runnable。
22. **静态方法调 `proceedWith(...)`** → `IllegalArgumentException`（"Static method interceptors should not call this method"）。静态方法只用 `proceed()`/`proceed(args)`。
23. **以为 `proceedWith(childObj)` 虚分派到 `Child.foo()`** → 仍执行 `Parent.foo()`。Javadoc 整段强调："It does **not** … select a different override, or switch to another executable's hook chain… Use an `Invoker` to start a new invocation when virtual dispatch is desired." 要虚分派 → `getInvoker(childFoo).invoke(childObj, args)`。

## B.4 Invoker（6 条）

24. **`getInvoker(m).invoke(...)` 想调原方法 → 无限递归/`StackOverflowError`**。**默认 type 是 `Chain.FULL`**（含你自己的 hook）。**修复**：`.setType(Invoker.Type.ORIGIN)`（"Invokes the original executable, skipping all hooks"）。
25. **`invoke` vs `invokeSpecial` 混用**：`invoke` 走 hook 链（等同 `Method.invoke`）；`invokeSpecial` **非虚调用**（等价 JNI `CallNonVirtual*`，绕过子类 override 直调声明类实现）。用途："call `super.xxx()` in a hooked constructor"。
26. **`Type.Chain(maxPriority)` 语义**：跳过所有优先级 > maxPriority 的 hook 从链中间开始；`FULL = new Chain(PRIORITY_HIGHEST)`；`ORIGIN` 跳过所有。官方示例：`setType(new Invoker.Type.Chain(-50)).invokeSpecial(...)`。
27. **`setType(ORIGIN)` 当万能递归解药** → 绕掉其它模块的 hook、原方法副作用重复执行。只在明确要原始行为时用；防递归的正确手段是别在拦截器里调用被 hook 的同一方法。【推断】+ 源码语义。
28. **`newInstanceSpecial(subClass, args)` 的 subClass 搞错**：传**子类 Class**，用**父类构造器**初始化；Javadoc 警告 "could leave the object in an invalid state"（子类字段不初始化）。用途：自己初始化子类字段。
29. **忽略 `InvocationTargetException`** → 日志看不到真实原因。`catch (InvocationTargetException e) { Throwable real = e.getTargetException(); }`。Invoker 绕过访问检查（"Invocations through invokers will bypass access checks"）但**不绕**异常包装。

## B.5 内联（5 条）

30. **症状识别**：hook 注册成功（无异常），回调一次不触发；或反射调用能触发、直接调用不能。短方法/getter/boolean 判定最易被内联。
31. **`deoptimize()` 去优化的是调用方，不是目标方法**（最大反直觉）。Javadoc 原例：短方法 B 被 A 内联 → hook B 后回调不触发 → **`deoptimize(A)`**（caller），不是 `deoptimize(B)`。
32. **以为 `deoptimize` 一劳永逸** → Javadoc："you need to find all the callers of your hooked callee, and that can hardly be achieved"。修复：① 换 hook 点；② DexKit 找全部 caller 逐个去优化；③ 兜底=**不卸载重装目标 App**（触发 dex2oat 重编）。
33. **debuggable App / 挂调试器时 hook 失效** → ART 行为不同（v1.8.3/v1.8.1 修复记录：【一手·release】"Fix hook may not work on debuggable apps/modules"）。排障用 release 构建。
34. **内联排查 runbook**：① 回调首行打日志，确认"没进"还是"进了但逻辑错"；② `getInvoker(m).setType(Chain.FULL).invoke(...)` 反射触发 → 指向内联；③ 换锚点/`hookClassInitializer` 提前；④ 全部已知 caller `deoptimize`；⑤ 换 hook 点；⑥ 重装 App。

## B.6 优先级与异常模式（6 条）

35. **硬编码 0/100 当默认/最高** → 常量是 `PRIORITY_DEFAULT=50`、`PRIORITY_LOWEST=Integer.MIN_VALUE`、`PRIORITY_HIGHEST=Integer.MAX_VALUE`。想比默认早用 `PRIORITY_DEFAULT + 10`。
36. **顺序规则**：优先级高者先执行，先 `proceed()` 交出控制，返回值沿链反向传回。
37. **依赖多模块同优先级的顺序** → 未定义（Javadoc 只保证"higher priority called before lower"）。不依赖与其它模块的相对顺序。
38. **`ExceptionMode` 三选一**：`DEFAULT`（跟 `module.prop` 的 `exceptionMode`，缺省 protective）/ `PROTECTIVE`（吞 hooker 异常记日志，官方"recommended for most cases"；proceed 的异常除外）/ `PASSTHROUGH`（异常直达调用方，官方"recommended for debugging"）。
39. **PROTECTIVE 吞 bug 导致"hook 没生效"假象** → 开发期 `module.prop` 写 `exceptionMode=passthrough` 或逐 hook `setExceptionMode(PASSTHROUGH)`，发布改回。
40. **以为 `setExceptionMode` 是全局** → 它是单个 hook 的 builder 方法；全局默认在 `module.prop`。

## B.7 混淆与构建（9 条）

41. **三件套缺任一**（`-dontwarn annotation` / `-adaptresourcefilecontents java_init.list` / `-keep …XposedModule { public <init>(); }`）→ 模块不加载。→ 见 `02-project-scaffold.md §6`。
42. **缺 `-adaptresourcefilecontents`（最经典）** → **debug 正常、release 完全失效**：release 混淆改了类名，清单没同步重写。
43. **`api` 写成 `implementation`** → 日志 `The Xposed API classes are compiled into the module's APK.`（真实 issue `LSPosed#2413`）或 `This class doesn't implement any sub-interface of XposedModule, skipping it`（APK 里是另一份类）。改回 `compileOnly`。
44. **`java_init.list` 为空/全注释** → 判定为 Legacy → 读 `assets/xposed_init` → 没有 → **静默失效**。自检：`unzip -l app-release.apk | grep xposed`。
45. **多入口 + 热重载冲突** → Javadoc："Hot reload is supported only for modules that declare **exactly one Java entry class**. Modules with zero or multiple … are not hot-reloadable" → `HotReloadResult.Status.UNSUPPORTED`。要热重载清单只能一行。
46. **缺 `packaging { merges += "META-INF/xposed/*" }`** → 声明文件没进 APK。与 `excludes += "**"` 成对。
47. **去掉 `-dontwarn io.github.libxposed.annotation.**`** → R8 missing class 警告/失败（annotation 是 compileOnly）。官方还**不建议**主动依赖 annotation 库（`libxposed/api#63`："it's unstable"）。
48. **`minSdk` 与 `minApiVersion` 混填** → 前者 Android SDK（≥26），后者 Xposed API（101/102），完全不同的两个数。
49. **`XposedNewApi` lint 报错** → `Severity.ERROR`。触发条件：`module.prop` `minApiVersion < 102` 且使用 `@SinceApi(102)` API（setId/getId/replaceHook/detach/onHotReloading/onHotReloaded/getRunningTargets/hotReloadModule）且无守卫。修复三选一：① `minApiVersion=102`；② `if (getApiVersion() >= XposedInterface.API_102)`；③ 重写高 @SinceApi 父类方法可豁免。

## B.8 Android 版本与 ART（6 条）

50. **兼容下限**：`api` 库 `minSdk=26`（Android 8.0）【一手·源码】；【二手】镜像线 v2.1.1 起框架最低 Android 9。按 **API 28** 规划下限稳妥。
51. **Android 12+ 静态方法 hook 首次调用失效** → 【一手·release】v1.8.0 修复记录；Android 10+ 内联抑制机制不同。旧版框架上仍会遇到。
52. **Android 13 DP1 系统框架 hook 失效**（`LSPosed#1675`，已修）→ 教训：**每次 Android 大版本升级后重验系统框架 hook**。
53. **hidden API 保护**：`PROP_RT_API_PROTECTION` 置位 = 禁止反射/动态加载代码访问 Xposed API（v1.9.1 起默认开启）。模块必须**直接静态引用** libxposed API。
54. **`targetApiVersion >= 102` 后调 Legacy API** → Javadoc："Libxposed modules can not call legacy `de.robv.android.xposed` APIs."。`de.robv.*` 全部禁止；找类/方法用 JDK 反射。
55. **指望资源 Hook 回归** → 无（v1.6.x 曾有手动开关，现代 API 完全没有）。

## B.9 多进程（4 条）

56. **`isFirstPackage()` 真实含义**：进程内**第一个/主包**。`sharedUserId`/`createPackageContext` 会多包加载 → 每包一次回调。防重复：`if (!param.isFirstPackage()) return;`。
57. **只要某子进程生效** → 进程名只能从 `onModuleLoaded`/`PackageReadyParam`（继承）的 `getProcessName()` 拿。`HotReloadedParam extends ModuleLoadedParam` 也有。
58. **`detach()`（@SinceApi 102）误用**：只停止**当前入口实例**的后续生命周期回调；**API 依然可用**；幂等；多入口只影响调用者。要 CL 被 GC 还须 unhook 全部 + 停线程 + 清外部引用。
59. **跨进程共享状态** → 官方答复（`libxposed/api#61`）："We do not currently provide an API for cross-process communication, you will need to use native Android mechanisms such as binder and provider."

## B.10 日志（4 条）

60. **用 `XposedBridge.log()`/裸 `Log.d()`** → 现代 API 里 `XposedBridge` 不存在（102 还禁止 Legacy API）。用 `log(priority, tag, msg[, tr])`（`XposedInterface` 方法，可直呼）。
61. **`log()` 签名**：`log(int priority, @Nullable String tag, @NonNull String msg[, @Nullable Throwable tr])`；priority 用 `android.util.Log` 常量；**tag 可 null，msg 不可 null**；带 Throwable 的重载是唯一能把堆栈写进 Xposed 日志的方式。
62. **日志在哪**：管理器日志页 + 框架 verbose 日志文件。【推断】`log()` 直写框架日志系统而非 logcat，排障以管理器日志为准。
63. **回调不打日志** → `onModuleLoaded` 至少一行：框架名 + `getApiVersion()` + `getFrameworkProperties()` + 进程名。这是一切排障的起点。

## B.11 配置共享（5 条）

64. **宿主进程内写 Remote Preferences** → **只读**。Javadoc："Note that those are read-only in hooked apps."。写只能在模块 App 侧经 `XposedService.getRemotePreferences()`。
65. **没探测 `PROP_CAP_REMOTE` 就用远程能力** → `UnsupportedOperationException`（embedded 框架）。先 `if ((getFrameworkProperties() & PROP_CAP_REMOTE) != 0)`。
66. **`openRemoteFile(name)` 语义搞错** → 宿主侧**只读**、不存在抛 `FileNotFoundException`；名字**不能含路径分隔符与 `.`/`..`**；写文件走模块 App 侧（可创建）。
67. **配置方案选型**：Remote Preferences（小配置、宿主侧支持变更监听）+ Remote Files（大 blob，存 `/data/adb/lspd/modules/<user>/<module>`）。New XSharedPreferences 是 Legacy 且计划移除。
68. **hook 自己的模块 App 传数据** → **禁止**。官方 wiki："module apps are no longer hooked by themselves"；`libxposed/api#61` 答复 "Use service to communicate"。

## B.12 作用域（5 条）

69. **作用域没勾选** → 模块激活但目标 App 无回调。勾选后**强制停止再启动目标 App**（框架只在进程启动时注入）。
70. **不自己过滤包名** → Javadoc："every loaded package in that process will trigger callbacks… modules may receive callbacks **beyond** the originally scoped packages, so they should **always filter by process name and package name**."。每个 onPackageXXX 第一行过滤。
71. **`staticScope` 写 `True`/`1`** → 不生效（全等比较字符串 `"true"`）。
72. **`system`/`android` 语义**：现代模块中与管理器显示一致——`system`=system_server（`pkg=system`），`android`=系统对话框等进程（`ChooserActivity`/`ResolverActivity`，`proc=android:ui`）。`android` 包无代码但仍是合法作用域；全组件都在 system_server 的 `android.uid.system` 包必须用 `system` 作用域；`com.android.providers.settings` **不是**合法作用域目标（用 `system` + 等它的包加载事件）。
73. **【二手】管理器作用域排序异常**（镜像线 v2.2.0 提到修复）→ 未获一手验证；排障时手动确认勾选状态，别依赖排序。

## B.13 安全与稳定（5 条）

74. **回调抛异常的后果**：PROTECTIVE=吞掉记日志（App 不崩但逻辑静默失效）；PASSTHROUGH=直达调用方（崩溃）。生产 PROTECTIVE、开发 PASSTHROUGH。
75. **拦截器里调被 hook 的方法** → 递归/回调放大。`getInvoker(m).invoke(...)` 默认 FULL 链含自己。原行为用 `setType(ORIGIN)`。框架有防无限递归兜底（【一手·release】v1.8.2）但别依赖。
76. **【二手】每方法回调数量上限**（镜像线 v2.0.2"减少每个方法的最大回调数量"）→ 单方法别挂太多 hook；反复注册不 `setId()`/不 `unhook()` 会逼近上限 → "hook 突然不生效"。
77. **回调里做重活** → `intercept` 在**调用方线程**同步执行；高频主线程方法里 I/O/反射 → 掉帧 ANR。反射结果缓存；配置用缓存值 + 监听器。
78. **捕获 `HookFailedError`** → Javadoc："hook failures are considered fatal framework bugs. Module developers should not attempt to catch this error… report the issue to the framework maintainers."。它继承 `Error`；`catch (Throwable)` 时要重新抛出它。

## B.14 热重载（7 条，更多细节 → 见 `08-service-and-advanced.md §4`）

79. **不重写 `onHotReloading`** → 默认返回 `false` = **拒绝一切热重载**（service 触发时报 `FAILED` 且 message 为 null）。`autoHotReload=true` 也不够——"App-update hot reloading still proceeds only if this callback returns `true`"。
80. **返回 true 前不清理** → 必须：停模块自有 Java/native 线程、注销 native hook 与外部回调、释放指向模块 CL 对象的 JNI 全局引用、清系统/App 类持有的模块引用。框架**不会**调 `UnregisterNatives`/`JNI_OnUnload`/`dlclose`；残留引用的崩溃"**is a module bug**"。
81. **以为生命周期回调会重放（最大陷阱）** → Javadoc："Hot reload does not automatically replay this callback or package lifecycle callbacks"。`onHotReloaded` 默认实现**只做 unhook 全部旧 hook** → 不重写 = 热重载成功但功能全消失。
82. **旧 HookHandle 三种处置**：`old.unhook()`（移除）/ `old.replaceHook(newHooker)`（原子替换：保留 executable/priority/exceptionMode/id，**调用成功后 old 失效**）/ 重新 `hook().setId(sameId)`（等价）。链是快照式："Replacing a hook while a call is running does not affect that in-flight call."
83. **`setSavedInstanceState` 放旧 CL 对象** → `IllegalArgumentException`（框架检测）。只放 primitive/String/数组/框架 Bundle（classloader 中立值）。
84. **`HotReloadResult.Status` 五态**：`SUCCEEDED`/`FAILED`（message null=旧代拒绝；非 null=重载异常诊断）/`UNSUPPORTED`（无恰好一个入口类等）/`IN_PROGRESS`/`PROCESS_DIED`。
85. **用热重载下发配置** → 反模式。Javadoc："It should not be used to propagate configuration changes. For configuration updates, use `getRemotePreferences(String)` and `OnSharedPreferenceChangeListener`."。且回调可能跑在 **Binder 线程**。

## B.15 其它（5 条）

86. **引入 `io.github.libxposed:helper`** → 停在 API 100（2023），与 102 Chain 模型不兼容。官方 wiki："We no longer provide interfaces like `XposedHelpers`…"。用 JDK 反射 + `getInvoker()`。
87. **反射调 Xposed API / 动态加载 dex 后调 API** → `PROP_RT_API_PROTECTION` 拦截。直接静态引用。
88. **解析 `getFrameworkVersion()` 字符串做判断** → 版本判断只走 `getApiVersion()` + `getFrameworkProperties()` 位掩码。框架版本仅用于展示。
89. **混淆模块 APK 路径与宿主数据目录** → `getModuleApplicationInfo().sourceDir` 是**模块 APK** 路径；宿主进程里写"模块自己的数据目录"无效（那是宿主沙盒）。共享数据用 Remote Files。
90. **hook `Constructor.newInstance`** → `IllegalArgumentException`（Javadoc：origin 为 framework internal 或 `Constructor#newInstance` 时抛）。要拦截对象创建 → hook **具体构造器** `getDeclaredConstructor(...)`。

---

## 快速索引（症状 → 坑号）

| 症状 | 先查 |
|---|---|
| `IllegalStateException: Framework not attached` | #1 |
| `ClassNotFoundException`（类名确认正确） | #3、#8、#13 |
| hook 装上了但回调从不触发 | #30-34（内联）、#69（作用域）、#39（异常被吞） |
| debug 正常 release 失效 | #42 |
| 回调执行多次 / 递归 | #11、#24、#75 |
| 改参数/返回值不生效 | #17、#19、#20 |
| `StackOverflowError` | #24、#75 |
| 热重载后功能消失 | #79-82 |
| lint `XposedNewApi` 报错 | #49 |
| 模块在管理器里消失/显示 Legacy | #44、`02-project-scaffold.md §7` |
| `UnsupportedOperationException`（远程能力） | #65、#66 |
| 宿主侧改配置不生效 | #64、#68 |


---

## C. v2.x 框架级陷阱（【一手·changelog】）

> 以下坑点来自 v2.0.2~v2.2.0 官方 changelog 中修复的真实问题，Agent 生成代码时必须规避。

### C.1 API 102 与 Legacy API 物理隔离（v2.0.3）

**症状**：模块 `targetApiVersion=102`，但代码中混用了 `de.robv.android.xposed.*` 和 `io.github.libxposed.*`，管理器显示"模块已加载"但所有 hook 不生效。
**原因**：v2.0.3 起实施物理级硬隔离。`targetApiVersion ≥ 102` 的模块调用 Legacy API 会被框架拦截并静默失败；反之 Legacy 模块调用 libxposed API 同样失败。
**规避**：
- `targetApiVersion ≥ 102` 的模块：**禁止** `import de.robv.android.xposed.*`，全部改用 `io.github.libxposed.*`。
- Legacy 模块（`targetApiVersion ≤ 101`）：**禁止** `import io.github.libxposed.*`。
- ProGuard 规则中不要 keep `de.robv.android.xposed.**`（API 102 模块）。

### C.2 Hook 链参数类型转换问题（v2.2.0 修复）

**症状**：多个模块 hook 同一方法时，`param.args[i]` 在某些情况下抛出 `ClassCastException` 或返回 `null`。
**原因**：v2.2.0 之前的框架在 Hook 链传递参数时，对泛型擦除后的类型转换存在缺陷（如 `List<String>` 在链中传递时被错误转换为 `List<Object>`）。
**规避**：
- 在 `beforeHookedMethod`/`afterHookedMethod` 中对 `param.args[i]` 增加 `instanceof` 检查。
- 若目标框架版本未知，使用防御性编程：
```java
Object arg = param.args[0];
if (arg instanceof String) {
    String str = (String) arg;
    // ...
}
```
- 推荐升级到 v2.2.0+ 以获取修复。

### C.3 XposedHelpers.findField 行为异常（v2.1.1 修复）

**症状**：`XposedHelpers.findField` 在某些 Android 版本上抛出非预期异常（如 `NoSuchFieldError` 而非 `NoSuchFieldException`）。
**原因**：v2.1.1 之前的实现在反射查找字段时，对某些 ART 内部类的字段处理不当。
**规避**：
- 对 `findField` 调用统一使用 `try-catch` 包裹，同时捕获 `NoSuchFieldException` 和 `NoSuchFieldError`：
```java
try {
    Field f = XposedHelpers.findField(clazz, "fieldName");
} catch (NoSuchFieldException | NoSuchFieldError e) {
    // 降级处理
}
```

### C.4 热重载状态异常（v2.2.0 修复）

**症状**：热重载失败后，模块状态异常（如 hook 残留但新逻辑未装），后续热重载请求被框架拒绝。
**原因**：v2.2.0 之前，热重载过程中若发生异常（如新模块 ClassLoader 加载失败），框架未正确回滚状态，导致后续请求被误判为"正在进行中"。
**规避**：
- 在 `onHotReloading` 中返回 `false` 的条件要严格（如检测到关键依赖缺失时主动拒绝）。
- 升级框架到 v2.2.0+。
- 若检测到热重载后模块状态异常，可尝试通过 `XposedService.hotReloadModule()` 重新触发。

### C.5 资源 Hook XML 重写长期失效（v2.2.0 修复）

**症状**：使用 `XResources` hook XML 资源时，修改未生效。
**原因**：v2.2.0 之前的框架在资源 Hook 的 XML 重写逻辑中存在长期未修复的 bug。
**规避**：
- 优先使用 Java 层 Hook（hook `Resources.getDrawable`/`getString` 等方法）替代 `XResources` XML 重写。
- 若必须使用 XML 重写，升级框架到 v2.2.0+。

## 未验证 / 未获取到

- #37 同优先级多模块的 tie-break 规则、#76 回调数量上限的具体数值：无一手来源（前者为【推断】，后者仅【二手】定性描述）。
- #62 `log()` 是否同时进 logcat：未确认（Javadoc 只说 "Writes a message to the Xposed log"）。
- #64 宿主侧对 `getRemotePreferences` **写入**的具体失败形态（静默失败 vs 抛异常）：无一手证据，一律按"只读"处理。
- #73、#76 的镜像线 changelog 条目（v2.2.0 作用域排序、v2.0.2 回调上限）无法在 GitHub 交叉验证（官方仓库无 2.x release）。
- #53 `PROP_RT_API_PROTECTION` 的具体拦截行为（抛什么异常/日志形态）未取到一手描述。
