// app/src/main/java/{{PACKAGE_PATH}}/{{MODULE_CLASS}}.java
// 最小可运行模块骨架。占位符：{{PACKAGE_NAME}} {{MODULE_CLASS}} {{TARGET_PACKAGE}}
package {{PACKAGE_NAME}};

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class {{MODULE_CLASS}} extends XposedModule {

    private static final String TAG = "{{MODULE_CLASS}}";
    private static final String TARGET_PACKAGE = "{{TARGET_PACKAGE}}";
    private static final String TARGET_CLASS = "{{TARGET_PACKAGE}}.SomeTarget";   // TODO 替换
    private static final String TARGET_METHOD = "someMethod";                     // TODO 替换

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        // ① 包名过滤（回调会超出 scope 范围，必须自己过滤）
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        // ② 同进程多包时只装一次
        if (!param.isFirstPackage()) return;

        try {
            // ③ 必须三参 Class.forName（两参用模块自己的 ClassLoader，必然失败）
            Class<?> target = Class.forName(TARGET_CLASS, true, param.getClassLoader());
            Method m = target.getDeclaredMethod(TARGET_METHOD);

            // ④ hook(Executable) -> HookBuilder -> .intercept(Hooker) -> HookHandle
            hook(m)
                    .setId(TARGET_CLASS + ":" + TARGET_METHOD)               // 重复注册自动原子替换
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)           // = 50
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();   // 不调 proceed() = 原方法不执行
                        log(Log.DEBUG, TAG, "hooked, result=" + result);
                        return result;                     // void 方法此处 return null
                    });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install hook failed", t);
        }
    }
}
