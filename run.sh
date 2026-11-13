#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

APP_JAR=travel-app/target/travel-app-0.0.1-SNAPSHOT.jar
REQUIRED_JAVA=21

usage() {
    cat <<EOF
Usage: ./run.sh [command] [app args...]

Commands:
  start   build without tests and start the API on :8080 (default)
  build   build all modules and run the tests
  test    run the tests
  reset   delete the local database in ./data
  help    show this message

Extra arguments after "start" go to the app, for example:
  ./run.sh start --server.port=9090
  ./run.sh start --spring.profiles.active=postgres
EOF
}

java_major_version() {
    "$1" -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print v[1]; exit }'
}

use_java() {
    local candidate=$1
    [[ -x "$candidate/bin/java" ]] || return 1
    [[ "$(java_major_version "$candidate/bin/java")" -ge $REQUIRED_JAVA ]] || return 1
    export JAVA_HOME=$candidate
    export PATH="$JAVA_HOME/bin:$PATH"
}

find_java() {
    if [[ -n "${JAVA_HOME:-}" ]] && use_java "$JAVA_HOME"; then
        return
    fi
    if command -v java >/dev/null && [[ "$(java_major_version java)" -ge $REQUIRED_JAVA ]]; then
        return
    fi
    if [[ -x /usr/libexec/java_home ]] && use_java "$(/usr/libexec/java_home -v $REQUIRED_JAVA 2>/dev/null)"; then
        return
    fi
    if command -v brew >/dev/null && use_java "$(brew --prefix openjdk@$REQUIRED_JAVA 2>/dev/null)"; then
        return
    fi
    echo "JDK $REQUIRED_JAVA or newer is required. On macOS: brew install openjdk@$REQUIRED_JAVA" >&2
    exit 1
}

command=${1:-start}
[[ $# -gt 0 ]] && shift

case "$command" in
    start)
        find_java
        ./mvnw -q -B package -DskipTests
        echo "Starting API (default http://localhost:8080, data in ./data)"
        exec java -jar "$APP_JAR" "$@"
        ;;
    build)
        find_java
        ./mvnw -B clean package
        ;;
    test)
        find_java
        ./mvnw -B test
        ;;
    reset)
        rm -rf data
        echo "Deleted ./data"
        ;;
    help | -h | --help)
        usage
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
