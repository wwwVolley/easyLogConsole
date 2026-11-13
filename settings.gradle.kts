pluginManagement {
    repositories {
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
        google()
        mavenCentral()
    }
}

rootProject.name = "easyLogConsole"

// 库本体。模块名故意写成 `easylog-console`，跟发布坐标的 artifactId 一致 ——
// 宿主的 composite build（includeBuild）靠 group:name 自动替换依赖，
// 名字对上了就**不用再写 dependencySubstitution**，接入只剩一行 includeBuild。
include(":easylog-console")
project(":easylog-console").projectDir = file("library")

// 演示 / 自测用，不发布
include(":sample")
