FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/cashier-api-1.0.0.jar app.jar
EXPOSE 3001
# Render free tier is 512 MB; let the JVM size its heap from the container limit.
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
