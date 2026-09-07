# 02 · 工程搭建与模块声明文件

> **本册定位**：从零搭一个能被框架识别、能通过 release 混淆的 libxposed API 102 模块工程——依赖、Gradle、`META-INF/xposed/` 声明文件、Manifest、ProGuard。
> **何时读这一册**：新建模块工程、模块装上后"管理器里根本不出现 / 加载失败"、release 构建失效排查时。
> **前置依赖**：无。API 语义 → 见 `03-api-reference.md`；速查表 → 见 `01-quick-reference.md`。
> **交叉引用**：→ 见 `01-quick-reference.md §6-§8`、`03-api-reference.md §1`、`05-pitfalls.md §B.7`、`08-service-and-advanced.md §1`

---

**基准**：`io.github.libxposed:api:102.0.0`（compileOnly）/ `io.github.libxposed:service:102.0.0`（implementation）。本册全部配置取自【一手·源码】`libxposed/api` README、`libxposed/example` 仓库逐字文件与框架侧解析源码（`ConfigFileManager.java`、`ModuleUtil.java`），标注省略时默认为一手。

---

## 1. 工程文件总览（最小完整集）

```
my-xposed-module/
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro                  # 三件套，缺一不可
    └── src/main/
        ├── AndroidManifest.xml             # 零 meta-data
        ├── java/com/example/myhook/ModuleMain.java
        └── resources/META-INF/xposed/      # ← 放 resources，不是 assets
            ├── module.prop
            ├── java_init.list
            ├── scope.list                  # 可选
            └── native_init.list            # 可选（native hook 才需要）
```

⚠️ 四个声明文件放在 `src/main/resources/META-INF/xposed/`，Gradle 会自动打进 APK。放 `assets/` 会被框架判定为 **Legacy 模块**。

---

## 2. Gradle 依赖

### 2.1 依赖坐标与 Gradle 写法【一手·文档】（api/service 两个 README）

| 工件 | 坐标 | Gradle 写法 | 写错的后果 |
|---|---|---|---|
| 模块 API | `io.github.libxposed:api:102.0.0` | `compileOnly(...)` | 写成 `implementation` → API 类被打进 APK → **框架拒绝加载**，日志：`The Xposed API classes are compiled into the module's APK. ... must be fixed by the module developer.`（真实 issue LSPosed#2413） |
| 服务库 | `io.github.libxposed:service:102.0.0` | `implementation(...)` | 这是模块 App 自己用的 Binder 客户端，**必须**打进 APK；写成 compileOnly 则运行时 `NoClassDefFoundError` |
| helper | `io.github.libxposed:helper` | ❌ 禁止引入 | 停在 API 100、2023 停更，与 102 的 Chain 模型不兼容 → 见 `03-api-reference.md §15` |

```kotlin
// app/build.gradle.kts
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")   // 用到 XposedService 才需要
}
```

### 2.2 版本目录 `gradle/libs.versions.toml`（官方示例逐字）

```toml
# gradle/libs.versions.toml
[versions]
agp = "9.2.1"
xposed-api = "102.0.0"
xposed-service = "102.0.0"

[libraries]
libxposed-api = { group = "io.github.libxposed", name = "api", version.ref = "xposed-api" }
libxposed-service = { group = "io.github.libxposed", name = "service", version.ref = "xposed-service" }
```

### 2.3 `settings.gradle.kts`（官方示例逐字，`mavenLocal` 仅本地联调需要）

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // mavenLocal { content { includeGroup("io.github.libxposed") } }  // 仅本地联调未发布版本时启用
    }
    versionCatalogs { create("libs") }
}
rootProject.name = "My Xposed Module"
include(":app")
```

---

## 3. `app/build.gradle.kts` 完整模板（官方示例逐字 + 注释）

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.agp.app)   // 或 id("com.android.application")
}

android {
    namespace = "com.example.myhook"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26          // ⚠️ 与 libxposed:api/service 库自身的 minSdk=26 对齐；低于 26 manifest merger 直接失败
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true       // 官方示例故意开启，验证 ProGuard 三件套
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21   // ≥17 必须；Invoker.Type 是 sealed interface
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"   // ⚠️ 关键：保住四个声明文件不被 AGP 剥离
            excludes += "**"                // ⚠️ 与上一条成对：排除其余 META-INF 资源污染
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
```

**编译环境硬约束**：

| 项 | 值 | 依据 |
|---|---|---|
| `minSdk` | ≥ 26（Android 8.0） | `api`/`service` 库 `build.gradle.kts` 【一手·源码】 |
| Java | ≥ 17（示例用 21） | `Invoker.Type` 为 `sealed interface`，Java 17 以下编译失败 |
| `compileSdk` | 建议 37（service 库以 compileSdk 37 构建） | `service/build.gradle.kts` |

