#!/usr/bin/env bash
# CI-build-check.sh — 对构建出的模块 APK 做静态自检（PC 端，无需设备）。
# 用法：bash CI-build-check.sh <path-to-apk>
# 依据：06-debugging-runbook.md §A3(S1)、02-project-scaffold.md。
set -euo pipefail

APK="${1:?usage: CI-build-check.sh <apk>}"
FAIL=0

say()  { printf '%s %s\n' "$1" "$2"; }
die()  { say "❌" "$2"; FAIL=1; }
ok()   { say "✅" "$2"; }

command -v unzip >/dev/null || { echo "unzip is required"; exit 2; }

# 1) 声明文件进了 APK
unzip -l "$APK" | grep -q "META-INF/xposed/module.prop" \
  && ok "module.prop in APK" || die "MISSING" "META-INF/xposed/module.prop not in APK (check packaging merges)"

unzip -l "$APK" | grep -q "META-INF/xposed/java_init.list" \
  && ok "java_init.list in APK" || die "MISSING" "META-INF/xposed/java_init.list not in APK"

# 2) java_init.list 非空且无 assets/xposed_init（现代模块判定：非空才走现代通道）
INIT=$(unzip -p "$APK" META-INF/xposed/java_init.list 2>/dev/null | grep -v '^\s*#' | grep -v '^\s*$' | tr -d ' \r' | grep -v '^$' || true)
[ -n "$INIT" ] && ok "java_init.list non-empty: $(echo "$INIT" | head -n1)" \
  || die "EMPTY" "java_init.list empty => judged as Legacy => silent failure"

if unzip -l "$APK" | grep -q "assets/xposed_init"; then
  say "⚠️" "assets/xposed_init also present (ignored by framework when java_init.list non-empty)"
fi

# 3) module.prop 必需键
PROP=$(unzip -p "$APK" META-INF/xposed/module.prop 2>/dev/null || true)
echo "$PROP" | grep -q "^minApiVersion="    && ok "minApiVersion present"    || die "MISSING" "module.prop: minApiVersion"
echo "$PROP" | grep -q "^targetApiVersion=" && ok "targetApiVersion present" || die "MISSING" "module.prop: targetApiVersion"
if echo "$PROP" | grep -q "^staticScope="; then
  echo "$PROP" | grep -q "^staticScope=true$" \
    && ok "staticScope lowercase true" || die "VALUE" "staticScope must be exactly lowercase 'true'"
fi

# 4) scope.list 行卫生（不 trim、不支持注释 → 不允许空行/行尾空格/注释）
if unzip -l "$APK" | grep -q "META-INF/xposed/scope.list"; then
  BAD=$(unzip -p "$APK" META-INF/xposed/scope.list | grep -nE '^\s*$|#|\s$' || true)
  [ -z "$BAD" ] && ok "scope.list clean" || die "FORMAT" "scope.list has empty lines/comments/trailing spaces (parser does NOT trim)"
fi

# 5) 禁止 manifest 里出现 legacy xposed meta-data（会触发管理器 Legacy 判定）
if command -v aapt2 >/dev/null 2>&1; then
  if aapt2 dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null | grep -q "xposedminversion\|xposeddescription\|xposedscope"; then
    die "LEGACY" "manifest contains legacy xposed meta-data"
  else
    ok "no legacy meta-data"
  fi
else
  say "⚠️" "aapt2 not found; skip manifest meta-data check"
fi

# 6) API 类不该打进 APK（compileOnly 检查的近似：查 io/github/libxposed/api 目录）
if unzip -l "$APK" | grep -q "io/github/libxposed/api/"; then
  die "PACKAGING" "io.github.libxposed.api classes found inside APK (api must be compileOnly)"
else
  ok "api not bundled (compileOnly)"
fi

[ "$FAIL" -eq 0 ] && { ok "ALL CHECKS PASSED"; exit 0; } || exit 1
