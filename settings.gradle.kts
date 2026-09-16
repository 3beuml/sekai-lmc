pluginManagement {
    repositories {
        // ── 国内镜像优先 ──────────────────────────────────────────────
        // 中国大陆直连 google() / mavenCentral() / plugins.gradle.org 经常超时，
        // 把阿里云镜像放前面可以显著减少「卡在下载依赖」的情况。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")

        // ── 官方源兜底（在能直连的网络下会用到）──────────────────────
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：镜像优先，官方源兜底
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "sekai-lmc"
include(":app")