⚠️ `minSdk`（Android SDK 版本，≥26）与 `module.prop` 的 `minApiVersion`（Xposed API 版本，102 时代通常写 101/102）是**完全不同的两个数字**，不要混填。

---

## 4. `META-INF/xposed/` 四个声明文件

### 4.1 `module.prop`（Java Properties 格式）

```properties
# app/src/main/resources/META-INF/xposed/module.prop
minApiVersion=101
targetApiVersion=102
staticScope=true
autoHotReload=true
```

| 字段 | 必需 | 语义 | 依据 |
|---|---|---|---|
| `minApiVersion` | ✅ | 框架 API 版本低于此值则拒绝加载 | wiki + `package-info.java`（"Required properties"） |
| `targetApiVersion` | ✅ | 目标 API 版本。**≥102 触发行为变更：禁止调用 legacy `de.robv.android.xposed` API** | `XposedInterface.API_102` Javadoc |
| `staticScope` | 可选 | `true` = 作用域固定，用户不能把模块应用到清单之外 | `package-info.java` |
| `autoHotReload` | 可选 | App 更新后自动触发热重载；**最终仍以 `onHotReloading()` 返回 `true` 为准** | Javadoc（Module Configuration 节）；官方 wiki 指南字段表未收录 |
| `exceptionMode` | 可选 | `protective`（默认）/ `passthrough`；`ExceptionMode.DEFAULT` 的全局来源 | `package-info.java` |

**框架侧解析的 5 条精确语义**【一手·源码】（`ModuleUtil.java`）：

1. `module.prop` 缺失时默认 `minVersion=100`、`targetVersion=100`、`staticScope=false`。
2. `staticScope` 是**全等字符串比较**：`TextUtils.equals(prop.getProperty("staticScope"), "true")` → 写 `True`/`TRUE`/`1`/`yes` 全部**不生效**。
3. 键存在但值缺失走 null 路径，**不会**回退到 100 → 三个键**全部显式写出**。
4. `autoHotReload` 的框架侧解析代码行未取到（语义来自 `package-info.java` 与示例）。
5. 不存在 `scope` / `require` 字段：作用域在独立文件 `scope.list`；`require` 无任何官方来源。

### 4.2 `java_init.list` —— 入口声明

```
# app/src/main/resources/META-INF/xposed/java_init.list
com.example.myhook.ModuleMain
```

**解析规则**【一手·源码】（`ConfigFileManager.readName()`）：逐行 `trim()` → 跳过空行 → 跳过 `#` 开头注释行 → 其余作为 FQCN 加载。**支持多入口**（每行一个），但热重载要求**恰好一个 Java 入口类** → 见 `08-service-and-advanced.md §4`。

**⭐ Legacy 判定逻辑（最关键的一段框架代码）**【一手·源码】（`ConfigFileManager.loadModule()`）：

```
先读 META-INF/xposed/java_init.list：
  非空 → legacy = false（现代模块），native 入口读 META-INF/xposed/native_init.list
  为空 → legacy = true，回退读 assets/xposed_init + assets/native_init
  两者皆空 → return null（模块加载失败，静默）
```

推论：
- `java_init.list` 存在但**内容全空/全注释** → 被判为 Legacy，`assets/xposed_init` 也没有 → 模块**静默失效**。这是最高频的构建事故之一。
- 现代/Legacy 是**二选一开关**：`java_init.list` 非空时框架**根本不会读** `assets/xposed_init`。想同时兼容新旧框架不能靠同时放两个文件。

**管理器侧判定（与 daemon 侧标准不同）**【一手·源码】：`isLegacyModule()` 看 manifest 是否含 meta-data `xposedminversion`。→ **现代模块 manifest 里禁止写 `xposedminversion`**，否则管理器按 Legacy 通道解析（读 `xposeddescription`/`xposedscope`），与 `module.prop` 体系冲突。

Kotlin 陷阱：清单里写的是 **JVM 类名**。Kotlin 顶层函数所在文件编译出的类名是 `<文件名>Kt`；`class ModuleMainKt` 无论写在哪个文件，类名都是 `ModuleMainKt`。

### 4.3 `scope.list` —— 推荐作用域

```
# app/src/main/resources/META-INF/xposed/scope.list
com.android.settings
```

⚠️ **解析行为与 `java_init.list` 不同**【一手·源码】（`ModuleUtil.java`：`reader.lines().collect(toList())`）：**不 trim、不过滤空行、不支持 `#` 注释**。写文件时：不留空行、不加注释、行尾不带空格。
缺失 → 空作用域（不报错）。语义是"初始建议值"而非硬限制 → 见 `05-pitfalls.md §B.12`。

### 4.4 `native_init.list` —— Native 入口（可选）

