#!/usr/bin/env bash
set -euo pipefail

BASE="/tmp/secure-downloads-demo"
SECRET_DIR="/tmp/protected"
DEPLOY_NAME="target.txt"           # the filename your API will request

# cleanup & create
rm -rf "$BASE" "$SECRET_DIR"
mkdir -p "$BASE" "$SECRET_DIR"

# permanent real source (never moved)
REAL_SRC="$BASE/target-real-source"
echo "NORMAL_CONTENT" > "$REAL_SRC"

# protected secret outside deploy dir (attacker wants to exfiltrate this)
SECRET="$SECRET_DIR/secret.txt"
echo "TOP_SECRET: do-not-share" > "$SECRET"

# ensure deployed file exists as hardlink to the permanent source
TARGET_PATH="$BASE/$DEPLOY_NAME"
ln "$REAL_SRC" "$TARGET_PATH"

ls -la "$BASE" "$SECRET_DIR"
echo "Initialized. Deployed path: $TARGET_PATH -> real source: $REAL_SRC"
