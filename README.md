# LSPosed API Knowledge Base

> **For AI agents:** Read `00-INDEX.md` first. It contains the full loading protocol and cross-reference index.

This is a structured knowledge base for LSPosed modern API (libxposed, `targetApiVersion ≥ 102`) module development. It is designed to serve as a reference corpus for AI-assisted Xposed module development workflows.

## Structure

| File | Purpose |
|---|---|
| `00-INDEX.md` | **Start here.** Loading protocol, file inventory, and cross-reference map. |
| `01-quick-reference.md` | One-page cheat sheet: entry points, core APIs, and common patterns. |
| `02-project-scaffold.md` | Gradle setup, Manifest metadata, and project structure. |
| `03-api-reference.md` | Full API reference: `XposedInterface`, Chain model, `Invoker`, `deoptimize`, removed-API list. |
| `04-lifecycle-and-patterns.md` | Module lifecycle callbacks, hot-reload state management, and design patterns. |
| `05-pitfalls.md` | Known traps, framework-level bugs, and version-specific gotchas. |
| `06-debugging-runbook.md` | Debugging workflow: logs, error codes, and troubleshooting steps. |
| `07-compat-matrix.md` | Framework version vs Android version compatibility, runtime injection prerequisites. |
| `08-service-and-advanced.md` | `XposedService`, IPC, resource hooks, and native hooks. |
| `09-legacy-migration.md` | Migrating from old `XposedBridge` API to libxposed. |
| `10-agent-rules.md` | Hard rules for AI agents: constraints, forbidden patterns, and best practices. |
| `templates/` | Code templates for common module skeletons. |

## Version Basis

This knowledge base is aligned with:
- **LSPosed framework**: v2.2.0 — official changelog (v2.0.2~v2.2.0) is a first-party fact source. GitHub Releases still lag at v1.9.2 and do **not** reflect current framework capability.
- **libxposed API**: v102 (latest stable with hot-reload support)
- **Android**: API 28 (Android 9) minimum, up to Android 17

## Licensing

Content is derived from LSPosed official documentation, GitHub repositories (libxposed/api, libxposed/service, LSPosed/LSPosed), and official release notes. See individual files for source annotations.

---

# LSPosed API 知识库

> **AI agent 请注意：** 请先阅读 `00-INDEX.md`。该文件包含完整的加载协议和交叉引用索引。

这是一个面向 LSPosed 现代 API（libxposed，`targetApiVersion ≥ 102`）模块开发的结构化知识库。专为 AI 辅助 Xposed 模块开发工作流设计，作为参考语料库使用。

## 文件结构

| 文件 | 用途 |
|---|---|
| `00-INDEX.md` | **从这里开始。** 加载协议、文件清单和交叉引用地图。 |
| `01-quick-reference.md` | 单页速查表：入口点、核心 API 和常见模式。 |
| `02-project-scaffold.md` | Gradle 配置、Manifest 元数据和项目结构。 |
| `03-api-reference.md` | 完整 API 参考：`XposedHelpers`、`XposedBridge`、`MethodHookParam` 等。 |
| `04-lifecycle-and-patterns.md` | 模块生命周期回调、热重载状态管理和设计模式。 |
| `05-pitfalls.md` | 已知陷阱、框架级 bug 和版本特定的注意事项。 |
| `06-debugging-runbook.md` | 调试工作流：日志、错误码和故障排查步骤。 |
| `07-compat-matrix.md` | 框架版本与 Android 版本兼容性、运行时注入前提条件。 |
| `08-service-and-advanced.md` | `XposedService`、IPC、资源 Hook 和原生 Hook。 |
| `09-legacy-migration.md` | 从旧版 `XposedBridge` API 迁移到 libxposed。 |
| `10-agent-rules.md` | AI agent 硬性规则：约束、禁止模式和最佳实践。 |
| `templates/` | 常见模块骨架的代码模板。 |

## 版本基准

本知识库对齐以下版本：
- **LSPosed 框架**：v2.2.0（官方 changelog v2.0.2~v2.2.0 为一手事实源；GitHub Releases 仍停在 v1.9.2，不代表当前能力）
- **libxposed API**：v102（最新稳定版，支持热重载）
- **Android**：最低 API 28（Android 9），最高至 Android 17

## 来源说明

内容源自 LSPosed 官方文档、GitHub 仓库（libxposed/api、libxposed/service、LSPosed/LSPosed）和官方发布日志。各文件内均有详细的来源标注。

## 维护建议

当 LSPosed 发布新版本或 libxposed/api 有重大变更时，只需：

```bash
cd /mnt/wslShare/lsposed-api-aidocs
# 修改相应分册文件
git add -A
git commit -m "chore: sync vX.Y.Z changelog"
git push
```

无需修改 README，因为它不依赖具体版本细节，只负责导航。
