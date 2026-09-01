# 10 · AI Agent 硬性规则集（DO / DON'T）

> **本册定位**：机器可读的生成前检查规则。**生成任何 libxposed API 102 模块代码之前，逐条过一遍本册**。
> **何时读这一册**：每次要写/改模块代码时（第一个加载）；代码写完后按 §4 checklist 自查。
> **前置依赖**：无（各条独立）。逐条出处 → 见 `03-api-reference.md`、`05-pitfalls.md` 对应编号。
> **交叉引用**：→ 见 `05-pitfalls.md`（症状索引）、`06-debugging-runbook.md §A3`、`04-lifecycle-and-patterns.md §5`

---

## 1. DON'T（硬禁令，违反 = 模块失效/崩溃/编译失败）

```yaml
- rule: D01
  forbid: 在构造器或字段初始化器里调用 hook()/log()/任何 XposedInterface 方法
  because: 框架未 attach，抛 IllegalStateException("Framework not attached")
  ref: "05-pitfalls.md #1"

- rule: D02
  forbid: 入口类缺少 public 无参构造器（或只写了带参构造器）
  because: 框架反射实例化失败，模块静默失效
  ref: "03-api-reference.md §10"

- rule: D03
  forbid: 把 io.github.libxposed:api 写成 implementation
  because: 框架检测到 API 类打进 APK 后拒绝加载（日志 "The Xposed API classes are compiled into the module's APK."）
  ref: "05-pitfalls.md #43"

- rule: D04
  forbid: 遗漏 packaging { resources { merges += "META-INF/xposed/*"; excludes += "**" } }
  because: 声明文件不进 APK
  ref: "05-pitfalls.md #46"

- rule: D05
  forbid: 开启混淆时缺三条 ProGuard 规则（尤其 -adaptresourcefilecontents META-INF/xposed/java_init.list）
  because: debug 正常、release 完全失效
  ref: "05-pitfalls.md #41-42"

- rule: D06
  forbid: 在 manifest 写任何 xposed meta-data（xposedmodule/xposedminversion/xposeddescription/xposedscope/xposedsharedprefs）
  because: Legacy 残留；xposedminversion 会让管理器按 Legacy 通道解析
  ref: "02-project-scaffold.md §5"

- rule: D07
  forbid: 调用任何 de.robv.android.xposed.* Legacy API
  because: targetApiVersion >= 102 时框架禁止（行为变更条款）
  ref: "05-pitfalls.md #54"

- rule: D08
  forbid: 引入 io.github.libxposed:helper
  because: 停在 API 100（2023 停更），与 102 Chain 模型不兼容
  ref: "05-pitfalls.md #86"

- rule: D09
  forbid: 写资源 Hook 代码（handleInitPackageResources/onResourceLoaded/hookXml 等）
  because: 现代 API 无此能力，资源 Hook 已移除
  ref: "05-pitfalls.md #4"

- rule: D10
  forbid: 把 io.github.libxposed:annotation 作为依赖引入
  because: 官方明示不稳定（libxposed/api#63），正规做法是 -dontwarn
  ref: "05-pitfalls.md #47"

- rule: D11
  forbid: 修改 chain.getArgs() 返回的 List / 把 chain 存字段、跨线程、intercept 返回后复用
  because: List 不可变；Chain 不可共享不可复用
  ref: "05-pitfalls.md #17、#21"

- rule: D12
  forbid: 静态方法的拦截器调 proceedWith(...)；用 proceedWith 做虚分派
  because: 前者抛 IllegalArgumentException；后者不做虚派发（仍执行声明类实现）
  ref: "05-pitfalls.md #22、#23"

- rule: D13
  forbid: hook Constructor.newInstance；hook framework internal 类
  because: intercept 契约性抛 IllegalArgumentException。拦截对象创建 → hook 具体构造器
  ref: "05-pitfalls.md #90"

- rule: D14
  forbid: catch HookFailedError 做降级
  because: 它继承 Error，官方定为"致命框架 bug，应上报维护者"
  ref: "05-pitfalls.md #78"

- rule: D15
  forbid: 比较框架版本字符串（getFrameworkVersion().equals(...)）或硬编码框架版本号
  because: 存在两条冲突的 2.x 线；环境判断只走 getApiVersion() + getFrameworkProperties() 位掩码
  ref: "07-compat-matrix.md §0、§4"

- rule: D16
  forbid: 在 system_server 里用 isFirstPackage() 做判断
  because: Javadoc 明文该值在 system_server 恒为 false
  ref: "05-pitfalls.md #7"

- rule: D17
  forbid: 模块 App 侧假设自己进程会被 hook（在模块 App 里等 XposedInterface 回调）
  because: "module apps are no longer hooked by themselves"；跨进程通信用 XposedService
  ref: "05-pitfalls.md #68"

- rule: D18
  forbid: 把 PROP_RT_* 能力位缓存到磁盘跨启动使用
  because: Javadoc 明文 PROP_RT_ 前缀属性每次启动可能变化
  ref: "07-compat-matrix.md §4"

- rule: D19
  forbid: 用热重载（hotReloadModule）下发配置变更；在 hotReloadModule 的 Bundle 里放模块自定义 Parcelable/Serializable
  because: Javadoc 明确反对前者；后者无法脱离模块 classloader 反序列化
  ref: "05-pitfalls.md #85"

- rule: D20
  forbid: 在 scope.list 里写空行/注释/行尾空格
  because: 解析不 trim 不过滤空行不支持注释（与 java_init.list 不同）
  ref: "02-project-scaffold.md §4.3"

- rule: D21
  forbid: 在 onPackageLoaded 里调 getClassLoader()
  because: 该方法只在 PackageReadyParam 上存在
  ref: "05-pitfalls.md #2"

- rule: D22
  forbid: 用两参 Class.forName 找宿主类；用 getDefaultClassLoader() hook 应用代码
  because: 前者用模块 CL 必然失败；后者在自定义 AppComponentFactory 时拿错 CL
  ref: "05-pitfalls.md #8、#9"
```

