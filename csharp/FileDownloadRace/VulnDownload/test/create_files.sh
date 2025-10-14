STORAGE="/tmp/secure-downloads-demo"
rm -rf "$STORAGE"                # remove any broken state from previous attempts
mkdir -p "$STORAGE"
# create safe / attack files and the deployed file
echo "NORMAL_CONTENT" > "$STORAGE/target-real"
echo "ATTACKER_CONTENT" > "$STORAGE/target-attack"
cp "$STORAGE/target-real" "$STORAGE/target.txt"
# show them
ls -la "$STORAGE"