```
# app/src/main/resources/META-INF/xposed/native_init.list
libmyhook.so
```

每行一个 so 库名。配套 `System.loadLibrary("myhook")` 必须手动调用 → 见 `08-service-and-advanced.md §6`。

---

## 5. AndroidManifest.xml

```xml
<!-- app/src/main/AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application
        android:name=".App"
        android:label="My Hook"                          <!-- ← 模块名 -->
        android:description="@string/module_desc"        <!-- ← 模块描述 -->
        tools:ignore="MissingApplicationIcon">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

【一手·文档】官方 Wiki："Modern API does not use metadata anymore as well. Module name uses the `android:label` resource; module description uses the `android:description` resource."

| meta-data 键 | 状态 | 说明 |
|---|---|---|
| `xposedmodule` / `xposedminversion` / `xposeddescription` / `xposedscope` / `xposedsharedprefs` | ❌ 全部**禁止写** | Legacy 残留；`xposedminversion` 会触发管理器 Legacy 判定（见 §4.2） |
| `android:label` | ✅ 模块名来源 | |
| `android:description` | ✅ 模块描述来源 | |

**⭐ `system` / `android` 语义陷阱**【一手·源码】：Legacy 模块声明的作用域中 `android` 与 `system` **语义相反**，框架在 `getScopeList()` 里自动互换；**现代模块走 `scope.list`，不做互换**——`system` 就是 `system`（系统框架），`android` 就是 `android`（系统对话框等进程）。`system_server` 的包名显示为 `system`。

---

## 6. ProGuard / R8 三件套（缺一不可）

```
# app/proguard-rules.pro
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

【一手·文档】`libxposed/api` README 原文："These rules keep module entry classes from being removed and rewrite `META-INF/xposed/java_init.list` when entry classes are obfuscated."

| 规则 | 作用 | 缺失后果 |
|---|---|---|
| `-dontwarn io.github.libxposed.annotation.**` | 注解包是 `compileOnly`，运行时不存在，压掉 R8 missing-class 警告 | release 构建警告/失败 |
| `-adaptresourcefilecontents META-INF/xposed/java_init.list` | 入口类被 R8 改名后**同步重写**清单里的类名 | **经典症状：debug 正常、release 完全失效**（debug 不混淆，名字对得上；release 对不上） |
| `-keep,...extends XposedModule { public <init>(); }` | 保住入口类与**公开无参构造器**（框架反射 `newInstance()` 用）；`allowobfuscation` 允许改名（靠上一条兜底） | 入口类被 shrink → 静默失效；构造器丢失 → `NoSuchMethodException` |

配套机制【一手·源码】：混淆启用时框架用 `ObfuscationManager.getSignatures()` 做**前缀替换**还原类名，与 `-adaptresourcefilecontents` 配对工作。

---

## 7. 构建失败 / 不加载对照速查

| 症状 | 根因 | 修复 |
|---|---|---|
| 管理器里模块显示为 Legacy / 图标异常 | manifest 写了 `xposedminversion` | 删除全部 xposed meta-data |
| 模块加载日志报 API classes compiled into APK | `api` 写成 `implementation` | 改回 `compileOnly` |
| debug 正常，release 完全失效 | 缺 `-adaptresourcefilecontents` | 补三件套 |
| release 后任何进程都不加载 | 入口类被 shrink / 构造器非 public | 补 `-keep ... { public <init>(); }` |
| `META-INF/xposed/module.prop` 不在 APK 里 | 缺 `packaging { merges += ... }` | 补 packaging 块（两条成对） |
| manifest merger 报 minSdkVersion 冲突 | 模块 `minSdk < 26` | 提到 26+ |
| 编译错 `sealed interface` | Java < 17 | 升 17+ |
| 模块被判 Legacy | `java_init.list` 为空/全注释，或路径放错到 assets | 填入 FQCN，放 `src/main/resources/META-INF/xposed/` |
| `staticScope` 不生效 | 写成 `True`/`1` | 只写小写 `true` |

---

## 未验证 / 未获取到

- `module.prop` 的 `autoHotReload` 与 `exceptionMode` 的**框架侧解析代码行**未取到（语义来自 `package-info.java` 与官方示例）。
- `scope.list` 是否会导致作用域外回调被过滤：源码确证它作为初始建议值被读入，与 Javadoc「回调可超出 scope」并存，两者优先级关系无一手说明。
- 现代 API 模块的 **debug 构建是否也必须带三件套**：官方示例 release 混淆是实测背书，debug 不混淆时清单不会被改写，理论上可不加；未找到官方明文。
- `libxposed:api` 自身 `build.gradle.kts` 中 annotation 以 `compileOnly` 引入的确切源码行未逐字复核（结论与 `-dontwarn` 规则的官方解释一致）。