## 2. DO（硬性要求，缺失 = 功能不完整）

```yaml
- rule: R01
  require: 依赖 compileOnly("io.github.libxposed:api:102.0.0")；用到 XposedService 时 implementation("io.github.libxposed:service:102.0.0")
  ref: "02-project-scaffold.md §2"

- rule: R02
  require: 入口类 public final class XxxMain extends XposedModule + 公开无参构造器
  ref: "04-lifecycle-and-patterns.md §3"

- rule: R03
  require: src/main/resources/META-INF/xposed/java_init.list 至少一行有效 JVM 类名；要热重载则恰好一行
  ref: "02-project-scaffold.md §4.2"

- rule: R04
  require: module.prop 含 minApiVersion 与 targetApiVersion（都显式写出）；staticScope 用小写 true
  ref: "02-project-scaffold.md §4.1"

- rule: R05
  require: Hook 只装在 onPackageReady（App）或 onSystemServerStarting（system_server）
  ref: "04-lifecycle-and-patterns.md §1-§2"

- rule: R06
  require: 每个 onPackageXXX 回调第一行过滤 getPackageName()；多包进程加 isFirstPackage() 判断
  because: 注入后回调会超出 scope 范围
  ref: "05-pitfalls.md #56、#70"

- rule: R07
  require: Class.forName(name, true, param.getClassLoader()) 三参形式
  ref: "05-pitfalls.md #8"

- rule: R08
  require: hook(executable) 后必须 .intercept(hooker) 才生效；每个 hook 都 .setId(...)
  because: hook() 返回 HookBuilder 不是 Handle；同 id 重复注册自动原子替换
  ref: "05-pitfalls.md #11；03-api-reference.md §4"

- rule: R09
  require: 保留原逻辑必须 chain.proceed()；void 方法/构造器 return null（Java lambda）
  ref: "05-pitfalls.md #14、#15"

- rule: R10
  require: 改参数 chain.proceed(newArgs)；改返回值 proceed() 后 return 新值；虚分派用 getInvoker(...)
  ref: "05-pitfalls.md #19、#20、#23"

- rule: R11
  require: 调原方法 getInvoker(m).setType(Invoker.Type.ORIGIN).invoke(...)（默认 FULL 链会递归）
  ref: "05-pitfalls.md #24"

- rule: R12
  require: hook 系统框架/短方法时，对【调用方】deoptimize（不是目标方法）
  ref: "05-pitfalls.md #31"

- rule: R13
  require: hook 安装块整体 try/catch(Throwable) 并 log(Log.ERROR, ..., t)；回调首行打点
  ref: "04-lifecycle-and-patterns.md §5.2"

- rule: R14
  require: onModuleLoaded 打一行指纹日志（框架名+版本+api+props+进程名）
  ref: "06-debugging-runbook.md §A1.3"

- rule: R15
  require: 生产用 PROTECTIVE；开发期用 PASSTHROUGH（或 module.prop exceptionMode=passthrough）
  because: PROTECTIVE 吞异常伪造成"hook 没生效"
  ref: "06-debugging-runbook.md §A2.2"

- rule: R16
  require: 102+ API（setId/getId/replaceHook/detach/onHotReloading/onHotReloaded/getRunningTargets/hotReloadModule）用 if (getApiVersion() >= 102) 守卫，或 minApiVersion=102
  because: 规避 XposedNewApi lint ERROR；旧框架上防崩溃
  ref: "05-pitfalls.md #49"

- rule: R17
  require: 环境判断只用 getApiVersion() + getFrameworkProperties() 位掩码；远程 API 包 try/catch(UnsupportedOperationException)
  ref: "07-compat-matrix.md §3-§4"

- rule: R18
  require: 配置共享用 Remote Preferences（小配置）+ Remote Files（大文件）；写配置只在模块 App 侧
  ref: "05-pitfalls.md #64、#67"

- rule: R19
  require: 热重载三件套：单一入口类 + onHotReloading 返回 true（且先完成线程/hook/JNI 清理）+ onHotReloaded 里对旧 handle 做 replaceHook
  because: 默认 onHotReloading=false 拒绝一切；默认 onHotReloaded 只 unhook
  ref: "05-pitfalls.md #79-82"

- rule: R20
  require: 反射查找结果缓存到字段；回调内不做 I/O/重活
  because: intercept 在调用方线程同步执行
  ref: "05-pitfalls.md #77"

- rule: R21
  require: XposedServiceHelper.registerListener 只在 Application.onCreate 调一次；XposedService 相关 UI 必须有 service==null 降级路径
  ref: "08-service-and-advanced.md §2"

- rule: R22
  require: 发版前过 §4 的 checklist 与 07 册 §5 兼容清单
  ref: "07-compat-matrix.md §5"
```

