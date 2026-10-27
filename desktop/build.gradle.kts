// Kotatsu Lunar — desktop (Windows) port of Kotatsu-Redo.
//
// The application has no third-party runtime dependencies: the UI is Swing (JDK), archives are read
// with java.util.zip, images with ImageIO and JSON with an in-tree parser. That keeps the build
// reproducible and the packaged application small (see docs/ARCHITECTURE.md).
//
// Typical commands (run from this directory):
//   gradle desktopTests      run the test suite
//   gradle jar               build the runnable jar
//   gradle packageImage      build the native app image with jpackage (needs a JDK 17+)
//   gradle packageMsi        build the MSI installer (Windows + WiX)
//   gradle packagePortable   build the portable .zip
//
// Without Gradle, `scripts/build.sh` (Linux/macOS) and `scripts/build-windows.ps1` (Windows) run
// exactly the same steps with a plain kotlinc installation, which is what CI uses.

import org.gradle.internal.os.OperatingSystem

plugins {
	kotlin("jvm") version "2.1.21"
}

// The version is declared once, in BuildInfo.kt, and read back here so that `gradle jar`,
// `jpackage` and the CI workflow all package the version that was actually compiled.
val appVersionFromFile: String = providers.provider {
	val buildInfo = file("src/main/kotlin/org/koitharu/kotatsu/desktop/core/BuildInfo.kt")
	Regex("""const val APP_VERSION = "([0-9][0-9A-Za-z.+-]*)"""")
		.find(buildInfo.readText())
		?.groupValues
		?.get(1)
		?: error("APP_VERSION not found in ${'$'}buildInfo")
}.get()

group = "org.koitharu.kotatsu.lunar"
version = appVersionFromFile

repositories {
	mavenCentral()
}

kotlin {
	jvmToolchain(17)
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
	main {
		kotlin.srcDir("src/main/kotlin")
	}
	test {
		kotlin.srcDir("src/test/kotlin")
	}
}

val appVersion = version.toString()
val mainClassName = "org.koitharu.kotatsu.desktop.MainKt"
val testMainClassName = "org.koitharu.kotatsu.desktop.test.TestSuiteKt"

// The test suite is a plain Kotlin program (no test framework), run on the JVM.
val desktopTests by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs the Kotatsu Lunar desktop test suite"
	dependsOn(tasks.named("testClasses"))
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set(testMainClassName)
	systemProperty("kotatsu.test", "1")
	systemProperty("java.awt.headless", "true")
}

tasks.named("check") {
	dependsOn(desktopTests)
}

tasks.jar {
	manifest {
		attributes(
			"Main-Class" to mainClassName,
			"Implementation-Title" to "Kotatsu Lunar",
			"Implementation-Version" to appVersion,
		)
	}
	archiveFileName.set("kotatsu-lunar.jar")
	// Single self-contained jar: the Kotlin standard library is the only runtime dependency.
	from({
		configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
	})
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	exclude("META-INF/MANIFEST.MF")
	exclude("module-info.class")
	exclude("META-INF/versions/9/module-info.class")
}

val packageInput = layout.buildDirectory.dir("jpackage-input")
val packageOutput = layout.buildDirectory.dir("package")
val jpackage = File(System.getProperty("java.home"), if (OperatingSystem.current().isWindows) "bin/jpackage.exe" else "bin/jpackage")

val prepareJpackageInput by tasks.registering(Copy::class) {
	dependsOn(tasks.jar)
	from(tasks.jar.flatMap { it.archiveFile })
	into(packageInput)
}

fun jpackageTask(name: String, type: String, extraArgs: List<String> = emptyList()) =
	tasks.register<Exec>(name) {
		group = "distribution"
		dependsOn(prepareJpackageInput)
		onlyIf { jpackage.exists() }
		val args = mutableListOf(
			jpackage.absolutePath,
			"--type", type,
			"--name", "KotatsuLunar",
			"--app-version", appVersion,
			"--vendor", "Kotatsu Lunar (unofficial desktop port)",
			"--description", "Kotatsu Lunar - unofficial Windows port of Kotatsu-Redo",
			"--input", packageInput.get().asFile.absolutePath,
			"--main-jar", "kotatsu-lunar.jar",
			"--main-class", mainClassName,
			"--dest", packageOutput.get().asFile.absolutePath,
			"--java-options", "-Xmx1536m",
			"--java-options", "-Dfile.encoding=UTF-8",
		)
		val icon = file("resources/icon.ico")
		if (icon.exists()) {
			args += listOf("--icon", icon.absolutePath)
		}
		args += extraArgs
		commandLine(args)
	}

val packageImage = jpackageTask("packageImage", "app-image")
val packageMsi = jpackageTask(
	"packageMsi",
	"msi",
	listOf("--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install"),
)

val packagePortable by tasks.registering(Zip::class) {
	group = "distribution"
	dependsOn(packageImage)
	archiveFileName.set("KotatsuLunar-$appVersion-windows-x64-portable.zip")
	destinationDirectory.set(layout.buildDirectory.dir("dist"))
	from(packageOutput.map { it.dir("KotatsuLunar") }) {
		include("**/*")
	}
	val repositoryRoot = projectDir.parentFile
	from(repositoryRoot.resolve("README.md")) { into("") }
	from(repositoryRoot.resolve("LICENSE")) { into("") }
	from(repositoryRoot.resolve("NOTICE.md")) { into("") }
}
