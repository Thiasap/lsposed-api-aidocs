// app/build.gradle.kts
// 基于 libxposed/example 官方 app/build.gradle.kts（API 102）。占位符：{{PACKAGE_NAME}}
plugins {
    id("com.android.application")
}

android {
    namespace = "{{PACKAGE_NAME}}"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26            // ⚠️ libxposed:api/service 库自身 minSdk=26，低于此 manifest merger 失败
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // 官方示例开启混淆；必须配合 proguard-rules.pro 三件套
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17   // Invoker.Type 是 sealed interface，最低 17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"   // ⚠️ 保住 module.prop/java_init.list/scope.list
            excludes += "**"                // ⚠️ 与上一条成对出现
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    // ⚠️ 必须 compileOnly：写成 implementation 会被框架拒绝加载
    compileOnly("io.github.libxposed:api:102.0.0")
    // 模块 App 与框架通信的 Binder 客户端，必须打进 APK（不用 XposedService 可删除此行）
    implementation("io.github.libxposed:service:102.0.0")
}