## 3. 判定规则（二选一场景的决策依据）

```yaml
- rule: J01
  question: onPackageLoaded 还是 onPackageReady 装 hook？
  decision: 一律 onPackageReady（真实 classloader）；onPackageLoaded 仅用于观察默认 CL（且 Android <29 不触发）

- rule: J02
  question: 改参数/改 this/改返回值用哪个方法？
  decision: |
    只改参数 → chain.proceed(newArgs)
    只改接收者 → chain.proceedWith(newThis)（非静态方法）
    都改 → chain.proceedWith(newThis, newArgs)
    只改返回值 → proceed() 后 return
    完全替换 → 不调 proceed() 直接 return
    要虚分派 → getInvoker(另一个Executable).invoke(...)

- rule: J03
  question: Invoker 的 Type 选哪个？
  decision: |
    调"最原始"实现（跳过一切 hook）→ Type.ORIGIN
    走完整 hook 链 → 默认（Chain.FULL），不要显式写
    从链中间开始（跳过高于 maxPriority 的 hook）→ new Invoker.Type.Chain(n)
    hook 的构造器里调 super → invokeSpecial；父类构造器初始化子类实例 → newInstanceSpecial(subClass, args)

- rule: J04
  question: 异常模式选哪个？
  decision: 生产 PROTECTIVE；调试 PASSTHROUGH；显式设置优于依赖 module.prop 的 exceptionMode

- rule: J05
  question: 优先级填什么？
  decision: 默认 PRIORITY_DEFAULT(50)；"比默认早" 用 PRIORITY_DEFAULT+10；永远不用裸数字 0/100/±10000

- rule: J06
  question: 配置存哪里？
  decision: |
    少量键值 → Remote Preferences（模块 App 读写，宿主只读，宿主侧有变更监听）
    大文件/blob → Remote Files（模块 App 读写，宿主只读 openRemoteFile）
    热重载传状态 → setSavedInstanceState（仅 classloader 中立值）
    跨进程其它数据 → 自建 binder/provider（官方无 API）

- rule: J07
  question: 模块不生效，先做什么？
  decision: onModuleLoaded 打 "ALIVE" 日志 → 出现与否二分 S0-S4 与 S5-S9 → 06 册 §A3 决策树；80% 是"进程没重启"或"作用域没勾"

- rule: J08
  question: 目标类加载不出来（加壳/延迟）？
  decision: hook 更早的稳定锚点（Application.attach 等）→ 或 hookClassInitializer（注意类已初始化则永不触发）→ 或 DexKit 延迟查找
```

