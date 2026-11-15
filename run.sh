#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

APP_JAR=travel-app/target/travel-app-0.0.1-SNAPSHOT.jar
REQUIRED_JAVA=21

usage() {
    cat <<EOF
Usage: ./run.sh [command] [app args...]

Commands:
  start     build without tests and start the API on :8080 (default)
  build     build all modules and run the tests
  test      run the tests
  reset     drop both shard databases (travel_shard_0, travel_shard_1); the next start recreates them
  rebalance after adding a shard: move external_id records to their new owners, then exit
  help      show this message

Extra arguments after "start" go to the app, for example:
  ./run.sh start --server.port=9090

Needs MariaDB on localhost:3306 with user travel/travel allowed to create travel_* databases
(see README). SHARD_0_URL, SHARD_1_URL, DATABASE_USERNAME and DATABASE_PASSWORD override the defaults.
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
        echo "Starting API (default http://localhost:8080, shards travel_shard_0 and travel_shard_1)"
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
    rebalance)
        find_java
        ./mvnw -q -B package -DskipTests
        exec java -jar "$APP_JAR" --rebalance --spring.main.web-application-type=none "$@"
        ;;
    reset)
        mariadb -h 127.0.0.1 -u "${DATABASE_USERNAME:-travel}" -p"${DATABASE_PASSWORD:-travel}" \
            -e "DROP DATABASE IF EXISTS travel_shard_0; DROP DATABASE IF EXISTS travel_shard_1"
        echo "Dropped travel_shard_0 and travel_shard_1"
        ;;
    help | -h | --help)
        usage
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
