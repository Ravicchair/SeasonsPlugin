#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "=== Seasons plugin builder ==="

if ! command -v javac >/dev/null 2>&1; then
  echo "Could not find the Java compiler (javac). You need a JDK, not just Java."
  echo "Install JDK 25 from https://adoptium.net and run this again."
  exit 1
fi

JV=$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)
if [ "$JV" -lt 25 ]; then
  echo "Your JDK is too old (found $JV). This plugin needs JDK 25."
  echo "Install JDK 25 from https://adoptium.net and run this again."
  exit 1
fi

JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
export JAVA_HOME

MVN_VERSION=3.9.9
MVN_DIR=".build/apache-maven-$MVN_VERSION"
if [ ! -x "$MVN_DIR/bin/mvn" ]; then
  echo "Downloading build tools (one time only)..."
  mkdir -p .build
  curl -fL -o .build/maven.zip "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MVN_VERSION/apache-maven-$MVN_VERSION-bin.zip"
  unzip -q -o .build/maven.zip -d .build
  chmod +x "$MVN_DIR/bin/mvn"
fi

echo "Building the plugin. The first run downloads Paper's libraries and takes a few minutes..."
if ! "$MVN_DIR/bin/mvn" -B clean package; then
  echo "BUILD FAILED. Copy the ERROR lines above and send them to Claude."
  exit 1
fi

cp target/Seasons-1.0.0.jar Seasons.jar
echo "Done! Your plugin is: $(pwd)/Seasons.jar"
