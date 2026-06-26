#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT"
mvn -f Web_Client_TeaVM/pom.xml \
	-Dweb.mainClass=com.voidscape.webclient.WebClientProbeMain \
	clean package
