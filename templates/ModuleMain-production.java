// app/src/main/java/{{PACKAGE_PATH}}/{{MODULE_CLASS}}.java
// 生产级模块骨架：过滤 / 统一注册 / 热重载替换 / 远程配置只读 / 指纹日志。
// 占位符：{{PACKAGE_NAME}} {{MODULE_CLASS}} {{TARGET_PACKAGE}}
package {{PACKAGE_NAME}};

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class {{MODULE_CLASS}} extends XposedModule {

    private static final String TAG = "{{MODULE_CLASS}}";
    private static final String TARGET_PACKAGE = "{{TARGET_PACKAGE}}";

    /** hook id -> 该 id 的 Hooker。热重载时按 id 原子替换。 */
    private final Map<String, XposedInterface.Hooker> mHookers = new HashMap<>();

    // ---------- 生命周期 ----------

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        // 指纹行：所有排障的起点（框架/版本/API/能力位/进程名）。此处无 ClassLoader，禁止 hook。
        log(Log.INFO, TAG, String.format(java.util.Locale.ROOT,
                "loaded proc=%s sysServer=%b | fw=%s ver=%s code=%d api=%d props=0x%x",
                param.getProcessName(), param.isSystemServer(),
                getFrameworkName(), getFrameworkVersion(), getFrameworkVersionCode(),
                getApiVersion(), getFrameworkProperties()));
    }

    @Override
    public void onSystemServerStarting(@NonNull XposedModuleInterface.SystemServerStartingParam param) {
        // system_server 专用入口；isFirstPackage() 在 system_server 恒为 false，不要用
        if ((getFrameworkProperties() & PROP_CAP_SYSTEM) == 0) return;   // 能力探测
        installHooks(param.getClassLoader(), "system");
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;
        installHooks(param.getClassLoader(), "app");
    }

    // ---------- Hook 安装 ----------

    private void installHooks(@NonNull ClassLoader cl, @NonNull String scopeTag) {
        try {
            // TODO 替换目标类/方法；如目标方法可能被内联，对【调用方】deoptimize（不是目标方法）
            Class<?> c = Class.forName("{{TARGET_PACKAGE}}.SomeTarget", true, cl);
            Method m = c.getDeclaredMethod("someMethod", String.class);

            register(scopeTag + ":someMethod", m, chain -> {
                String arg0 = (String) chain.getArg(0);                 // getArgs() 是不可变 List
                Object result = chain.proceed(new Object[]{"replaced"}); // 改参数 = 传新数组
                return "wrapped:" + result;                              // 改返回值；void 方法 return null
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "installHooks failed", t);
        }
    }

    /** 统一注册：始终 setId（原子替换）+ 显式优先级 + 显式异常模式。 */
    private void register(@NonNull String id, @NonNull Executable exec,
                          @NonNull XposedInterface.Hooker hooker) {
        mHookers.put(id, hooker);
        hook(exec)
                .setId(id)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)  // 开发期可临时 PASSTHROUGH
                .intercept(hooker);
    }

    // ---------- 热重载（API 102） ----------

    @Override
    public boolean onHotReloading(@NonNull XposedModuleInterface.HotReloadingParam param) {
        // 返回 true 前必须：停模块自有 Java/native 线程、注销 native hook 与外部回调、
        // 释放指向模块 classloader 对象的 JNI 全局引用、清系统/App 类持有的模块引用。
        // 默认实现返回 false = 拒绝一切热重载。
        param.setSavedInstanceState(null);   // ⚠️ 只能放 classloader 中立值（primitive/String/数组/框架 Bundle）
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        // 生命周期回调不会重放；默认实现只 unhook 全部旧 hook → 不重写 = 热重载后功能全消失
        for (XposedInterface.HookHandle old : param.getOldHookHandles()) {
            String id = old.getId();
            XposedInterface.Hooker fresh = id == null ? null : mHookers.get(id);
            try {
                if (fresh != null) {
                    old.replaceHook(fresh);   // 保留 executable/priority/exceptionMode/id；成功后 old 失效
                } else {
                    old.unhook();
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "replace hook failed: " + id, t);
                old.unhook();
            }
        }
    }

    // ---------- 远程配置（宿主进程内只读） ----------

    @Nullable
    private String readConfig(@NonNull String group, @NonNull String key) {
        if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) return null;   // embedded 框架无远程能力
        try {
            return getRemotePreferences(group).getString(key, null);   // ⚠️ 宿主侧只读；写配置在模块 App 侧
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }
}
