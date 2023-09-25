plugins {
    id("java")
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

tasks.test {
    useJUnitPlatform()
}

abortMission {
    toolVersion = libs.versions.abortMission.get()
}
