import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// 必须显式 import：在 Kotlin DSL 脚本里写 `java.util.zip.ZipFile` 会被解析成
// Gradle 的 `java` 扩展（JavaPluginExtension），而不是 Java 的包名，
// 报错是「Unresolved reference: util」。
import java.util.Properties
import java.util.zip.ZipFile

// ── release 签名 ───────────────────────────────────────────────────────
// 凭据放在仓库根目录的 keystore.properties（已 gitignore），keystore 文件本身放在
// **仓库之外**（..\..\keystore\），所以两者都不会被提交。
//
// 没这个文件时（别人 clone 下来自己构建）自动回退到 debug 签名：
// 这样 assembleRelease 不会因为缺凭据直接失败，仓库里也不必放任何密钥。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")?.let { file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pjsk.toolbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pjsk.toolbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有正式密钥就用它；没有就退回 debug 签名（并会在构建时打印提醒）
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("⚠️ 未找到 keystore.properties，release 包将使用 debug 签名（不能覆盖安装正式签名的包）")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room 导出 schema（便于日后写迁移）。
// 注意：ksp { } 是项目级扩展，必须放在 android { } 之外，
// 放进 android.defaultConfig { } 会导致构建脚本编译失败。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 把「编译期 classpath」落盘，供 `tools/compile-check.ps1` 使用。
 *
 * 为什么不让离线检查脚本自己去扫 Gradle 缓存：缓存里同一个构件可能存着**多个版本**
 * （本机其它工程留下的），扫出来的 classpath 会把旧版本排在前面，于是出现
 * 「旧版 Compose 遮蔽新版 Compose」这种**假失败**，甚至可能**假通过**（更危险）。
 * 让 Gradle 交出它真正解析出来的那一份，才是唯一可靠的做法。
 *
 * AAR 里的类在 `classes.jar` 中，所以顺便把每个 AAR 的 classes.jar 解出来。
 *
 * 用法：gradle :app:writeCompileClasspath
 */
tasks.register("writeCompileClasspath") {
    // 需要在执行期解析依赖，不能进配置缓存
    notCompatibleWithConfigurationCache("需要在执行期解析依赖")
    val classpath = configurations.named("debugCompileClasspath")
    val outFile = layout.buildDirectory.file("compile-classpath.txt")
    val aarDir = layout.buildDirectory.dir("aar-classes")
    doLast {
        val dir = aarDir.get().asFile.apply { mkdirs() }
        val entries = classpath.get().files.mapNotNull { f ->
            when (f.extension) {
                "aar" -> {
                    val target = File(dir, "${f.nameWithoutExtension}-classes.jar")
                    if (!target.exists() || target.length() == 0L) {
                        ZipFile(f).use { zip ->
                            val entry = zip.getEntry("classes.jar")
                            if (entry != null) {
                                zip.getInputStream(entry).use { input ->
                                    target.outputStream().use { input.copyTo(it) }
                                }
                            }
                        }
                    }
                    target.takeIf { it.exists() }?.absolutePath
                }
                "jar" -> f.absolutePath
                else -> null
            }
        }
        val out = outFile.get().asFile
        out.writeText(entries.joinToString(";"))
        println("compile classpath: ${entries.size} 项 → ${out.absolutePath}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    // 试听/播放：Media3/ExoPlayer（Apache-2.0）+ 复用现有 OkHttpClient 的数据源。
    // 只加这两个：media3-session（通知栏/锁屏）等阶段 8 再说。
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    // 通知栏 / 锁屏 / 蓝牙控制（MediaSessionService）
    implementation(libs.androidx.media3.session)
}
