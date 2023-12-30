plugins {
    id("java")
    signing
    `maven-publish`
    alias(libs.plugins.abort.mission)
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

tasks.jar {
    manifest.attributes["Main-Class"] = "com.github.nagyesta.filebarj.job.Main"
    val dependencies = configurations
            .runtimeClasspath
            .get()
            .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
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
        maven {
            name = "ossrh"
            url = uri(rootProject.extra.get("ossrhMavenRepoUrl").toString())
            credentials {
                username = rootProject.extra.get("ossrhUser").toString()
                password = rootProject.extra.get("ossrhPass").toString()
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = name
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
                withXml {
                    asElement().apply {
                        val deps = this.getElementsByTagName("dependencies").item(0)
                        this.removeChild(deps)
                    }
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
