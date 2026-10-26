#!/usr/bin/env bash
#
# Kotatsu Lunar — desktop build (Linux/macOS, offline-friendly).
#
# This script only needs a JDK 17+ and a Kotlin command line compiler (kotlinc). It is the same
# pipeline that `scripts/build-windows.ps1` uses on Windows, and it is what CI runs, so the
# dependency-free build can be reproduced without Gradle and without network access to Maven.
#
# Usage:
#   KOTLINC_HOME=/path/to/kotlin-compiler-2.1.21 JAVA_HOME=/path/to/jdk scripts/build.sh
#
# Environment:
#   KOTLINC_HOME   directory that contains bin/kotlinc (or set KOTLINC to the binary itself)
#   JAVA_HOME      JDK 17 or newer
#   SKIP_TESTS=1   skip the test suite
#   OUT_DIR        build output directory (default: <repo>/build)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/build}"
KOTLINC="${KOTLINC:-${KOTLINC_HOME:-}/bin/kotlinc}"
APP_VERSION="0.1.0"
JAR_NAME="kotatsu-lunar.jar"
MAIN_CLASS="org.koitharu.kotatsu.desktop.MainKt"
TEST_CLASS="org.koitharu.kotatsu.desktop.test.TestSuiteKt"

if [[ ! -x "$KOTLINC" ]]; then
	echo "error: kotlinc not found at '$KOTLINC' (set KOTLINC_HOME or KOTLINC)" >&2
	exit 1
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
	echo "error: JAVA_HOME must point to a JDK 17+" >&2
	exit 1
fi
JAVA="$JAVA_HOME/bin/java"
STDLIB="$KOTLINC_HOME/lib/kotlin-stdlib.jar"
if [[ ! -f "$STDLIB" ]]; then
	STDLIB="$(dirname "$(dirname "$KOTLINC")")/lib/kotlin-stdlib.jar"
fi

echo "== Kotatsu Lunar desktop build =="
echo "kotlinc : $KOTLINC"
echo "java    : $JAVA"
echo "out     : $OUT_DIR"

MAIN_SRC="$ROOT/desktop/src/main/kotlin"
TEST_SRC="$ROOT/desktop/src/test/kotlin"
MAIN_OUT="$OUT_DIR/classes/main"
TEST_OUT="$OUT_DIR/classes/test"
DIST="$OUT_DIR/dist"

rm -rf "$MAIN_OUT" "$TEST_OUT"
mkdir -p "$MAIN_OUT" "$TEST_OUT" "$DIST"

mapfile -t MAIN_FILES < <(find "$MAIN_SRC" -name '*.kt' | sort)
mapfile -t TEST_FILES < <(find "$TEST_SRC" -name '*.kt' | sort)

echo "-- compiling ${#MAIN_FILES[@]} source files"
"$KOTLINC" -jvm-target 17 -nowarn -d "$MAIN_OUT" "${MAIN_FILES[@]}"

if [[ "${SKIP_TESTS:-0}" != "1" ]]; then
	echo "-- compiling ${#TEST_FILES[@]} test files"
	"$KOTLINC" -jvm-target 17 -nowarn -Xfriend-paths="$MAIN_OUT" -classpath "$MAIN_OUT" -d "$TEST_OUT" "${TEST_FILES[@]}"
	echo "-- running tests"
	"$JAVA" -Dkotatsu.test=1 -Djava.awt.headless=true -cp "$MAIN_OUT:$TEST_OUT:$STDLIB" "$TEST_CLASS"
fi

echo "-- writing $JAR_NAME"
STAGE="$OUT_DIR/jar-stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -r "$MAIN_OUT"/. "$STAGE"/
# The application only needs the Kotlin standard library, which is merged into the jar so that the
# result is a single self-contained file (used by jpackage).
if [[ -f "$STDLIB" ]]; then
	mkdir -p "$OUT_DIR/stdlib"
	(cd "$OUT_DIR/stdlib" && rm -rf ./* && unzip -q "$STDLIB" && rm -rf META-INF/versions/9/module-info.class module-info.class META-INF/MANIFEST.MF)
	cp -r "$OUT_DIR/stdlib"/. "$STAGE"/
fi

MANIFEST="$OUT_DIR/manifest.mf"
{
	echo "Manifest-Version: 1.0"
	echo "Main-Class: $MAIN_CLASS"
	echo "Implementation-Title: Kotatsu Lunar"
	echo "Implementation-Version: $APP_VERSION"
} >"$MANIFEST"

JAR_TOOL="$JAVA_HOME/bin/jar"
if [[ -x "$JAR_TOOL" ]]; then
	"$JAR_TOOL" --create --file "$DIST/$JAR_NAME" --manifest "$MANIFEST" -C "$STAGE" .
else
	# A JRE has no `jar` tool; build the archive with the JDK-free helper instead.
	python3 "$ROOT/scripts/make_jar.py" "$DIST/$JAR_NAME" "$STAGE" "$MANIFEST"
fi

echo "-- smoke test"
"$JAVA" -jar "$DIST/$JAR_NAME" --version
"$JAVA" -jar "$DIST/$JAR_NAME" --smoke-test

echo
echo "Build OK"
echo "  jar: $DIST/$JAR_NAME"
echo "  run: $JAVA -jar $DIST/$JAR_NAME"
