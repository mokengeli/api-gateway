# Étape de build
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Copie et installation des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copie du code source et compilation
COPY src ./src
RUN mvn package -DskipTests

# Extraction dynamique de la version
RUN APP_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout) \
    && echo "Version extraite: $APP_VERSION" \
    && cp target/api-gateway-$APP_VERSION.jar target/app.jar

# Étape finale
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Création d'un utilisateur non-root
RUN addgroup --system appuser && adduser --system --ingroup appuser appuser
USER appuser:appuser

# Copie du JAR compilé
COPY --from=build /app/target/app.jar ./app.jar

# Copie de tous les fichiers de configuration
COPY src/main/resources/application*.yml ./config/

# Exposer le port
EXPOSE 8081

# Variables d'environnement REQUISES
ENV SERVER_PORT="" \
    JWT_SECRET="" \
    ALLOWED_ORIGINS="" \
    MOBILE_PATTERNS="" \
    EUREKA_SERVER_URL=""

# Variables d'environnement OPTIONNELLES
ENV TIME_ZONE="GMT+01:00" \
    SPRING_PROFILES_ACTIVE="dev" \
    SESSION_CACHE_TTL="120" \
    CLOUD_GATEWAY_LOG_LEVEL="INFO" \
    PUBLIC_PATHS="public/**, /api/auth/login" \
    INVENTORY_SERVICE_URL="lb://inventory-service" \
    USER_SERVICE_URL="lb://user-service" \
    AUTH_SERVICE_URL="lb://authentication-service" \
    ORDER_SERVICE_URL="lb://order-service"

# Point d'entrée
ENTRYPOINT ["java", "-jar", "app.jar","--spring.profiles.active=${SPRING_PROFILES_ACTIVE}",\
            "--spring.config.location=file:./config/"]

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1