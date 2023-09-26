import org.sonatype.gradle.plugins.scan.ossindex.OutputFormat

plugins {
    id("java")
    jacoco
    checkstyle
    alias(libs.plugins.versioner)
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.abort.mission) apply false
    alias(libs.plugins.index.scan)
    alias(libs.plugins.owasp.dependencycheck)
}

group = "com.github.nagyesta.file-barj"

project.apply(from = "config/ossindex/ossIndexAudit.gradle.kts")

buildscript {
    fun optionalPropertyString(name: String): String {
        return if (project.hasProperty(name)) {
            project.property(name) as String
        } else {
            ""
        }
    }

    // Define versions in a single place
    extra.apply {
        set("gitToken", optionalPropertyString("githubToken"))
        set("gitUser", optionalPropertyString("githubUser"))
        set("ossrhUser", optionalPropertyString("ossrhUsername"))
        set("ossrhPass", optionalPropertyString("ossrhPassword"))
        set("ossIndexUser", optionalPropertyString("ossIndexUsername"))
        set("ossIndexPass", optionalPropertyString("ossIndexPassword"))
        set("repoUrl", "https://github.com/nagyesta/file-barj")
        set("licenseName", "MIT License")
        set("licenseUrl", "https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE")
        set("maintainerId", "nagyesta")
        set("maintainerName", "Istvan Zoltan Nagy")
        set("maintainerUrl", "https://github.com/nagyesta/")
        set("scmConnection", "scm:git:https://github.com/nagyesta/file-barj.git")
        set("scmProjectUrl", "https://github.com/nagyesta/file-barj/")
    }
}

versioner {
    startFrom {
        major = 0
        minor = 0
        patch = 1
    }
    match {
        major = "{major}"
        minor = "{minor}"
        patch = "{patch}"
    }
    pattern {
        pattern = "%M.%m.%p"
    }
    git {
        authentication {
            https {
                token = project.extra.get("gitToken").toString()
            }
        }
    }
    tag {
        prefix = "v"
        useCommitMessage = true
    }
}

versioner.apply()

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "org.gradle.jacoco")
    apply(plugin = "org.gradle.checkstyle")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "org.sonatype.gradle.plugins.scan")
    apply(plugin = "org.owasp.dependencycheck")
    apply(plugin = "com.github.nagyesta.abort-mission-gradle-plugin")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    tasks.javadoc.configure {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    jacoco {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    tasks.jacocoTestReport {
        reports {
            xml.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/report.xml"))
            csv.required.set(false)
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        }
        dependsOn(tasks.test)
        finalizedBy(tasks.getByName("jacocoTestCoverageVerification"))
    }

    tasks.withType<JacocoCoverageVerification>().configureEach {
        inputs.file(layout.buildDirectory.file("reports/jacoco/report.xml"))
        outputs.file(layout.buildDirectory.file("reports/jacoco/jacocoTestCoverageVerification"))

        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal.valueOf(0.8)
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal.valueOf(0.8)
                }
                excludes = mutableListOf()
            }
            rule {
                element = "CLASS"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal.valueOf(0.5)
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal.valueOf(0.5)
                }
                excludes = mutableListOf()
            }
        }
        doLast {
            layout.buildDirectory.file("reports/jacoco/jacocoTestCoverageVerification").get().asFile.writeText("Passed")
        }
    }

    tasks.test {
        useJUnitPlatform()
        finalizedBy(tasks.getByName("jacocoTestReport"))
    }

    java {
        withJavadocJar()
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.jar.configure {
        dependsOn(tasks.check)
    }

    tasks.withType<Checkstyle>().configureEach {
        configProperties = mutableMapOf<String, Any>(
                "base_dir" to rootDir.absolutePath.toString(),
                "cache_file" to layout.buildDirectory.file("checkstyle/cacheFile").get().asFile.absolutePath.toString()
        )
        checkstyle.toolVersion = rootProject.libs.versions.checkstyle.get()
        checkstyle.configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        reports {
            xml.required.set(false)
            html.required.set(true)
            html.stylesheet = rootProject.resources.text.fromFile("config/checkstyle/checkstyle-stylesheet.xsl")
        }
    }

    tasks.withType<PublishToMavenRepository>().configureEach {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/nagyesta/lowkey-vault")
                credentials {
                    username = rootProject.extra.get("gitUser").toString()
                    password = rootProject.extra.get("gitToken").toString()
                }
            }
            maven {
                name = "ossrh"
                url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                credentials {
                    username = rootProject.extra.get("ossrhUser").toString()
                    password = rootProject.extra.get("ossrhPass").toString()
                }
            }
        }
    }

    //Disable metadata publishing and rely on Maven only
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    ossIndexAudit {
        username = rootProject.extra.get("ossIndexUser").toString()
        password = rootProject.extra.get("ossIndexPass").toString()
        isPrintBanner = false
        isColorEnabled = true
        isShowAll = false
        outputFormat = OutputFormat.DEPENDENCY_GRAPH
        @Suppress("UNCHECKED_CAST")
        excludeVulnerabilityIds = rootProject.extra.get("ossIndexExclusions") as MutableSet<String>
    }
}

ossIndexAudit {
    username = rootProject.extra.get("ossIndexUser").toString()
    password = rootProject.extra.get("ossIndexPass").toString()
    isPrintBanner = false
    isColorEnabled = true
    isShowAll = false
    outputFormat = OutputFormat.DEPENDENCY_GRAPH
    @Suppress("UNCHECKED_CAST")
    excludeVulnerabilityIds = rootProject.extra.get("ossIndexExclusions") as MutableSet<String>
}

repositories {
    mavenCentral()
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(false)
        html.required.set(false)
        csv.required.set(false)
    }
}