## 4. 生成代码前 Checklist（逐条打勾）

**结构**
- [ ] 入口类 `public` + `extends XposedModule` + 无参构造器（D02/R02）
- [ ] `java_init.list` 非空（R03）；`module.prop` 两个 ApiVersion 显式（R04）
- [ ] `compileOnly(api)`（D03/R01）；`packaging` 两条成对（D04/R01）
- [ ] Java 17+（sealed interface）；`minSdk >= 26`
- [ ] 混淆启用 → 三条 ProGuard 规则（D05）
- [ ] Manifest 零 xposed meta-data（D06）

**生命周期**
- [ ] 构造器/字段初始化器里无任何 XposedInterface 调用（D01）
- [ ] hook 只在 `onPackageReady` / `onSystemServerStarting`（R05）
- [ ] 每个回调首行过滤包名（R06）；system_server 不用 `isFirstPackage()`（D16）

**Hook**
- [ ] 三参 `Class.forName`（R07）；每个 hook `.setId()`（R08）；`hook()` 后有 `.intercept()`（R08）
- [ ] 保留原逻辑有 `proceed()`；void → `return null`（R09）
- [ ] 无 `getArgs().set()` / 无跨线程 chain（D11）；静态方法无 `proceedWith`（D12）
- [ ] 调原方法 `setType(ORIGIN)`（R11）；未 hook `Constructor.newInstance`（D13）
- [ ] 未 catch `HookFailedError`（D14）

**稳定性**
- [ ] 安装块 try/catch(Throwable) + 日志（R13）；指纹行存在（R14）
- [ ] 异常模式显式（J04）；反射结果缓存（R20）
- [ ] 配置走 Remote 体系（J06/R18）；环境判断走位掩码（R17/D15）

**热重载（如启用）**
- [ ] 恰好一个入口（R19）；`onHotReloading` 返回 true 且完成清理；`onHotReloaded` 做 `replaceHook`
- [ ] `setSavedInstanceState` 无旧 CL 对象

**Lint**
- [ ] 102+ API 有版本守卫或 `minApiVersion=102`（R16）；构建零 `XposedNewApi` ERROR

---



---

## 5. v2.x Changelog 派生规则（【一手·changelog】）

> 以下规则来自 v2.0.2~v2.2.0 官方 changelog 中明确的行为变更，Agent 生成代码时必须遵守。

### D. 硬性规则（违反即模块失效）

#### V01. API 102 与 Legacy API 物理隔离（v2.0.3）

- **约束**：`targetApiVersion ≥ 102` 的模块**禁止** `import de.robv.android.xposed.*`；Legacy 模块（`targetApiVersion ≤ 101`）**禁止** `import io.github.libxposed.*`。
- **检测**：在生成的代码中，若 `targetApiVersion=102`，搜索所有 `import` 语句，确保无 `de.robv.android.xposed` 包引用。
- **修复**：将所有 Legacy API 调用替换为对应的 libxposed API（→ 见 `09 册` 映射表）。

#### V02. x86 架构移除（v2.0.3）

