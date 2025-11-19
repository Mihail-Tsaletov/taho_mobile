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

        maven { url = uri("https://artifactory.yandex.net/artifactory/maven") }
        // или этот (оба работают):
        // maven { url = uri("https://maven.yandex.ru/repository/maven-releases") }
    }
}

rootProject.name = "taho_prime"
include(":app")
 