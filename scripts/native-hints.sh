#!/usr/bin/env bash
# Collects GraalVM reachability hints for the `smith` native binary by running the CLI and
# the builder's offline tests under the native-image tracing agent, then merges the result.
#
# Copy what it writes over
# prod/embabel-smith-builder/src/main/resources/META-INF/native-image/com.embabel.smith/builder-traced-hints/reachability-metadata.json
# — that directory holds only generated hints. The hand-written ones (resources Embabel opens by
# name, Kotlin-reflection bindings) live beside it in builder-native-hints/ and are never
# regenerated. Rebuild with `make native` and re-run the binary: an unregistered type or resource
# surfaces there as a MissingReflectionRegistrationError naming exactly what to add.
#
# Usage: scripts/native-hints.sh        (output dir: OUT, default /tmp/smith-hints)
set -euo pipefail

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$DIR/.." && pwd)

# A GraalVM JDK is the one that ships the tracing agent; llm.env may well JAVA_HOME elsewhere,
# so this is a fixed default rather than a fallback.
GRAAL_HOME=${GRAAL_HOME:-$HOME/.sdkman/candidates/java/25-graalce}
test -x "$GRAAL_HOME/bin/java" || { echo "no GraalVM at $GRAAL_HOME (sdk install java 25-graalce)"; exit 1; }
export JAVA_HOME="$GRAAL_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

OUT=${OUT:-/tmp/smith-hints}
MODULE=$REPO/prod/embabel-smith-builder
CP="$MODULE/target/classes:$(cat "${CP_FILE:-/tmp/smith-cp.txt}")"

rm -rf "$OUT"
mkdir -p "$OUT"

# The agent writes on JVM exit, so every invocation below contributes; a failing run is not a
# reason to stop collecting.
run() {
	echo "### smith $*"
	"$JAVA_HOME/bin/java" \
		-agentlib:native-image-agent=config-merge-dir="$OUT" \
		-cp "$CP" com.embabel.smith.builder.SmithBuilderApplication "$@" || true
}

cd "$REPO"
run
run build --help
run blueprint --help
run build "An analyst reads a market brief and writes a one paragraph summary." \
	--out "folder:/tmp/smith-hints-out" --max-refine 0

# The offline suite covers the validate / write / publish and security paths, which no single
# CLI invocation reaches. Surefire forks its own JVM, hence JAVA_HOME above.
./mvnw -q test -pl :embabel-smith-builder \
	-DargLine="-agentlib:native-image-agent=config-merge-dir=$OUT" || true

python3 - "$OUT/reachability-metadata.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
print("collected:", len(d.get("reflection", [])), "reflection entries,",
      len(d.get("resources", [])), "resource globs")
print("->", sys.argv[1])
PY
