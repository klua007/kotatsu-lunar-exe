<#
.SYNOPSIS
    Builds the Kotatsu Lunar Windows desktop application: classes, tests, self-contained jar,
    a native .exe launcher (jpackage app-image), a portable .zip and, when the WiX toolset is
    available, an .msi installer.

.DESCRIPTION
    Requires a JDK 17+ (jpackage is part of the JDK) and a Kotlin command line compiler.

        # CI / manual build
        $env:KOTLINC_HOME = "$env:RUNNER_TEMP\kotlin-compiler-2.1.21"
        $env:JAVA_HOME   = "C:\hostedtoolcache\windows\Java_Temurin-Hotspot_jdk\17\..."
        pwsh scripts/build-windows.ps1

    Produces (under build\):
        dist\kotatsu-lunar.jar                              runnable jar
        dist\KotatsuLunar-<version>-windows-x64-portable.zip  no-install package (contains the .exe)
        build\package\KotatsuLunar\KotatsuLunar.exe          app image launcher
        dist\KotatsuLunar-<version>.msi                      installer (if WiX is installed)

.NOTES
    This is the unofficial desktop port; see NOTICE.md for credits and licensing.
#>
[CmdletBinding()]
param(
    [string]$AppVersion = "0.2.0",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$OutDir = if ($env:OUT_DIR) { $env:OUT_DIR } else { Join-Path $Root "build" }
$JarName = "kotatsu-lunar.jar"
$MainClass = "org.koitharu.kotatsu.desktop.MainKt"
$TestClass = "org.koitharu.kotatsu.desktop.test.TestSuiteKt"
$AppName = "KotatsuLunar"

function Fail($message) {
    Write-Host "error: $message" -ForegroundColor Red
    exit 1
}

# --- Toolchain ---------------------------------------------------------------------------------
$JavaHome = $env:JAVA_HOME
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
    Fail "JAVA_HOME must point to a JDK 17 or newer"
}
$Java = Join-Path $JavaHome "bin\java.exe"
$JarTool = Join-Path $JavaHome "bin\jar.exe"
$JPackage = Join-Path $JavaHome "bin\jpackage.exe"
if (-not (Test-Path $JarTool)) { Fail "jar.exe not found - a full JDK (not a JRE) is required" }
if (-not (Test-Path $JPackage)) { Write-Host "warning: jpackage.exe not found; the .exe/.msi packages will be skipped" -ForegroundColor Yellow }

$Kotlinc = $env:KOTLINC
if (-not $Kotlinc -and $env:KOTLINC_HOME) {
    $candidate = Join-Path $env:KOTLINC_HOME "bin\kotlinc.bat"
    if (Test-Path $candidate) { $Kotlinc = $candidate }
}
if (-not $Kotlinc) {
    $command = Get-Command "kotlinc.bat" -ErrorAction SilentlyContinue
    if ($command) { $Kotlinc = $command.Source }
}
if (-not $Kotlinc -or -not (Test-Path $Kotlinc)) {
    Fail "kotlinc not found. Set KOTLINC_HOME to the extracted Kotlin compiler directory."
}
$KotlinHome = Split-Path -Parent (Split-Path -Parent $Kotlinc)
$StdLib = Join-Path $KotlinHome "lib\kotlin-stdlib.jar"
if (-not (Test-Path $StdLib)) { Fail "kotlin-stdlib.jar not found under $KotlinHome\lib" }

Write-Host "== Kotatsu Lunar Windows build $AppVersion ==" -ForegroundColor Cyan
Write-Host "kotlinc : $Kotlinc"
Write-Host "java    : $Java"
Write-Host "out     : $OutDir"

# --- Clean -------------------------------------------------------------------------------------
$MainOut = Join-Path $OutDir "classes\main"
$TestOut = Join-Path $OutDir "classes\test"
$Dist = Join-Path $OutDir "dist"
$PackageDir = Join-Path $OutDir "package"
foreach ($path in @($MainOut, $TestOut, $Dist, $PackageDir)) {
    if (Test-Path $path) { Remove-Item -Recurse -Force $path }
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}

