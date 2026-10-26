#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUI_DIR="$ROOT_DIR/src"
OUT_DIR="$ROOT_DIR/out"

# Set JAVAFX_LIB if JavaFX is installed somewhere other than the common paths.
JAVAFX_LIB="${JAVAFX_LIB:-}"
if [[ -z "$JAVAFX_LIB" ]]; then
  for candidate in \
    "$ROOT_DIR/javafx-sdk-25/lib" \
    "$ROOT_DIR/../javafx-sdk-25/lib" \
    "$HOME/javafx-sdk-25/lib"; do
    if [[ -f "$candidate/javafx.controls.jar" ]]; then
      JAVAFX_LIB="$candidate"
      break
    fi
  done
fi

if [[ -z "$JAVAFX_LIB" || ! -f "$JAVAFX_LIB/javafx.controls.jar" ]]; then
  echo "JavaFX SDK not found. Install JavaFX 25 and run again." >&2
  echo "Or set JAVAFX_LIB to its lib directory, for example:" >&2
  echo "  JAVAFX_LIB=/path/to/javafx-sdk-25/lib ./run-gui.sh" >&2
  exit 1
fi

for variable in DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required database environment variable: $variable" >&2
    exit 1
  fi
done

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

javac \
  --module-path "$JAVAFX_LIB" \
  --add-modules javafx.controls,javafx.fxml \
  -cp "$ROOT_DIR/src/external-resources/postgresql-42.2.8.jar" \
  -d "$OUT_DIR" \
  $(find "./src" -name '*.java' -print)

mkdir -p "$OUT_DIR/frontend"
cp -R "$GUI_DIR/frontend/resources" "$OUT_DIR/frontend/"

exec java \
  --enable-native-access=javafx.graphics \
  --module-path "$JAVAFX_LIB" \
  --add-modules javafx.controls,javafx.fxml \
  -cp "$OUT_DIR:$ROOT_DIR/src/external-resources/postgresql-42.2.8.jar" \
  frontend.app.DatabaseApp