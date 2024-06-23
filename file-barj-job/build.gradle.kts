plugins {
    id("java")
    signing
    `maven-publish`
    alias(libs.plugins.abort.mission)
    alias(libs.plugins.shadow)
    alias(libs.plugins.licensee.plugin)
}

extra.apply {
    set("artifactDisplayName", "File BaRJ - Job")
    set("artifactDescription", "Executable Jar for easy execution of backup or restore jobs.")
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    implementation(project(":file-barj-stream-io"))
    implementation(project(":file-barj-core"))
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.validation)
    implementation(libs.commons.io)
    implementation(libs.commons.cli)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.slf4j.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.jupiter)
    testImplementation(libs.abort.mission.jupiter)
    testImplementation(libs.mockito.core)
}

abortMission {
    toolVersion = libs.versions.abortMission.get()
}

tasks.shadowJar {
    manifest.attributes["Main-Class"] = "com.github.nagyesta.filebarj.job.Main"
    append("META-INF/LICENSE")
    append("META-INF/LICENSE.txt")
    append("META-INF/NOTICE")
    append("META-INF/NOTICE.txt")
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    archiveClassifier.set("")
}
tasks.build.get().finalizedBy(tasks.shadowJar)

val copyLegalDocs = tasks.register<Copy>("copyLegalDocs") {
    from(file("${project.rootProject.projectDir}/LICENSE"))
    from(layout.buildDirectory.file("reports/licensee/artifacts.json").get().asFile)
    from(layout.buildDirectory.file("reports/bom.json").get().asFile)
    into(layout.buildDirectory.dir("resources/main/META-INF").get().asFile)
    rename("artifacts.json", "dependency-licenses.json")
    rename("bom.json", "SBOM.json")
}.get()
copyLegalDocs.dependsOn(tasks.licensee)
copyLegalDocs.dependsOn(tasks.cyclonedxBom)
tasks.javadoc.get().dependsOn(copyLegalDocs)
tasks.compileJava.get().dependsOn(copyLegalDocs)
tasks.processResources.get().finalizedBy(copyLegalDocs)

licensee {
    allow("MIT")
    allow("Apache-2.0")
    allow("LGPL-2.1-only")
    allow("BSD-2-Clause")
    allow("GPL-2.0-with-classpath-exception")
    allowUrl("https://www.bouncycastle.org/licence.html")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(rootProject.extra.get("githubMavenRepoUrl").toString())
            credentials {
                username = rootProject.extra.get("gitUser").toString()
                password = rootProject.extra.get("gitToken").toString()
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            artifact(tasks["shadowJar"])
            artifactId = project.name
            pom {
                name.set(project.extra.get("artifactDisplayName").toString())
                description.set(project.extra.get("artifactDescription").toString())
                url.set(rootProject.extra.get("repoUrl").toString())
                packaging = "jar"
                licenses {
                    license {
                        name.set(rootProject.extra.get("licenseName").toString())
                        url.set(rootProject.extra.get("licenseUrl").toString())
                    }
                }
                developers {
                    developer {
                        id.set(rootProject.extra.get("maintainerId").toString())
                        name.set(rootProject.extra.get("maintainerName").toString())
                        email.set(rootProject.extra.get("maintainerUrl").toString())
                    }
                }
                scm {
                    connection.set(rootProject.extra.get("scmConnection").toString())
                    developerConnection.set(rootProject.extra.get("scmConnection").toString())
                    url.set(rootProject.extra.get("scmProjectUrl").toString())
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
