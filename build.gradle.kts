plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("checkstyle")
    id("com.github.spotbugs") version "6.4.5"
}

group = "org.example"
version = "2.0"

repositories {
    mavenCentral()
}

application {
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
        "-Dorg.slf4j.simpleLogger.logFile=System.out",
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

    // Add SLF4J Simple Logger
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    annotationProcessor("com.google.errorprone:error_prone_core:2.23.0")

    // SpotBugs annotations (optional but helpful)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")

    // ✅ Add Apache HttpClient (required by Java-Client and SpotBugs analysis)
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.4")
}

spotbugs {
    toolVersion.set("4.9.8")
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    ignoreFailures.set(true) // Make it optional - won't fail the build
}

// ✅ SpotBugs configuration for Kotlin DSL
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
        outputLocation.set(file("${layout.buildDirectory.get()}/reports/spotbugs/${this@configureEach.name}.html"))
    }
    reports.create("xml") {
        required.set(false)
    }
}

// ✅ Checkstyle Configuration - CHECK ONLY, NO AUTO-FORMAT
checkstyle {
    toolVersion = "10.12.5"
    configFile = file("${rootProject.projectDir}/config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true // Make it optional - won't fail the build
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}