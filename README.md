# LSPosed API Knowledge Base

> **For AI agents:** Read `00-INDEX.md` first. It contains the full loading protocol and cross-reference index.

This is a structured knowledge base for LSPosed modern API (libxposed, `targetApiVersion ≥ 102`) module development. It is designed to serve as a reference corpus for AI-assisted Xposed module development workflows.

## Structure

| File | Purpose |
|---|---|
| `00-INDEX.md` | **Start here.** Loading protocol, file inventory, and cross-reference map. |
| `01-quick-reference.md` | One-page cheat sheet: entry points, core APIs, and common patterns. |
| `02-project-scaffold.md` | Gradle setup, Manifest metadata, and project structure. |
| `03-api-reference.md` | Full API reference: `XposedHelpers`, `XposedBridge`, `MethodHookParam`, etc. |
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
- **LSPosed framework**: v2.2.0 (including all v2.0.x/v2.1.x changelog facts as first-party sources)
- **libxposed API**: v102 (latest stable with hot-reload support)
- **Android**: API 28 (Android 9) minimum, up to Android 17

## Licensing

Content is derived from LSPosed official documentation, GitHub repositories (libxposed/api, libxposed/service, LSPosed/LSPosed), and official release notes. See individual files for source annotations.
