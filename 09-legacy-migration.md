# 09 · Legacy → Modern 迁移指南

> **本册定位**：`de.robv.android.xposed`（Legacy Xposed API）到 libxposed API 102 的逐项映射表与迁移步骤。**本册是知识库中唯一完整介绍 Legacy API 的分册**——其它分册只在"防止被旧教程误导"时一句话点名。
> **何时读这一册**：维护旧模块并向现代 API 迁移；读懂网上大量基于 Legacy API 的旧教程/旧源码；判断某个 Legacy 惯用法在 102 下的等价物。
> **前置依赖**：Modern 侧语义 → 见 `03-api-reference.md`、`04-lifecycle-and-patterns.md`。
> **交叉引用**：→ 见 `02-project-scaffold.md`（新工程配置）、`03-api-reference.md §4-§5`、`05-pitfalls.md #54`

---

**来源声明**：【一手·文档】官方 Wiki API Changes；【一手·源码】官方示例与框架解析代码（`ConfigFileManager`/`ModuleUtil`）。⚠️ **官方没有专门的迁移页**——wiki 7 页无迁移页、四个相关仓库 README 均无迁移章节，本册映射表由 wiki API Changes + 示例源码 + 框架解析代码拼装。Legacy 签名参考 api.xposed.info 文档【二手·历史文档】。

**硬约束**：`module.prop` `targetApiVersion >= 102` 时，模块**禁止**调用任何 legacy `de.robv.android.xposed` API（Javadoc 原文："Libxposed modules can not call legacy `de.robv.android.xposed` APIs."）。

---

## 1. 声明与入口迁移

| Legacy | Modern (API 102) | 说明 |
|---|---|---|
| `assets/xposed_init` | `META-INF/xposed/java_init.list` | 位置从 `assets/` 换到 `resources/`；一行一个 FQCN |
| `assets/native_init` | `META-INF/xposed/native_init.list` | |
| `xposedminversion` meta-data | `module.prop` 的 `minApiVersion` + `targetApiVersion` | ⚠️ 保留 `xposedminversion` 会让管理器把模块判为 Legacy |
| `xposeddescription` meta-data | `android:description` 资源 | |
| `xposedscope` meta-data（`@array` 或 `;` 分隔 value） | `META-INF/xposed/scope.list` | 每行一个包名；不 trim、不支持注释 |
| `xposedsharedprefs` meta-data | 移除 → Remote Preferences | |
| `IXposedHookLoadPackage.handleLoadPackage(LoadPackageParam)` | `onPackageLoaded(PackageLoadedParam)` / **`onPackageReady(PackageReadyParam)`** | hook 写在 `onPackageReady`（真实 classloader 就绪） |
| `IXposedHookZygoteInit.initZygote(StartupParam)` | `onModuleLoaded(ModuleLoadedParam)`（进程级初始化）/ **`onSystemServerStarting`**（系统框架） | zygote 级注入在 101+ 不存在 |
| `IXposedHookInitPackageResources.handleInitPackageResources` | ❌ **无对应——资源 Hook 已移除** | 需重新设计功能 |
| `IXposedHookCmdInit` | ❌ 无对应 | |
| 构造器接收 `(XposedInterface, ModuleLoadedParam)`（API 100） | **无参构造器**；框架自动 `attachFramework` | 初始化放 `onModuleLoaded` 之后 |

**Legacy 参数 → Modern Param**：

| `lpparam.*`（Legacy） | Modern |
|---|---|
| `packageName` | `PackageLoadedParam.getPackageName()` |
| `processName` | `ModuleLoadedParam.getProcessName()` |
| `classLoader` | `PackageReadyParam.getClassLoader()`（`onPackageLoaded` 里只有 `getDefaultClassLoader()`） |
| `appInfo` | `PackageLoadedParam.getApplicationInfo()` |
| `isFirstApplication` | `PackageLoadedParam.isFirstPackage()` |
| `XposedBridge.BOOTCLASSLOADER` | `SystemServerStartingParam.getClassLoader()`（system_server 中） |

