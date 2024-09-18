import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogSectionUrlBuilder
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("org.jetbrains.intellij.platform") version "2.0.0-beta9"
//    id("org.jetbrains.intellij.platform.migration") version "2.0.0-beta7"
    id("org.jetbrains.changelog") version "2.0.0"
    id("idea")
    id("java")
    kotlin("jvm")
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    mavenLocal()
    maven(
        url = "https://oss.sonatype.org/content/repositories/snapshots/"
    )
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("dev.failsafe:failsafe:3.3.1")
    implementation(kotlin("stdlib-jdk8"))

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
}


intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
    }
    buildSearchableOptions = false

    publishing {
        /*
         publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }
        * */
        token = System.getenv("PUBLISH_TOKEN")
    }

    tasks {
        patchPluginXml {
            version = properties("pluginVersion")
            sinceBuild = properties("pluginSinceBuild")
//            untilBuild = properties("pluginUntilBuild")

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

        runIde {
            jvmArgs("-Xmx4G", "-Didea.log.debug.categories=#de.sist", "-DgitlabPipelineViewerDebugging=true")
        }
    }

    verifyPlugin {
        ides {
            recommended()
//            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2023.1")
//            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2023.3")
//            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2024.1")
//            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2024.2")
        }
    }
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



kotlin {
    jvmToolchain(21)
}