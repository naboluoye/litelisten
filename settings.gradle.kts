pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // TinyPinyin 2.0.3 只发布在已停服的 JCenter，用镜像补回；限制 group 避免污染其他依赖解析。
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
            content { includeGroup("com.github.promeg") }
        }
    }
}

rootProject.name = "LiteListen"
include(":app")
