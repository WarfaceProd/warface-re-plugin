#!/bin/bash
# Build the WarfaceGPT RE Plugin for Ghidra
# Requires: Maven 3.6+, JDK 17+, GHIDRA_INSTALL_DIR set

set -e

echo "=== WarfaceGPT RE Plugin Build ==="

# Check prerequisites
if [ -z "$GHIDRA_INSTALL_DIR" ]; then
    echo "ERROR: GHIDRA_INSTALL_DIR is not set."
    echo "Set it to your Ghidra installation directory:"
    echo "  export GHIDRA_INSTALL_DIR=/path/to/ghidra"
    exit 1
fi

if [ ! -d "$GHIDRA_INSTALL_DIR" ]; then
    echo "ERROR: GHIDRA_INSTALL_DIR does not exist: $GHIDRA_INSTALL_DIR"
    exit 1
fi

echo "GHIDRA_INSTALL_DIR: $GHIDRA_INSTALL_DIR"

# Build
echo "Building..."
mvn clean package -Dghidra.install.dir="$GHIDRA_INSTALL_DIR"

# Check output
if [ -f "target/WarfaceGPT-RE-Plugin-1.0.0.zip" ]; then
    echo ""
    echo "=== Build Successful ==="
    echo "Extension ZIP: target/WarfaceGPT-RE-Plugin-1.0.0.zip"
    echo ""
    echo "To install in Ghidra:"
    echo "  1. Open Ghidra"
    echo "  2. File → Install Extensions..."
    echo "  3. Click '+' and select the ZIP file"
    echo "  4. Restart Ghidra"
    echo "  5. Window → WarfaceGPT RE"
else
    echo "ERROR: Build failed. ZIP not found."
    exit 1
fi