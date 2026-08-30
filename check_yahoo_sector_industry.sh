#!/bin/bash
# Query Yahoo Finance search API for sector/industry data
# Usage: ./check_yahoo_sector_industry.sh

SYMBOLS=$(mysql -u root -proot stockmarket -e "SELECT symbol FROM stocks ORDER BY symbol" -B -N)
TOTAL=0
HAS_SECTOR=0
HAS_INDUSTRY=0
MISSING_BOTH=0
MISSING_SECTOR=0
MISSING_INDUSTRY=0
MISSING_SYMBOLS=""

echo "symbol|sector|industry"

for sym in $SYMBOLS; do
    TOTAL=$((TOTAL + 1))
    
    # Build URL
    url="https://query1.finance.yahoo.com/v1/finance/search?q=${sym}&quotesCount=1&newsCount=0&enableFuzzyQuery=false&quotesQueryId=tss_match_phrase_query"
    
    # Query with User-Agent
    resp=$(curl -s -H "User-Agent: Mozilla/5.0" "$url")
    
    # Extract first quote's sector and industry using jq
    sector=$(echo "$resp" | jq -r '.quotes[0].sector // empty' 2>/dev/null)
    industry=$(echo "$resp" | jq -r '.quotes[0].industry // empty' 2>/dev/null)
    
    # Handle empty strings
    [ -z "$sector" ] && sector="NULL"
    [ -z "$industry" ] && industry="NULL"
    
    echo "${sym}|${sector}|${industry}"
    
    # Count
    if [ "$sector" != "NULL" ]; then
        HAS_SECTOR=$((HAS_SECTOR + 1))
    fi
    if [ "$industry" != "NULL" ]; then
        HAS_INDUSTRY=$((HAS_INDUSTRY + 1))
    fi
    
    # Track missing
    if [ "$sector" = "NULL" ] && [ "$industry" = "NULL" ]; then
        MISSING_BOTH=$((MISSING_BOTH + 1))
        MISSING_SYMBOLS="${MISSING_SYMBOLS}${sym} "
    elif [ "$sector" = "NULL" ]; then
        MISSING_SECTOR=$((MISSING_SECTOR + 1))
        MISSING_SYMBOLS="${MISSING_SYMBOLS}${sym}(no-sector) "
    elif [ "$industry" = "NULL" ]; then
        MISSING_INDUSTRY=$((MISSING_INDUSTRY + 1))
        MISSING_SYMBOLS="${MISSING_SYMBOLS}${sym}(no-industry) "
    fi
    
    # Rate limiting - small delay
    sleep 0.25
done

echo ""
echo "=== SUMMARY ==="
echo "Total symbols checked: $TOTAL"
echo "Has sector: $HAS_SECTOR"
echo "Has industry: $HAS_INDUSTRY"
echo "Missing both sector+industry: $MISSING_BOTH"
echo "Missing sector only: $MISSING_SECTOR"
echo "Missing industry only: $MISSING_INDUSTRY"
echo ""
if [ -n "$MISSING_SYMBOLS" ]; then
    echo "Symbols with missing data:"
    for s in $MISSING_SYMBOLS; do
        echo "  $s"
    done
fi
