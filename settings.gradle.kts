pluginManagement {
    repositories {
        maven("https://edgedl.me.gvt1.com/dl/android/maven2/") {
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") }
        }
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://edgedl.me.gvt1.com/dl/android/maven2/") {
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx\\..*"); includeGroupByRegex("com\\.google\\.testing.*") }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "Schedule"
include(":app")
