plugins {
    id("java")
}

dependencies {
    implementation(project(":file-barj-core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.jupiter)
    testImplementation(libs.abort.mission.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

abortMission {
    toolVersion = libs.versions.abortMission.get()
}