- **约束**：模块 `abiFilters` 中**禁止包含 `x86`**（32 位 x86）。`x86_64` 不受影响。
- **检测**：在 `app/build.gradle.kts` 的 `ndk.abiFilters` 中检查是否包含 `"x86"`。
- **修复**：移除 `"x86"`，保留 `"arm64-v8a"`、`"armeabi-v7a"`、`"x86_64"`。

#### V03. minSdk 底线（v2.1.1）

- **约束**：模块 `minSdk` 应设为 **28**（Android 9），不再支持 API 27 及以下。
- **检测**：在 `app/build.gradle.kts` 的 `defaultConfig.minSdk` 中检查是否 ≥ 28。
- **修复**：将 `minSdk` 设为 28，移除所有 API 27 的降级逻辑（如 `SDK_INT >= 28` 的分支判断）。

#### V04. NSP 迁移截止（v2.3.0）

- **约束**：使用 New XSharedPreferences 的 Legacy 模块必须在 v2.3.0 发布前完成迁移，否则偏好设置读取将失效。
- **检测**：在 `AndroidManifest.xml` 中检查是否存在 `xposedsharedprefs` 元数据。
- **修复**：
  - 方案 A（推荐）：迁移到 libxposed API 102，使用 `RemotePreferences`。
  - 方案 B（临时）：在 `module.prop` 中设置 `xposedminversion=82`，删除 `xposedsharedprefs` 元数据。

### E. 防御性规则（建议遵守，提升健壮性）

#### W01. Hook 链参数类型防御（v2.2.0 修复）

- **约束**：在 `beforeHookedMethod`/`afterHookedMethod` 中，对 `param.args[i]` 增加 `instanceof` 检查后再强制转换。
- **示例**：
```java
Object arg = param.args[0];
if (arg instanceof String) {
    String str = (String) arg;
    // 使用 str
} else {
    // 降级处理或记录警告
}
```

#### W02. findField 异常防御（v2.1.1 修复）

- **约束**：对 `XposedHelpers.findField` 调用统一使用 `try-catch` 包裹，同时捕获 `NoSuchFieldException` 和 `NoSuchFieldError`。
- **示例**：
```java
try {
    Field f = XposedHelpers.findField(clazz, "fieldName");
    f.setAccessible(true);
    // 使用 f
} catch (NoSuchFieldException | NoSuchFieldError e) {
    // 降级处理
}
```

#### W03. 热重载状态隔离（v2.2.0 修复）

- **约束**：在 `onHotReloading` 的 `setSavedInstanceState` 中，仅传递 ClassLoader 无关的对象（如 `Bundle`、`Map<String, Object>` 基础类型值）。禁止传递由旧模块 ClassLoader 加载的类实例。
- **检测**：在生成的热重载代码中，检查 `setSavedInstanceState` 的参数类型，确保无自定义类（除非该类由系统 ClassLoader 加载，如 `Bundle`）。

### F. 环境前提规则（特定功能依赖）

#### P01. HyperOS Runtime 注入前提（v2.2.0）

- **约束**：若模块 Hook 目标为 HyperOS Rust Runtime 应用（native-only、非 ART），必须在模块文档中声明依赖"兼容 Zygisk Next API 的 Zygisk 实现"（ZygiskNext / ReZygisk / NeoZygisk 三选一）。
- **检测**：在生成的模块文档中，若涉及 HyperOS 系统应用 Hook，检查是否包含 ZygiskNext 依赖声明。

#### P02. Android 17 dex2oat 注入前提（v2.2.0）

- **约束**：若模块需要在 Android 17+ 的 dex2oat 包装器进程中 Hook，必须依赖兼容 Zygisk Next API 的实现。
- **检测**：在生成的模块文档中，若涉及 dex2oat 进程 Hook，检查是否包含 ZygiskNext 依赖声明。


## 未验证 / 未获取到

- 本册为规则汇总，个别条目的深层不确定性以被引用分册的「未验证」节为准（`05-pitfalls.md`、`07-compat-matrix.md`、`08-service-and-advanced.md`）。
- `intercept` 是否总在调用方线程：由链模型推断，未见官方明文（`03-api-reference.md` 未验证节）。
- 同优先级多 hook 的 tie-break：未定义（J01/J05 不依赖它）。
