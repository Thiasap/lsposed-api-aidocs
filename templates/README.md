# templates/ — libxposed API 102 模块骨架模板

> 用途：Agent 生成新模块工程时，把本目录文件复制到对应路径并替换占位符。全部内容基于【一手·源码】`libxposed/example` 与 `libxposed/api` README。

## 占位符约定

| 占位符 | 含义 | 示例 |
|---|---|---|
| `{{PACKAGE_NAME}}` | 模块 applicationId / Java 包名 | `com.example.myhook` |
| `{{MODULE_CLASS}}` | 入口类简名（不带包名） | `ModuleMain` |
| `{{MODULE_NAME}}` | 管理器里显示的模块名 | `My Hook` |
| `{{MODULE_DESC}}` | 模块描述 | `A demo module` |
| `{{TARGET_PACKAGE}}` | 默认作用域目标包名 | `com.android.settings` |

## 文件 → 目标路径对照

| 模板文件 | 复制到 | 必需 |
|---|---|---|
| `build.gradle.kts` | `app/build.gradle.kts` | ✅ |
| `AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` | ✅ |
| `ModuleMain-minimal.java` 或 `ModuleMain-production.java` | `app/src/main/java/{{PACKAGE_NAME路径}}/{{MODULE_CLASS}}.java` | ✅ 二选一 |
| `module.prop` | `app/src/main/resources/META-INF/xposed/module.prop` | ✅ |
| `java_init.list` | `app/src/main/resources/META-INF/xposed/java_init.list` | ✅ |
| `scope.list` | `app/src/main/resources/META-INF/xposed/scope.list` | 建议 |
| `proguard-rules.pro` | `app/proguard-rules.pro` | ✅（release 必需） |
| `CI-build-check.sh` | 仓库根或 `.github/scripts/` | 可选 |

另需（无模板、直接照抄）：`gradle/libs.versions.toml` 与 `settings.gradle.kts` → 见 `../02-project-scaffold.md §2`。

## 生成后必查

1. `java_init.list` 里的 FQCN 与实际入口类一致（`{{PACKAGE_NAME}}.{{MODULE_CLASS}}`）。
2. `module.prop`：`minApiVersion`/`targetApiVersion` 显式；用到 102-only API（setId/热重载）则 `minApiVersion=102`。
3. `scope.list`：一行一个包名，**无空行、无注释、行尾无空格**。
4. release 构建一次并在真机验证（ProGuard 三件套只在 release 暴露问题）。
5. `CI-build-check.sh` 里的 APK 自检逻辑可在本地直接跑：`bash CI-build-check.sh app/build/outputs/apk/release/app-release.apk`。
