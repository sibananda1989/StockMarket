#!/bin/bash
#
# Script to run all Portfolio Chart critical fixes tests
# Runs both backend Java tests and frontend Playwright tests
# Exit codes: 0 = success, 1 = test failure
#

set -e  # Exit on first error (remove to continue after backend failures)

SCRIPT_DIR="/Users/sibanandasahoo/Documents/projects/stockmarket"
cd "$SCRIPT_DIR"

echo "========================================"
echo "Portfolio Chart Critical Fixes - Test Runner"
echo "========================================"
echo ""

# Color codes
echo_red() { echo -e "\033[31m$1\033[0m"; }
echo_green() { echo -e "\033[32m$1\033[0m"; }
echo_blue() { echo -e "\033[34m$1\033[0m"; }
echo_yellow() { echo -e "\033[33m$1\033[0m"; }

# Function to check if commands exist
check_dependencies() {
    echo_yellow "Checking dependencies..."

    # Check Java
    if ! command -v java &> /dev/null; then
        echo_red "ERROR: Java JDK 17+ is not installed"
        echo "Please install Java JDK 17 or higher and ensure it's in PATH"
        exit 1
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        echo_red "ERROR: Maven is not installed"
        echo "Please install Maven 3.6+ and ensure it's in PATH"
        exit 1
    fi

    # Check Node.js
    if ! command -v node &> /dev/null; then
        echo_red "ERROR: Node.js is not installed"
        echo "Please install Node.js 16+"
        exit 1
    fi

    # Check npm
    if ! command -v npm &> /dev/null; then
        echo_red "ERROR: npm is not installed"
        echo "Please install npm with Node.js"
        exit 1
    fi

    echo_green "✓ All dependencies available"
    echo ""
}

# Function to run backend tests
run_backend_tests() {
    echo_blue "========================================"
    echo_blue "Running Backend Tests (Java/Spring Boot)"
    echo_blue "========================================"
    echo ""

    echo "Starting Spring Boot context..."
    echo "Running: mvn clean test -Dtest=PortfolioChart*Test"
    echo ""

    # Run tests with detailed output
    mvn clean test -Dtest=PortfolioChart*Test 2>&1 | tee backend-test-output.log || {
        echo_red ""
        echo_red "❌ Backend tests FAILED"
        echo_red "Check backend-test-output.log for details"
        return 1
    }

    # Check if tests passed
    if grep -q "BUILD SUCCESS" backend-test-output.log; then
        echo_green ""
        echo_green "✅ All backend tests PASSED"
        return 0
    else
        echo_red ""
        echo_red "❌ Backend tests FAILED"
        return 1
    fi
}

# Function to run frontend tests
run_frontend_tests() {
    echo_blue "========================================"
    echo_blue "Running Frontend Tests (Playwright)"
    echo_blue "========================================"
    echo ""

    # Check if Playwright browsers are installed
    echo "Checking Playwright browsers..."
    if [ ! -d "/Users/sibanandasahoo/Library/Caches/ms-playwright" ]; then
        echo "Installing Playwright browsers (first time setup)..."
        npm list -g @playwright/test || true
        npm list playwright || true
        npx playwright install || { echo_red "❌ Failed to install Playwright browsers"; return 1; }
        npx playwright install-deps || echo_yellow "⚠️  Could not install system deps (may require sudo)"
    fi

    # Run tests
    echo "Starting Playwright tests..."
    echo "Running: npx playwright test tests/portfolio-chart-fixes.spec.js --reporter=list"
    echo ""

    # Prefer chromium for deterministic testing
    npx playwright test tests/portfolio-chart-fixes.spec.js --reporter=list 2>&1 | tee frontend-test-output.log || {
        echo_red ""
        echo_red "❌ Frontend tests FAILED"
        echo_red "Check frontend-test-output.log for details"
        return 1
    }

    # Quick check for pass/fail
    if grep -q "passed" frontend-test-output.log && ! grep -q "failed" frontend-test-output.log; then
        echo_green ""
        echo_green "✅ All frontend tests PASSED"
        return 0
    else
        echo_red ""
        echo_red "❌ Frontend tests FAILED"
        return 1
    fi
}

# Function to generate summary
show_summary() {
    echo_blue "========================================"
    echo_blue "Test Summary"
    echo_blue "========================================"
    echo ""

    # Backend results
    if [ -f backend-test-output.log ]; then
        if grep -q "BUILD SUCCESS" backend-test-output.log && ! grep -q "Failed tests" backend-test-output.log; then
            echo_green "✅ Backend Tests: PASSED"
        else
            echo_red "❌ Backend Tests: FAILED"
            grep -A 5 "Tests run:" backend-test-output.log | head -20 || echo "Check backend-test-output.log"
        fi
    fi

    # Frontend results
    if [ -f frontend-test-output.log ]; then
        if grep -q "passed" frontend-test-output.log && ! grep -q "failed" frontend-test-output.log; then
            echo_green "✅ Frontend Tests: PASSED"
        else
            echo_red "❌ Frontend Tests: FAILED"
            grep -A 3 "failed" frontend-test-output.log || echo "Check frontend-test-output.log"
        fi
    fi

    echo ""
    echo_yellow "Full logs available:"
    echo "- Backend: $SCRIPT_DIR/backend-test-output.log"
    echo "- Frontend: $SCRIPT_DIR/frontend-test-output.log"
    echo "- HTML Report: $SCRIPT_DIR/playwright-report/index.html"
    echo ""
}

# Main execution
check_dependencies
run_backend_tests
BACKEND_EXIT=$?

echo ""
echo ""
run_frontend_tests
FRONTEND_EXIT=$?

echo ""
echo ""
show_summary

echo ""

# Exit with appropriate code
if [ $BACKEND_EXIT -eq 0 ] && [ $FRONTEND_EXIT -eq 0 ]; then
    echo_green "========================================"
    echo_green "🎉 ALL TESTS PASSED - Code is Ready!"
    echo_green "========================================"
    exit 0
else
    echo_red "========================================"
    echo_red "❌ SOME TESTS FAILED - Review Required"
    echo_red "========================================"
    exit 1
fi
