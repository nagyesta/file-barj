plugins {
    id("java")
    signing
    `maven-publish`
    alias(libs.plugins.abort.mission)
}

extra.apply {
    set("artifactDisplayName", "File BaRJ - Core")
    set("artifactDescription", "Defines the inner working mechanism of backup and restore tasks.")
}

dependencies {
    implementation(libs.bundles.jackson)
    implementation(libs.commons.codec)
    implementation(libs.commons.compress)
    implementation(libs.commons.crypto)
    implementation(libs.commons.io)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.jupiter)
    testImplementation(libs.abort.mission.jupiter)
    testImplementation(libs.mockito.core)
}

abortMission {
    toolVersion = libs.versions.abortMission.get()
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
            artifactId = tasks.jar.get().archiveBaseName.get()
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
