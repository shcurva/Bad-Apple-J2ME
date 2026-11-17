#!/bin/bash
set -e
cd "$(dirname "$0")"

if [ -z "$1" ]; then
    echo "Usage: ./build.sh <project>"
    exit 1
fi

PROJECT="$1"
PROJ_DIR="j2me/projects/$PROJECT"

if [ ! -d "$PROJ_DIR" ]; then
    echo "Project not found: $PROJ_DIR" >&2
    exit 1
fi

J8=/usr/lib/jvm/java-8-openjdk-amd64
JAVAC="$J8/bin/javac"
JARCMD="$J8/bin/jar"

command -v proguard >/dev/null || { echo "sudo apt install -y proguard" >&2; exit 1; }

SDK="sdk/midpapi20.jar:sdk/cldcapi11.jar"
SRC="$PROJ_DIR/src/BadAppleMidlet.java"
CLASSES="$PROJ_DIR/build/classes"
META="$PROJ_DIR/build/META-INF"
OUT="$PROJ_DIR/build/BadApple.jar"
PV="$PROJ_DIR/build/pv"

# Выбираем правильную иконку и копируем в проект
case "$PROJECT" in
    *_lite)       ICON="j2me/icon_24x24.png" ;;
    *_full)       ICON="j2me/icon_48x48.png" ;;
    30fps_ultra)  ICON="j2me/icon_64x64.png" ;;
    *)            ICON="j2me/icon_32x32.png" ;;
esac

cp "$ICON" "$PROJ_DIR/icon.png"

rm -rf "$CLASSES" "$META" "$OUT" "$PV"
mkdir -p "$CLASSES" "$META" "$PV/unpacked"

echo ">>> Compiling $PROJECT (Icon: $ICON)..."
"$JAVAC" -source 1.3 -target 1.3 -bootclasspath "$SDK" -d "$CLASSES" "$SRC"

echo ">>> Preverifying for CLDC..."
JAVA_HOME="$J8" PATH="$J8/bin:$PATH" proguard \
    -injars "$CLASSES" \
    -outjars "$PV/classes.jar" \
    -libraryjars "$SDK" \
    -microedition \
    -dontshrink -dontoptimize -dontobfuscate -dontwarn

pushd "$PV/unpacked" > /dev/null
"$JARCMD" xf ../classes.jar
popd > /dev/null

case "$PROJECT" in
    10fps) NAME="Bad Apple!! 10 FPS" ;;
    13fps) NAME="Bad Apple!! 13 FPS" ;;
    15fps) NAME="Bad Apple!! 15 FPS" ;;
    10fps_lite) NAME="Bad Apple!! 10 FPS Lite" ;;
    13fps_lite) NAME="Bad Apple!! 13 FPS Lite" ;;
    15fps_lite) NAME="Bad Apple!! 15 FPS Lite" ;;
    20fps) NAME="Bad Apple!! 20 FPS" ;;
    20fps_lite) NAME="Bad Apple!! 20 FPS Lite" ;;
    20fps_full) NAME="Bad Apple!! 20 FPS Full" ;;
    30fps_ultra) NAME="Bad Apple!! 30 FPS Ultra" ;;
    *) echo "Unknown project: $PROJECT" >&2; exit 1 ;;
esac

cat > "$META/MANIFEST.MF" << MANIFEST
Manifest-Version: 1.0
MIDlet-1: $NAME, /icon.png, BadAppleMidlet
MIDlet-Vendor: Shcurva
MicroEdition-Configuration: CLDC-1.0
MIDlet-Name: $NAME
MIDlet-Version: 1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST

"$JARCMD" cfm "$OUT" "$META/MANIFEST.MF" \
    -C "$PV/unpacked" . \
    -C "$PROJ_DIR" badapple.bin \
    -C "$PROJ_DIR" bad_apple.mid \
    -C "$PROJ_DIR" icon.png

echo ">>> SUCCESS: $OUT ($(du -h "$OUT" | cut -f1))"
