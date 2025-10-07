plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics"
    )
    // fully qualified name of your main Application class
    mainClass.set("app.MusicAppDemo")
}

javafx {
    version = "24"
    modules = listOf("javafx.controls", "javafx.media")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.github.archipelagomw:Java-Client:0.2.0")
}

tasks.test {
    useJUnitPlatform()
}