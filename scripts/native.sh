#!/usr/bin/env bash
# Builds the builder's native binary: prod/embabel-smith-builder/target/smith.
#
# The wiring (Spring AOT processing, reachability metadata, main class, --no-fallback)
# lives in the `native` profile of prod/embabel-smith-builder/pom.xml, so this script only
# has to put a GraalVM JDK with native-image in front of Maven.
set -euo pipefail

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$DIR/.."

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-graalce >/dev/null || true
sdk use java 25-graalce
export GRAALVM_HOME="$JAVA_HOME"

native-image --version
java -version

./mvnw package -Pnative -DskipTests -pl :embabel-smith-builder -am

ls -lh prod/embabel-smith-builder/target/smith