$MainSources = Get-ChildItem -Path (Join-Path $Root "desktop\src\main\kotlin") -Recurse -Filter *.kt |
    Sort-Object FullName | ForEach-Object { $_.FullName }
$TestSources = Get-ChildItem -Path (Join-Path $Root "desktop\src\test\kotlin") -Recurse -Filter *.kt |
    Sort-Object FullName | ForEach-Object { $_.FullName }
if (-not $MainSources) { Fail "no Kotlin sources found" }

# --- Compile -----------------------------------------------------------------------------------
Write-Host "-- compiling $($MainSources.Count) source files"
& $Kotlinc -jvm-target 17 -nowarn -d $MainOut $MainSources
if ($LASTEXITCODE -ne 0) { Fail "compilation failed" }

# --- Tests -------------------------------------------------------------------------------------
if (-not $SkipTests) {
    Write-Host "-- compiling $($TestSources.Count) test files"
    & $Kotlinc -jvm-target 17 -nowarn "-Xfriend-paths=$MainOut" -classpath $MainOut -d $TestOut $TestSources
    if ($LASTEXITCODE -ne 0) { Fail "test compilation failed" }
    Write-Host "-- running tests"
    & $Java "-Dkotatsu.test=1" "-Djava.awt.headless=true" -cp "$MainOut;$TestOut;$StdLib" $TestClass
    if ($LASTEXITCODE -ne 0) { Fail "tests failed" }
}

# --- Fat jar -----------------------------------------------------------------------------------
Write-Host "-- building $JarName"
$Stage = Join-Path $OutDir "jar-stage"
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null
Copy-Item -Path (Join-Path $MainOut "*") -Destination $Stage -Recurse -Force

$StdLibZip = Join-Path $OutDir "kotlin-stdlib.zip"
Copy-Item $StdLib $StdLibZip -Force
$StdLibDir = Join-Path $OutDir "stdlib"
if (Test-Path $StdLibDir) { Remove-Item -Recurse -Force $StdLibDir }
Expand-Archive -Path $StdLibZip -DestinationPath $StdLibDir -Force
Remove-Item -Force (Join-Path $StdLibDir "META-INF\MANIFEST.MF") -ErrorAction SilentlyContinue
Remove-Item -Force (Join-Path $StdLibDir "module-info.class") -ErrorAction SilentlyContinue
Copy-Item -Path (Join-Path $StdLibDir "*") -Destination $Stage -Recurse -Force

$JarPath = Join-Path $Dist $JarName
& $JarTool --create --file $JarPath --main-class $MainClass -C $Stage .
if ($LASTEXITCODE -ne 0) { Fail "jar creation failed" }

Write-Host "-- jar self test"
& $Java -jar $JarPath --version
if ($LASTEXITCODE -ne 0) { Fail "jar does not start" }
& $Java -jar $JarPath --smoke-test
if ($LASTEXITCODE -ne 0) { Fail "smoke test failed for the jar" }

