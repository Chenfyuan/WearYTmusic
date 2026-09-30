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
        // NewPipeExtractor is only on JitPack. Gradle doesn't fall through on 5xx errors, so
        // each repository is restricted to the groups it actually serves.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.TeamNewPipe") }
        }
        maven("https://maven.aliyun.com/repository/google") { googleGroups() }
        google { googleGroups() }
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

fun MavenArtifactRepository.googleGroups() = content {
    includeGroupByRegex("com\\.android.*")
    includeGroupByRegex("com\\.google\\.android.*")
    includeGroupByRegex("androidx.*")
}
rootProject.name = "WearYTmusic"
include(":app")
