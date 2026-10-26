pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Sakugapp"
include(":app")
// Isolated black-box runner; never part of the shipped application or normal build.
if (providers.gradleProperty("upgradeUiCheck").orNull == "true") include(":upgrade-ui-check")
