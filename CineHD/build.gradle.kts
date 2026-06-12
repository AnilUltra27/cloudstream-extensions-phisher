import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension.Companion.cloudstream

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.phisher.cinehd"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    // Cloudstream engine frameworks compiled at runtime
    implementation(kotlin("stdlib"))
    implementation("com.github.recloudstream:cloudstream:master-SNAPSHOT")
}

configure<CloudstreamExtension> {
    // Specifies package location of your main API class
    setClassName("com.phisher.CineHDProvider")
    
    // Developer profile details
    setAuthor("NewbieDev")
    setVersion(1)
    setDescription("Scrapes and extracts streaming indexes from CineHD")
}

kotlin {
    jvmToolchain(11)
}