**作用域语义陷阱**：Legacy 模块声明的作用域中 `android`/`system` **语义相反**（框架自动互换，历史遗留）；现代模块的 `scope.list` 与管理器显示一致。迁移时若原 `xposedscope` 里写过 `android` 或 `system`，**按现代语义重新确认**（`system`=system_server，`android`=系统对话框进程）→ 见 `05-pitfalls.md #72`。

## 2. Hook 与回调迁移

| Legacy | Modern (API 102) |
|---|---|
| `XposedBridge.hookMethod(Member, XC_MethodHook)` | `hook(executable).setPriority(p).setExceptionMode(m).setId(id).intercept(hooker)` → `HookHandle` |
| `XposedHelpers.findAndHookMethod(Class, String, Object...)` | `hook(clazz.getDeclaredMethod(name, types)).intercept(hooker)` |
| `XposedHelpers.findAndHookConstructor(...)` | `hook(clazz.getDeclaredConstructor(types)).intercept(hooker)` |
| `XposedBridge.hookAllMethods(Class, String, cb)` | **无直接对应**：遍历 `getDeclaredMethods()` 按名过滤逐个 hook |
| `XposedBridge.hookAllConstructors(...)` | **无直接对应**：遍历 `getDeclaredConstructors()` 逐个 hook |
| `beforeHookedMethod(param)` | `intercept(chain)` 中 `chain.proceed()` **之前**的代码 |
| `afterHookedMethod(param)` | `intercept(chain)` 中 `chain.proceed()` **之后**的代码 |
| `XC_MethodReplacement` | `intercept(chain -> v)`，**不调 `proceed()`** |
| `XC_MethodHook(int priority)` | `.setPriority(int)`（⚠️ Legacy 的 after 逆序执行；Modern 是统一链序） |
| `param.args` | `chain.getArgs()`（⚠️ **不可变 List**，Legacy 可直接改元素） |
| `param.args[i] = v` | `chain.proceed(newArgs)` / `chain.proceedWith(newThis, newArgs)` |
| `param.thisObject` | `chain.getThisObject()` |
| `param.setResult(v)`（before，跳过原方法） | 不调 `proceed()`，直接 `return v;` |
| `param.setResult(v)`（after，改返回值） | `chain.proceed(); return v;` |
| `param.setThrowable(t)` | `throw t;`（从 `intercept` 抛出；配合 `ExceptionMode.PASSTHROUGH` 传给宿主） |
| `param.getResult()` | `chain.proceed()` 的返回值 |
| `param.getThrowable()` / `hasThrowable()` | `try { chain.proceed(); } catch (Throwable t) { ... }` |
| `param.returnEarly` | 隐式：不调 `proceed()` 即跳过 |
| `MethodHookParam.method` | `chain.getExecutable()` |
| `XposedBridge.invokeOriginalMethod(m, this, args)` | `getInvoker(m).setType(Invoker.Type.ORIGIN).invoke(thisObject, args)` |
| `XposedBridge.unhookMethod` / `Unhook#unhook()` | `HookHandle.unhook()`（幂等） |
| `XposedBridge.log(String)` | `log(Log.INFO, tag, msg)` |
| `XposedBridge.log(Throwable)` | `log(Log.ERROR, tag, msg, tr)` |
| `XposedBridge.getXposedVersion()` | `getApiVersion()`（⚠️ 语义不同：API 版本，不是 bridge 版本） |
| `setAdditionalInstanceField` / `getAdditional...` | **无对应** → 自维护 `WeakHashMap<Object, Map<String,Object>>` |
| `incrementMethodDepth` / `getMethodDepth` | **无对应** → 自维护 `ThreadLocal<Integer>` |
| `XSharedPreferences` / New XSharedPreferences | `getRemotePreferences(group)`（小配置）；`openRemoteFile(name)`（大文件） |

### before/after 迁移样板

```java
// Legacy:
//   XC_MethodHook: beforeHookedMethod → log + 改 args[0]; afterHookedMethod → 改 result
// Modern (API 102):
// app/src/main/java/com/example/myhook/ModuleMain.java （片段）
hook(targetMethod).intercept(chain -> {
    // ── before 区：proceed() 之前 ──
    Object[] args = chain.getArgs().toArray();   // getArgs() 不可变，先拷
    args[0] = "rewritten";
    Object result = chain.proceed(args);          // ── 原方法在这里执行 ──
    // ── after 区：proceed() 之后 ──
    return "wrapped:" + result;
});
```

