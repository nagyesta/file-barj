import org.cyclonedx.Version
import org.sonarqube.gradle.SonarTask
import org.sonatype.gradle.plugins.scan.ossindex.OutputFormat
import java.util.*

plugins {
    id("java")
    jacoco
    checkstyle
    alias(libs.plugins.sonar.qube)
    alias(libs.plugins.versioner)
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.index.scan)
    alias(libs.plugins.owasp.dependencycheck)
    alias(libs.plugins.nexus.publish.plugin)
    alias(libs.plugins.cyclonedx.bom)
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
        set("githubMavenRepoUrl", "https://maven.pkg.github.com/nagyesta/file-barj")
        set("ossrhMavenRepoUrl", "https://ossrh-staging-api.central.sonatype.com/service/local/")
        set("ossrhSnapshotRepoUrl", "https://central.sonatype.com/repository/maven-snapshots/")
        set("sonarOrganization", "nagyesta")
        set("sonarProjectKey", "nagyesta_file-barj")
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

sonar {
    properties {
        property("sonar.coverage.jacoco.xmlReportPaths", layout.buildDirectory.file("reports/jacoco/report.xml").get().asFile.path)
        property("sonar.junit.reportPaths", layout.buildDirectory.dir("test-results/test").get().asFile.path)
        property("sonar.sources", "src/main/java")
        property("sonar.exclusions", "**/*.md,.github/**,.idea/**")
        property("sonar.organization", rootProject.extra.get("sonarOrganization") as String)
        property("sonar.projectKey", rootProject.extra.get("sonarProjectKey") as String)
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.gradle.jacoco")
    apply(plugin = "org.gradle.checkstyle")
    apply(plugin = "org.gradle.signing")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "org.sonatype.gradle.plugins.scan")
    apply(plugin = "org.owasp.dependencycheck")
    apply(plugin = "org.cyclonedx.bom")

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

    tasks.withType(SonarTask::class).forEach {
        it.dependsOn(tasks.jacocoTestReport)
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
                excludes = mutableListOf(
                        "com.github.nagyesta.filebarj.core.backup.FileParseException",
                        "com.github.nagyesta.filebarj.core.backup.worker.WindowsFileMetadataParser",
                        "com.github.nagyesta.filebarj.core.progress.NoOpProgressTracker",
                        "com.github.nagyesta.filebarj.core.restore.worker.WindowsFileMetadataSetter",
                        "com.github.nagyesta.filebarj.job.Main",
                        "com.github.nagyesta.filebarj.job.Controller"
                )
            }
        }
    }

    tasks.test {
        outputs.upToDateWhen { false }
        useJUnitPlatform()
        systemProperties["ci"] = rootProject.hasProperty("ci").toString()
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
        outputFormat = OutputFormat.DEFAULT
        @Suppress("UNCHECKED_CAST")
        excludeVulnerabilityIds = rootProject.extra.get("ossIndexExclusions") as MutableSet<String>
    }

    tasks.cyclonedxDirectBom {
        if (project.name.endsWith("job")) {
            projectType.set(org.cyclonedx.model.Component.Type.APPLICATION)
        } else {
            projectType.set(org.cyclonedx.model.Component.Type.LIBRARY)
        }
        schemaVersion.set(Version.VERSION_16)
        includeConfigs.set(listOf("runtimeClasspath"))
        skipConfigs.set(listOf("compileClasspath", "testCompileClasspath"))
        jsonOutput = project.layout.buildDirectory.file("reports/bom.json").get().asFile
        //noinspection UnnecessaryQualifiedReference
        val attachmentText = org.cyclonedx.model.AttachmentText()
        attachmentText.text = Base64.getEncoder().encodeToString(
            file("${project.rootProject.projectDir}/LICENSE").readBytes()
        )
        attachmentText.encoding = "base64"
        attachmentText.contentType = "text/plain"
        //noinspection UnnecessaryQualifiedReference
        val license = org.cyclonedx.model.License()
        license.name = "MIT License"
        license.setLicenseText(attachmentText)
        license.url = "https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE"
        licenseChoice = org.cyclonedx.model.LicenseChoice().apply {
            addLicense(license)
        }
    }
}

ossIndexAudit {
    username = rootProject.extra.get("ossIndexUser").toString()
    password = rootProject.extra.get("ossIndexPass").toString()
    isPrintBanner = false
    isColorEnabled = true
    isShowAll = false
    outputFormat = OutputFormat.DEFAULT
    @Suppress("UNCHECKED_CAST")
    excludeVulnerabilityIds = rootProject.extra.get("ossIndexExclusions") as MutableSet<String>
}

checkstyle {
    toolVersion = rootProject.libs.versions.checkstyle.get()
}

val writeVersion = tasks.register<DefaultTask>("writeVersion") {
    group = "versioning"
    description = "Writes project version to a file."
    outputs.file(layout.buildDirectory.file("version").get().asFile)
    inputs.property("version", project.version)

    val versionFile = file("build/version")
    val versionText = project.version.toString()
    doLast {
        versionFile.writeText("v${versionText}")
    }
    mustRunAfter(tasks.clean)
}.get()
tasks.build.get().dependsOn(writeVersion)

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

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri(rootProject.extra.get("ossrhMavenRepoUrl").toString()))
            snapshotRepositoryUrl.set(uri(rootProject.extra.get("ossrhSnapshotRepoUrl").toString()))
            username = rootProject.extra.get("ossrhUser").toString()
            password = rootProject.extra.get("ossrhPass").toString()
        }
    }
}
