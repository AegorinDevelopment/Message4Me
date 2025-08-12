FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/message4me-1.0-SNAPSHOT.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]