# --- Windows packages (jpackage) ----------------------------------------------------------------
$PortableZip = Join-Path $Dist "$AppName-$AppVersion-windows-x64-portable.zip"
$MsiPath = Join-Path $Dist "$AppName-$AppVersion.msi"
if (Test-Path $JPackage) {
    $JpackageInput = Join-Path $OutDir "jpackage-input"
    if (Test-Path $JpackageInput) { Remove-Item -Recurse -Force $JpackageInput }
    New-Item -ItemType Directory -Force -Path $JpackageInput | Out-Null
    Copy-Item $JarPath (Join-Path $JpackageInput $JarName) -Force

    $Icon = Join-Path $Root "desktop\resources\icon.ico"
    Write-Host "-- creating the native launcher (app-image)"
    $appImageArgs = @(
        "--type", "app-image",
        "--name", $AppName,
        "--app-version", $AppVersion,
        "--vendor", "Kotatsu Lunar (unofficial desktop port)",
        "--description", "Kotatsu Lunar - unofficial Windows port of Kotatsu-Redo",
        "--input", $JpackageInput,
        "--main-jar", $JarName,
        "--main-class", $MainClass,
        "--dest", $PackageDir,
        "--java-options", "-Xmx1536m",
        "--java-options", "-Dfile.encoding=UTF-8"
    )
    if (Test-Path $Icon) { $appImageArgs += @("--icon", $Icon) }
    & $JPackage @appImageArgs
    if ($LASTEXITCODE -ne 0) { Fail "jpackage app-image failed" }

    $AppImage = Join-Path $PackageDir $AppName
    $Launcher = Join-Path $AppImage "$AppName.exe"
    if (-not (Test-Path $Launcher)) { Fail "launcher was not produced at $Launcher" }

    Write-Host "-- packaged launcher self test"
    & $Launcher --version
    if ($LASTEXITCODE -ne 0) { Fail "packaged launcher does not start" }
    & $Launcher --smoke-test
    if ($LASTEXITCODE -ne 0) { Fail "smoke test failed for the packaged launcher" }

    # Portable package: the app image plus the licence and notices (GPL compliance).
    foreach ($doc in @("README.md", "LICENSE", "NOTICE.md")) {
        $source = Join-Path $Root $doc
        if (Test-Path $source) { Copy-Item $source (Join-Path $AppImage $doc) -Force }
    }
    if (Test-Path $PortableZip) { Remove-Item -Force $PortableZip }
    Compress-Archive -Path (Join-Path $AppImage "*") -DestinationPath $PortableZip -CompressionLevel Optimal
    Write-Host "portable package: $PortableZip"

    # MSI installer (needs the WiX toolset). CI installs it with choco; local builds may skip it.
    $WixRoot = Get-ChildItem "C:\Program Files (x86)\WiX Toolset*" -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $WixRoot) {
        $WixRoot = Get-ChildItem "C:\Program Files\WiX Toolset*" -Directory -ErrorAction SilentlyContinue |
            Select-Object -First 1
    }
    if ($WixRoot -and (Test-Path (Join-Path $WixRoot.FullName "bin\candle.exe"))) {
        Write-Host "-- creating the MSI installer (WiX: $($WixRoot.FullName))"
        $env:PATH = (Join-Path $WixRoot.FullName "bin") + ";" + $env:PATH
        $msiArgs = @(
            "--type", "msi",
            "--name", $AppName,
            "--app-version", $AppVersion,
            "--vendor", "Kotatsu Lunar (unofficial desktop port)",
            "--description", "Kotatsu Lunar - unofficial Windows port of Kotatsu-Redo",
            "--input", $JpackageInput,
            "--main-jar", $JarName,
            "--main-class", $MainClass,
            "--dest", $Dist,
            "--java-options", "-Xmx1536m",
            "--java-options", "-Dfile.encoding=UTF-8",
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-per-user-install"
        )
        if (Test-Path $Icon) { $msiArgs += @("--icon", $Icon) }
        & $JPackage @msiArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Host "warning: MSI creation failed; the portable .zip is still available" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "warning: WiX toolset not found; skipping the MSI installer" -ForegroundColor Yellow
    }
}
else {
    Write-Host "warning: jpackage missing; only the jar was produced" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Build OK" -ForegroundColor Green
Write-Host "  jar            : $JarPath"
if (Test-Path $PortableZip) { Write-Host "  portable zip   : $PortableZip" }
if (Test-Path $MsiPath) { Write-Host "  installer      : $MsiPath" }
if (Test-Path (Join-Path $PackageDir $AppName)) {
    Write-Host "  application    : $(Join-Path $PackageDir "$AppName\$AppName.exe")"
}
