# app/proguard-rules.pro
# libxposed API 102 三件套（来源：libxposed/api README 逐字）。缺一不可：
# 1) annotation 包是 compileOnly，运行时不存在，压掉 missing-class 警告
-dontwarn io.github.libxposed.annotation.**
# 2) 入口类被 R8 改名后同步重写清单内容（缺失 = debug 正常、release 完全失效）
-adaptresourcefilecontents META-INF/xposed/java_init.list
# 3) 保住入口类与其公开无参构造器（框架反射实例化用）；允许改名由第 2 条兜底
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