## 3. `XposedHelpers` 反射工具迁移

| `XposedHelpers` | Modern |
|---|---|
| `findClass(String, ClassLoader)` | `Class.forName(name, true, classLoader)`（**三参**，两参会用模块自己的 CL） |
| `findClassIfExists` | `Class.forName` + catch `ClassNotFoundException` |
| `findMethodExact(Class, String, Class...)` | `clazz.getDeclaredMethod(name, types)` |
| `findMethodBestMatch` | 遍历 `getDeclaredMethods()` 自行匹配（helper 库**已停更，勿引**） |
| `findConstructorExact` / `findConstructorBestMatch` | `getDeclaredConstructor(types)` / 遍历 |
| `findField(Class, String)` | `clazz.getDeclaredField(name)` |
| `findFirstFieldByExactType` | 遍历 `getDeclaredFields()` |
| `getObjectField` / `setObjectField` | `f.setAccessible(true); f.get(obj)` / `f.set(obj, v)` |
| `getStaticObjectField` / `setStaticObjectField` | 同上，对象传 `null` |
| `callMethod(Object, String, Object...)` | `getInvoker(m).invoke(obj, args)` 或 `m.setAccessible(true); m.invoke(obj, args)` |
| `callStaticMethod(...)` | 同上，`obj=null` |
| `newInstance(Class, Object...)` | `getInvoker(ctor).newInstance(args)` 或 `ctor.newInstance(args)` |
| `getSurroundingThis` | **无对应** → 读 `this$0` 字段 |

**两条重要差异**：
1. `getInvoker()` 返回的 Invoker **绕过访问检查**（Javadoc："Invocations through invokers will bypass access checks"）→ 多数场景不需要 `setAccessible(true)`；直接用 `Field`/`Method` 时仍需要。
2. Android 9+ 隐藏 API 限制不受 `setAccessible` 影响，需要 hiddenapibypass 等手段（框架注入不改变此规则）。

## 4. 迁移步骤（12 步，按序执行）

1. **改入口类**：新建 `extends XposedModule` 的 public 类 + 公开无参构造器；初始化移入 `onModuleLoaded` 之后。
2. **改声明**：删 `assets/xposed_init`；建 `src/main/resources/META-INF/xposed/java_init.list` 写入 FQCN。
3. **建 `module.prop`**：`minApiVersion` / `targetApiVersion` / `staticScope`（小写 `"true"`）。
4. **建 `scope.list`**：每行一个包名，无空行无注释。
5. **清 manifest**：删除 `xposedminversion`/`xposeddescription`/`xposedscope`/`xposedsharedprefs` 四个 meta-data；模块名/描述改用 `android:label`/`android:description`。
6. **加构建配置**：`packaging { merges += "META-INF/xposed/*"; excludes += "**" }` + ProGuard 三件套（→ `02-project-scaffold.md §3/§6`）。
7. **重写 hook 代码**为 Chain/Interceptor 形式（§2 对照表）。
8. **替换 `XposedHelpers`** → JDK 反射 + `getInvoker()` 体系（§3）。
9. **替换配置读取** → `getRemotePreferences()` / `openRemoteFile()`；写配置走模块 App 侧 `XposedService`。
10. **注册 `XposedServiceHelper.registerListener()`**（模块 App 的 `Application.onCreate`，只调一次）→ 见 `08-service-and-advanced.md §2`。
11. **移除全部资源 Hook 代码**（无替代，重新设计）。
12. **检查 Gradle**：`compileOnly(api)`、`implementation(service)`、Java 17+、`minSdk 26+`。

## 5. 迁移中最容易漏的三件事

1. **`packaging` 块**：漏了 → 声明文件不进 APK → 管理器识别异常/静默失效。
2. **`-adaptresourcefilecontents`**：开混淆但漏了 → **debug 正常、release 必然失效**（有迷惑性，可能到线上才暴露）。
3. **残留 `xposedminversion` meta-data** → 管理器把现代模块判为 Legacy，读 Legacy 通道的元数据。

