#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

./mvnw -q -B clean package
cp target/hw-template.jar hw-template.jar
java -jar hw-template.jar . | tee results.txt
