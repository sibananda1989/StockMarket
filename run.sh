#!/bin/bash
cd /Users/sibanandasahoo/Documents/projects/stockmarket

# Build the app if the JAR doesn't exist
if [ ! -f target/stockmarket-1.0-SNAPSHOT.jar ]; then
    echo "Building the application..."
    mvn package -DskipTests -q
fi

# Start the Spring Boot backend with DevTools restart disabled
# (DevTools restart mechanism triggers JVM shutdown when running from a JAR)
echo "Starting StockTrackerApplication..."
java -Dspring.profiles.active=dev -Dspring.devtools.restart.enabled=false \
    -jar target/stockmarket-1.0-SNAPSHOT.jar 2>&1
