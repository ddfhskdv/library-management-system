FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S library && adduser -S library -G library
COPY --from=build --chown=library:library /workspace/target/library-management-1.0.0.jar /app/app.jar
USER library
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
