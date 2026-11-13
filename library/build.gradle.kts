import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "io.github.wwwvolley.easylog"
    compileSdk = 37

    defaultConfig {
        // 21 是 Compose 的下限，但 androidx.core 1.19 起要求 23，
        // 取 23（Android 6.0）对宿主工程没有任何额外约束，覆盖面已经足够
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // 发布 release 变体 + sources jar（发布到 mavenLocal / GitHub Packages 时用）
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 用 api 而不是 implementation：对外暴露的公开 API 里有 @Composable 与 Modifier，
    // 宿主编译期必须有 Compose；用 implementation 的话 Maven 坐标只带 runtime 作用域，
    // 别的工程直接依赖 AAR 时会缺编译期符号
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.core.ktx)
    // installEasyLogOverlay() 的接收者是 ComponentActivity，属于对外 API 的一部分
    api(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}

// ── 发布配置 ───────────────────────────────────────────────────────
// 用法：
//   本地： ./gradlew :library:publishToMavenLocal
//   到本地仓库目录：./gradlew :library:publishReleasePublicationToLocalRepoRepository
//   GitHub Packages：把 repositories 换成 maven { url = uri("https://maven.pkg.github.com/...") }
val libGroup: String = providers.gradleProperty("easylog.group").getOrElse("io.github.wwwvolley")
val libVersion: String = providers.gradleProperty("easylog.version").getOrElse("1.0.0")

// 必须等 AGP 把变体注册完（`components["release"]` 才存在）再建 publication
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = libGroup
                artifactId = "easylog-console"
                version = libVersion

                pom {
                    name.set("easyLogConsole")
                    description.set("App 内悬浮日志台：一个悬浮球 + 半透明日志面板，用来在真机上直接看关键链路日志。")
                    url.set("https://github.com/wwwVolley/easyLogConsole")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/wwwVolley/easyLogConsole.git")
                        developerConnection.set("scm:git:ssh://git@github.com/wwwVolley/easyLogConsole.git")
                        url.set("https://github.com/wwwVolley/easyLogConsole")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "localRepo"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }
}