另一个行为级差异：迁移后**模块 App 自身进程不再被 hook**（官方 wiki："module apps are no longer hooked by themselves"）——Legacy 时代"hook 自己的 App 传数据"的套路彻底失效，改用 XposedService。

## 6. 现代模块还要不要兼容 Legacy 框架？

**不要做"双开"尝试**：框架判定是**二选一开关**——`java_init.list` 非空 → 现代模式（根本不读 `assets/xposed_init`）；为空 → 回退 Legacy 路径。同时放两个文件不能让一份 APK 同时在新旧框架上按现代 API 加载。且 `targetApiVersion >= 102` 后调用 Legacy API 是被禁止的行为。**结论：一次迁移到位，不做双轨。**

---



---

## 7. New XSharedPreferences 的现状与迁移

> **重要**：v2.2.0 官方 changelog 宣布 New XSharedPreferences (NSP) **计划于 v2.3.0 正式移除**，并自 v2.2.0 起对有兼容风险的模块显示废弃警告。NSP 依赖 `MODE_WORLD_READABLE`，这本身就是被 Android 淘汰的机制，**Legacy 模块应尽快迁移到 Remote Preferences**。

### 7.1 废弃机制

- **判定逻辑**：框架检查模块是否同时满足：① `AndroidManifest.xml` 中存在 `xposedsharedprefs` 元数据；② 偏好设置文件为 `others` 可读（`MODE_WORLD_READABLE`）。
- **表现形式**：满足条件的模块在管理器页面显示"废弃警告"。

### 7.2 迁移方案（二选一）

#### 方案 A：迁移到 libxposed API 102（推荐）

- 放弃 NSP，改用 `RemotePreferences`（实现 `SharedPreferences` 接口，支持变更监听）。
- 删除 `AndroidManifest.xml` 中的 `xposedsharedprefs` 元数据。
- 使用 `io.github.libxposed:service:102.0.0` 的 `XposedService.getRemotePreferences()`。
- 详见 `08-service-and-advanced.md §1`。

#### 方案 B：保持 Legacy 但消除警告

- 在 `AndroidManifest.xml` 中把 `xposedminversion` meta-data 设置为 `82`（NSP 引入版本）。
- 删除 `AndroidManifest.xml` 中的 `xposedsharedprefs` 元数据（框架不再识别此标志）。
- 确保偏好设置文件权限为 `MODE_PRIVATE`，不再依赖 `MODE_WORLD_READABLE`。
- **注意**：此方案只消除当前警告，NSP 仍将在 v2.3.0 被移除。

### 7.3 Agent 迁移检查清单

- [ ] 检查 `AndroidManifest.xml` 是否存在 `<meta-data android:name="xposedsharedprefs" android:value="true" />`。
- [ ] 若存在，选方案 A（迁移到 libxposed API 102）或方案 B（`xposedminversion=82` + 删除 `xposedsharedprefs`）。
- [ ] 若选方案 A，把所有 `XSharedPreferences` 调用替换为 `XposedService.getRemotePreferences()`。
- [ ] 若选方案 B，`xposedminversion` 写在 **`AndroidManifest.xml` 的 meta-data**里——`module.prop` 没有这个字段。
- [ ] 测试模块在目标框架下不再显示废弃警告。


## 8. 未验证 / 未获取到

- `IXposedHookCmdInit` 是否存在任何 Modern 对应物：wiki 与四个仓库中均未出现。
- Legacy `XC_MethodHook` 的 after 逆序与 Modern 链序在**多 hook 同一方法**时的行为对照：Modern 链序有 Javadoc 保证，与 Legacy 的精确对齐关系未验证。
- New XSharedPreferences 的**正式移除**（计划于 v2.3.0）尚未落地，实现细节可能变动；v2.2.0 起的废弃警告机制已确认。
- `getInvoker` 绕过访问检查是否同样绕过 hidden API 限制：未验证（推断只绕 Java 层访问检查）。
