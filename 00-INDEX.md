# 00 · 索引 — LSPosed API 102 知识库（面向 AI Agent）

> **基准**：`io.github.libxposed:api:102.0.0` / `io.github.libxposed:service:102.0.0`（2026-06 发布线）。
> **读者**：开发 Xposed/LSPosed 插件模块的 AI Agent。
> **四条全局基准**：① 只写 API 102（旧 API 仅在防误导处点名，完整对照见 09 册）；② 不绑定任何框架发行版——环境判断一律走运行时探测（`getApiVersion()` + `getFrameworkProperties()` 位掩码），禁止比较框架版本字符串；③ **v2.x 官方 changelog（lsposed.cn 发布线 + Vector v2.x release）作为一手事实源**，与 libxposed API/service 源码同等地位；④ 每条结论可溯源（【一手·源码】/【一手·文档】/【一手·changelog】/【二手】/【推断】五级标注，各分册末尾有「未验证」节）。

---

## 1. 分册路由表

| 分册 | 行数 | 定位 | 何时加载 |
|---|---|---|---|
| `00-INDEX.md`（本册） | 91 | 入口路由 | 总是 |
| `01-quick-reference.md` | 250 | 10 张速查表：依赖/方法/常量/生命周期/声明文件/反向索引 | 写代码时手边常驻；查签名 |
| `02-project-scaffold.md` | 308 | 工程搭建：Gradle、`META-INF/xposed/` 四文件、Manifest、ProGuard 三件套、构建失败对照 | 新建工程；模块不被识别；release 失效 |
| `03-api-reference.md` | 1268 | 完整 API 参考：类型总览、Chain 心智模型、Invoker、deoptimize、已移除 API 清单 | 需要某个 API 的精确语义/Javadoc 依据 |
| `04-lifecycle-and-patterns.md` | 419 | 生命周期流程图、各回调操作边界、热重载完整语义（PR#62）、最小/生产级骨架、Kotlin 差异 | 写入口类前；决定逻辑放哪个回调 |
| `05-pitfalls.md` | 235 | **95+ 条坑点**（20 组，含 v2.x 框架级陷阱）+ 症状→坑号快查索引 | 行为异常时检索；代码写完自查 |
| `06-debugging-runbook.md` | 310 | 日志与指纹行、hook 验证、**S0-S9 排障决策树**、adb 速查、热重载验证清单 | 模块"不生效"类一切场景 |
| `07-compat-matrix.md` | 233 | Root/Zygisk/免 Root/Android 版本/ABI 兼容事实 + 能力探测模板 | 评估目标设备；写环境分支 |
| `08-service-and-advanced.md` | 1138 | XposedService 15 方法、Binder 推送机制、热重载（API 102）、Native Hook | 进阶主题：作用域申请/远程配置/热重载/native |
| `09-legacy-migration.md` | 198 | Legacy(`de.robv`) → Modern 逐项映射 + 12 步迁移（唯一完整介绍旧 API 的分册） | 迁移旧模块；读懂网上 Legacy 教程 |
| `10-agent-rules.md` | 396 | 机器可读 DO/DON'T 规则（D01-D22 / R01-R22 / 判定规则 J01-J08）+ 生成前 checklist | **生成代码前第一个加载** |
| `templates/` | 10 文件 | 可编译模块骨架（占位符 `{{PACKAGE_NAME}}`/`{{MODULE_CLASS}}`） | 从零生成工程 |

## 2. 按场景的加载顺序

1. **从零写一个新模块**
   `10-agent-rules.md`（规则）→ `04-lifecycle-and-patterns.md §5`（骨架）→ `02-project-scaffold.md`（工程与声明文件）→ `templates/`（复制模板）→ 完成后 `10-agent-rules.md §4` 自查。

2. **给现有模块加 hook**
   `01-quick-reference.md §2-§3`（hook/Chain 签名）→ `03-api-reference.md §4`（链语义）→ `05-pitfalls.md §B.3-§B.6`。

3. **模块装上没反应 / hook 不触发**
   `06-debugging-runbook.md`（S0-S9 决策树，先打 ALIVE 信号）→ `05-pitfalls.md` 快查索引 → 根因定位后跳对应分册。

4. **debug 正常、release 失效**
   `02-project-scaffold.md §6`（ProGuard 三件套）→ `05-pitfalls.md §B.7`。

5. **作用域申请 / 远程配置 / 模块 App 与宿主通信**
   `08-service-and-advanced.md §1-§3`（XposedService/Helper/Manifest）→ `05-pitfalls.md §B.11`（只读约束）。

6. **热重载**
   `08-service-and-advanced.md §4` → `04-lifecycle-and-patterns.md §5.2`（onHotReloaded 模式）→ `06-debugging-runbook.md §A5`（验证清单）。

