import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "9.2.0"
    id("checkstyle")
    id("com.github.spotbugs") version "6.4.5"
    id("net.ltgt.errorprone") version "4.1.0"
}

group = "org.example"
version = "5.0.1"

repositories {
    mavenCentral()
}

application {
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
    )
    // fully qualified name of your main Application class
    mainClass.set("app.Main")
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.media")
}

dependencies {
    // --- Multi-Platform JavaFX Classifiers ---
    // Explicitly pulls native binaries for Windows x64, Linux x64, and macOS Apple Silicon
    val javafxVersion = "25"
    val javafxModules = listOf("base", "graphics", "controls", "media")
    val platforms = listOf("win", "linux", "mac-aarch64")

    for (platform in platforms) {
        for (module in javafxModules) {
            implementation("org.openjfx:javafx-$module:$javafxVersion:$platform")
        }
    }

    // --- Standard Dependencies ---
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("io.github.archipelagomw:Java-Client:0.2.0")
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Add SLF4J Simple Logger
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    annotationProcessor("com.google.errorprone:error_prone_core:2.23.0")

    // SpotBugs annotations (optional but helpful)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")

    // ✅ Add Apache HttpClient (required by Java-Client and SpotBugs analysis)
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2.4")

    // ✅ Error Prone dependencies
    errorprone("com.google.errorprone:error_prone_core:2.44.0")  // Updated to latest
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
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${this@configureEach.name}.html"))
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

// Add this to handle ShadowJar duplicates in Gradle 9.x
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ✅ Error Prone Configuration
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        isEnabled.set(true)
        disableWarningsInGeneratedCode.set(true)

        // Set severity levels
        error(
            "DefaultCharset",  // Catches the FileReader issue SpotBugs found
            "StreamResourceLeak"
        )

        warn(
            "UnusedVariable",
            "UnusedMethod"
        )

    }
}

tasks.test {
    useJUnitPlatform()
}