#!/bin/bash
# Test runner with progress tracking (macOS compatible)

LOG_FILE="/tmp/maven-test-progress.log"
TEST_LOG="/tmp/maven-test-output.log"
TOTAL_TESTS=$(find src/test -name "*Test.java" | wc -l | tr -d ' ')

echo "========================================"
echo "Running $TOTAL_TESTS test classes..."
echo "Log: $LOG_FILE"
echo "========================================"
echo ""

# Clear log files
> "$LOG_FILE"
> "$TEST_LOG"

# Start maven test in background, capturing output
mvn test "$@" > "$TEST_LOG" 2>&1 &
MAVEN_PID=$!

# Progress loop
while kill -0 $MAVEN_PID 2>/dev/null; do
    # Count completed test classes
    completed=$(grep -c "Tests run:" "$TEST_LOG" 2>/dev/null | tr -d '[:space:]')
    if [ -n "$completed" ] && [ "$completed" -gt 0 ]; then
        # Cap at total tests
        if [ "$completed" -gt "$TOTAL_TESTS" ]; then
            completed=$TOTAL_TESTS
        fi
        percent=$((completed * 100 / TOTAL_TESTS))
        bar=$(printf '%*s' $((percent / 2)) '' | tr ' ' '█')
        empty=$(printf '%*s' $((50 - percent / 2)) '' | tr ' ' '░')
        printf "\r[$bar$empty] %3d%% (%d/%d) - Running..." "$percent" "$completed" "$TOTAL_TESTS"
    fi
    sleep 1
done

wait $MAVEN_PID
EXIT_CODE=$?

echo ""
echo ""
echo "========================================"
if [ $EXIT_CODE -eq 0 ]; then
    echo "✓ Test run completed successfully!"
else
    echo "✗ Test run failed!"
fi
echo "========================================"
echo "Full log: $LOG_FILE"
echo "Test output: $TEST_LOG"