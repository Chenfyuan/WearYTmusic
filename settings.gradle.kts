pluginManagement {
    repositories {
        // Mirrors first (faster in mainland China); official sources as fallback.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // NewPipeExtractor is only published on JitPack (not mirrored by Aliyun).
        maven("https://jitpack.io")
    }
}
rootProject.name = "WearYTmusic"
include(":app")
