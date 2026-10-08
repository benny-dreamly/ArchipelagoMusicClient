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
version = (project.findProperty("appVersion") as String?) ?: "5.3.2"

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

// macOS-only Dock identity for `gradle run` (menu bar name + Dock tile).
// The Activity Monitor process name still shows "java" without a bundled .app;
// packageApp produces the real .app with the proper process name.
tasks.named<JavaExec>("run") {
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        val dockIcon = file("packaging/AppIcon.icns").absolutePath
        jvmArgs("-Xdock:name=Archipelago Music Client", "-Xdock:icon=$dockIcon")
    }
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
    implementation("org.fxmisc.richtext:richtextfx:0.11.7")
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

// --- App icon & packaging (per-platform) ---

val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
val isWindowsOs = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

// Rebuilds packaging/AppIcon.icns from app-icon-1024.png (macOS only; needs sips + iconutil).
// Run again after dropping in your real 1024x1024 PNG:
//   ./gradlew generateIcns
tasks.register<Exec>("generateIcns") {
    group = "packaging"
    description = "Rebuilds packaging/AppIcon.icns from src/main/resources/icons/app-icon-1024.png (macOS; sips + iconutil)"
    onlyIf { isMacOs }
    val png = file("src/main/resources/icons/app-icon-1024.png")
    val iconset = layout.buildDirectory.dir("icons/AppIcon.iconset").get().asFile
    val icns = file("packaging/AppIcon.icns")
    inputs.file(png)
    outputs.file(icns)
    workingDir = iconset
    doFirst {
        iconset.mkdirs()
    }
    commandLine("bash", "-c", """
        set -e
        for spec in "16 icon_16x16.png" "32 icon_16x16@2x.png" "32 icon_32x32.png" "64 icon_32x32@2x.png" \
                    "128 icon_128x128.png" "256 icon_128x128@2x.png" "256 icon_256x256.png" "512 icon_256x256@2x.png" \
                    "512 icon_512x512.png" "1024 icon_512x512@2x.png"; do
          read -r size out <<< "${'$'}spec"
          sips -z "${'$'}size" "${'$'}size" "${png.absolutePath}" --out "${'$'}out" >/dev/null
        done
        iconutil -c icns . -o "${icns.absolutePath}"
    """.trimIndent())
}

// Regenerates packaging/AppIcon.ico (Windows jpackage needs .ico) from the
// 1024px source. macOS-only (uses sips to resize); the .ico is committed to the
// repo, so Windows/Linux builds use the committed copy and never run this.
// Run after replacing the PNG:
//   ./gradlew generateIco
tasks.register<Exec>("generateIco") {
    group = "packaging"
    description = "Rebuilds packaging/AppIcon.ico from src/main/resources/icons/app-icon-1024.png (macOS; sips)"
    onlyIf { isMacOs }
    val png = file("src/main/resources/icons/app-icon-1024.png")
    val ico = file("packaging/AppIcon.ico")
    inputs.file(png)
    outputs.file(ico)
    commandLine(
        "python3", "-c", """
        import struct
        import subprocess
        import sys
        import tempfile

        src = sys.argv[1]
        dst = sys.argv[2]
        with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
            small = f.name
        subprocess.run(['sips', '-z', '256', '256', src, '--out', small], check=True, capture_output=True)
        with open(small, 'rb') as f:
            data = f.read()
        # ICO with a single PNG-compressed entry (Windows Vista+ supports PNG entries).
        # The u8 width/height can't hold 256, so 0 means 256.
        header = struct.pack('<HHH', 0, 1, 1)
        entry = struct.pack('<BBBBHHII', 0, 0, 0, 0, 1, 32, len(data), 6 + 16)
        with open(dst, 'wb') as f:
            f.write(header + entry + data)
    """.trimIndent(),
        png.absolutePath,
        ico.absolutePath,
    )
}

// Builds the native app with jpackage (backed by your JDK). On macOS it produces
// "Archipelago Music Client.app": correct Dock icon, and the Activity Monitor
// process name becomes "Archipelago Music Client" instead of "java". On Windows
// it produces the .exe with our .ico; on Linux an executable with the icon PNG.
//   ./gradlew packageApp    # -> build/pkg/Archipelago Music Client.<app|exe|bin>
tasks.register<Exec>("packageApp") {
    group = "packaging"
    description = "Builds the native 'Archipelago Music Client' app with jpackage for the current OS"
    dependsOn("shadowJar")
    val fatJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
        .get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("pkg").get().asFile
    // Bundle name jpackage produces under outputDir: "<name>.app" on macOS,
    // "<name>" on Windows and Linux.
    val bundleDir = outputDir.resolve(
        if (isMacOs) "Archipelago Music Client.app" else "Archipelago Music Client"
    )
    val icon = if (isWindowsOs) {
        file("packaging/AppIcon.ico")
    } else if (isMacOs) {
        file("packaging/AppIcon.icns")
    } else {
        file("src/main/resources/icons/app-icon-512.png")
    }
    inputs.file(fatJar)
    inputs.file(icon)
    outputs.dir(outputDir)
    doFirst {
        // jpackage refuses to overwrite an existing destination bundle.
        bundleDir.deleteRecursively()
    }
    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "Archipelago Music Client",
        "--app-version", project.version.toString(),
        "--input", fatJar.parentFile.absolutePath,
        "--main-jar", fatJar.name,
        "--main-class", "app.Main",
        "--icon", icon.absolutePath,
        "--dest", outputDir.absolutePath,
        "--java-options", "--enable-native-access=javafx.graphics",
    )
}
