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
    }
}

rootProject.name = "SmartMovie"

include(
    ":app",
    ":core:model",
    ":core:network",
    ":core:database",
    ":core:data",
    ":core:designsystem",
    ":core:testing",
    ":feature:home",
    ":feature:explore",
    ":feature:search",
    ":feature:library",
    ":feature:detail",
    ":feature:about",
)
