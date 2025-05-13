# Étape de build
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Copie et installation des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copie du code source et compilation
COPY src ./src
RUN mvn package -DskipTests

# Extraction dynamique de la version du POM
RUN APP_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout) \
    && echo "Version extraite: $APP_VERSION" \
    && cp target/api-gateway-$APP_VERSION.jar target/app.jar

# Étape finale
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Création d'un utilisateur non-root
RUN addgroup --system javauser && adduser --system --ingroup javauser javauser
USER javauser:javauser

# Copie du JAR compilé (avec nom générique)
COPY --from=build /app/target/app.jar ./app.jar

# Exposer le port
EXPOSE 8080

# Variables d'environnement
ENV SERVER_PORT="" \
    JWT_SECRET="" \
    ALLOWED_ORIGINS="" \
    EUREKA_SERVER_URL="" \
    TIME_ZONE="GMT+01:00" \
    SESSION_CACHE_TTL="120" \
    CLOUD_GATEWAY_LOG_LEVEL="INFO" \
    PUBLIC_PATHS="public/**, /api/auth/login" \
    INVENTORY_SERVICE_URL="lb://inventory-service" \
    USER_SERVICE_URL="lb://user-service" \
    AUTH_SERVICE_URL="lb://auth-service" \
    ORDER_SERVICE_URL="lb://order-service"

# Point d'entrée simplifié
ENTRYPOINT ["java", "-jar", "app.jar"]

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1