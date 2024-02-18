import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogSectionUrlBuilder
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.intellij") version "1.17.1"
    id("org.jetbrains.changelog") version "2.0.0"
    id("idea")
    id("java")
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    maven(
        url = "https://oss.sonatype.org/content/repositories/snapshots/"
    )
    maven(
        url = "https://packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-structure"
    )
    maven(
        url = "https://www.jetbrains.com/intellij-repository/releases"
    )
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("dev.failsafe:failsafe:3.3.1")
}


intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    downloadSources.set(properties("platformDownloadSources").toBoolean())
    updateSinceUntilBuild.set(true)

    plugins.set(
        listOf(
            "Git4Idea"
        )
    )
}

changelog {
    header.set(provider { "${version.get()}" })
    headerParserRegex.set("""(\d+\.\d+.\d+)""".toRegex())
    version.set(properties("pluginVersion"))
    groups.set(listOf("Added", "Changed", "Fixed"))
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    lineSeparator.set("\n")
    combinePreReleases.set(true)
    sectionUrlBuilder.set(ChangelogSectionUrlBuilder { repositoryUrl, currentVersion, previousVersion, isUnreleased -> "foo" })
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        version.set(properties("pluginVersion"))
        changeNotes.set(provider {
            changelog.renderItem(
                changelog
                    .getLatest()
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        })
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }

    runIde {
        jvmArgs("-Xmx4G", "-Didea.log.debug.categories=#de.sist", "-DgitlabPipelineViewerDebugging=true")
    }

    buildSearchableOptions {
        //Allows building the plugin while a sandbox is running
        enabled = false
    }
}


