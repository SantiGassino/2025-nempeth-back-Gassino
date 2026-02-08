# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests clean package

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# App Platform sets PORT; Spring will read it from application.properties using PORT fallback
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
