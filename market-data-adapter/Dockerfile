FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# All module directories must be present so Maven can read the parent
# reactor POM without failing on missing child module paths.
COPY pom.xml                  ./pom.xml
COPY common                   ./common
COPY market-data-adapter      ./market-data-adapter
COPY candle-api               ./candle-api
COPY logger-service           ./logger-service

RUN mvn install -pl common -am -q -DskipTests
RUN mvn package -pl market-data-adapter -q -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/market-data-adapter/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