7. **native hook**
   `08-service-and-advanced.md §5`（`native_init.list` + `hookFunc` ABI；libxposed API 契约内无 native）。

8. **迁移 Legacy 模块**
   `09-legacy-migration.md`（映射表 + 12 步）→ `02-project-scaffold.md` → `05-pitfalls.md #54`。

9. **判断目标设备能不能跑**
   `07-compat-matrix.md`（矩阵 + 能力探测模板）。

## 3. 最高频的 10 个事实（一句话版，详情见分册）

1. 依赖必须 `compileOnly`（api）+ `implementation`（service）；写错 implementation 会被框架拒绝加载 → `02 册 §2`。
2. `hook()` 返回 **HookBuilder** 不是 Handle，必须再 `.intercept(hooker)` → `03 册 §4`。
3. Chain 模型：`proceed()` 才执行原方法；`getArgs()` 不可变；Chain 不可跨线程/复用 → `03 册 §4`。
4. `getInvoker()` 默认 `Chain.FULL`——调原方法必须 `.setType(Invoker.Type.ORIGIN)` → `03 册 §5`。
5. `deoptimize()` 去优化的是**调用方**，不是目标方法 → `03 册 §6`。
6. 资源 Hook 已被官方移除，无任何替代 API → `03 册 §9`。
7. 主 hook 安装点是 `onPackageReady`（有真实 `getClassLoader()`）；`onPackageLoaded` 只有 `getDefaultClassLoader()` 且 Android <29 不触发 → `04 册 §1`。
8. Remote Preferences 宿主进程内**只读**；写入只能在模块 App 侧经 XposedService → `08 册 §1`。
9. 热重载三前提：恰好一个入口类 + `onHotReloading` 返回 true + `onHotReloaded` 里 `replaceHook`（默认实现只 unhook）→ `08 册 §4`。
10. libxposed API 102 契约内**没有** native hook；`native_init.list` + `native_init` 入口是框架私有 ABI（Vector 下为 Dobby）→ `08 册 §5`。

## 4. 版本环境说明（影响事实判读）

- `LSPosed/LSPosed` 官方 GitHub 停在 v1.9.2（2023-10）；**GitHub 上不存在任何 v2.x release**。
- 活跃的 API 102 框架线：`JingMatrix/Vector` v2.2（API 102，Android 9~17）。
- **镜像线（lsposed.cn 发布线）**：v2.0.2~v2.2.0，由原 LSPosed 团队核心成员维护的平行发布渠道。其 changelog 为【一手·changelog】事实源，涵盖 HyperOS 注入、A17 dex2oat 注入、New XSharedPreferences 废弃计划等 Vector release body 未详述的特性。
- **API 102 强制隔离**（v2.0.3 起）：`targetApiVersion >= 102` 的模块禁止调用 Legacy API；Legacy 模块禁止调用 libxposed API。违反即加载失败。
- 本知识库硬约束来自：① libxposed API/service/example 源码；② libxposed GitHub PR/commit 记录；③ 镜像线官方 changelog；④ Vector release body。

## 5. 知识库整体未决事项（跨分册汇总）

- LSPatch/embedded 对 API 102 与 XposedService 的实际支持（仓库 404/停更）→ 仅【推断】降级策略（`08 册`未验证节）。
- `module.prop` 的 `autoHotReload`/`exceptionMode` 框架侧解析源码行；`scope.list` 过滤语义与 Javadoc 的优先级关系。
- 同优先级 hook 的 tie-break、每方法回调数量上限的具体数值、`log()` 是否进 logcat。
- `libxposed/helper` 官方废弃声明原文（README 404，api/service 的 Related Projects 仍链接它——文档滞后）。
- dokka 站点（`libxposed.github.io`）不可达；全部 API 事实取自 GitHub 源码原文（可信度更高）。

**已解决（v2.x changelog 转正）**：
- HyperOS Runtime 注入、A17 dex2oat 包装器注入 → 确认为 v2.2.0 一手事实，强依赖兼容 Zygisk Next API 的实现（→ 见 `07 册 §1.4`）。
- New XSharedPreferences 废弃时间表 → 确认为 v2.3.0 正式移除（→ 见 `09 册 §3`）。
- 32 位 x86 架构移除 → v2.0.3 一手事实（→ 见 `07 册 §1.5`）。
- 最低支持版本 → v2.1.1 起为 Android 9 (API 28)（→ 见 `07 册 §1.4`）。
- API 102 与 Legacy API 的物理隔离 → v2.0.3 一手事实（→ 见 `10 册` 规则）。

> 生效日期：2026-08-31。由工程保障团队基于 5 份一手采集稿（7193 行）汇编；关键决策请由人类开发者复核。
