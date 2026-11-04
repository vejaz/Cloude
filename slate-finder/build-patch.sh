#!/usr/bin/env bash
# Builds a patched Slate Finder jar: takes an existing jar, replaces the Describing finder tab with the
# new one in src/, and writes a NEW jar next to it. The jar you pass in is never modified.
#
#   ./build-patch.sh path/to/SlateFinder-<old>.jar [out.jar]
set -euo pipefail
base="${1:?usage: build-patch.sh <existing SlateFinder jar> [output jar]}"
here="$(cd "$(dirname "$0")" && pwd)"
out="${2:-$here/SlateFinder-$(date +%Y%m%d-%H%M%S).jar}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/classes"
( cd "$work/classes" && unzip -q "$base" )
# the old tab's classes go; everything else (the other tabs, the tests, the textures) is kept as it was
rm -f "$work/classes"/slatefinder/DescribePanel*.class
javac --release 21 -encoding UTF-8 -cp "$work/classes" -d "$work/classes" "$here"/src/slatefinder/*.java
jar --create --file "$out" --main-class slatefinder.App -C "$work/classes" .
echo "wrote $out"
