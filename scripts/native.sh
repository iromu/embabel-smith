#!/usr/bin/env bash
# Builds the builder's native binary: prod/embabel-smith-builder/target/smith.
#
# The wiring (Spring AOT processing, reachability metadata, main class, --no-fallback)
# lives in the `native` profile of prod/embabel-smith-builder/pom.xml, so this script only
# has to put a GraalVM JDK with native-image in front of Maven.
set -euo pipefail

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$DIR/.."

# sdkman's scripts are not nounset-clean — the init reads ZSH_VERSION and `sdk use` reads
# help_flag, each of which aborts under this script's `set -u` (and an abort inside a sourced
# file or a function leaves no `||` fallback to run). So the whole sdkman interaction runs with
# nounset off, and the strict settings resume for the build itself.
set +u
# shellcheck disable=SC1091
source "$HOME/.sdkman/bin/sdkman-init.sh"

# The GraalVM patch the shipped binary is built with -- the same value
# .github/workflows/native-image.yml pins as GRAAL_VERSION, and the patch the polyglot
# artifacts in prod/embabel-smith-builder/pom.xml are pinned to (a newer polyglot has
# already broken a native build once). sdkman's rolling "25-graalce" is not a patch version,
# so without this a local build silently runs on a different GraalVM than the one CI proves.
# The candidate is used only if it is already here: asking sdkman for a version it does not
# have means an API call that hangs a build script with no network, which is worse than the
# drift it is here to catch.
GRAAL_VERSION=${GRAAL_VERSION:-25.0.2}
if [[ -d "$HOME/.sdkman/candidates/java/$GRAAL_VERSION-graalce" ]]; then
  sdk use java "$GRAAL_VERSION-graalce" >/dev/null
else
  echo "note: no local sdkman candidate java $GRAAL_VERSION-graalce, using 25-graalce." >&2
  echo "      native-image.yml builds with $GRAAL_VERSION, so this binary is not the one CI" >&2
  echo "      proves. Install the patch, or set GRAAL_VERSION to a candidate you have." >&2
  sdk install java 25-graalce >/dev/null || true
  sdk use java 25-graalce
fi
set -u
export GRAALVM_HOME="$JAVA_HOME"

native-image --version
java -version
# The pair, side by side: whatever the two lines above say, this is the line CI's
# GRAAL_VERSION names. A mismatch is not fatal -- a newer GraalVM may build fine -- but it
# means the binary in front of you was not built by the toolchain the workflow documents.
echo "expected GraalVM patch (native-image.yml GRAAL_VERSION): $GRAAL_VERSION"

./mvnw package -Pnative -DskipTests -pl :embabel-smith-builder -am

ls -lh prod/embabel-smith-builder/target/smith
