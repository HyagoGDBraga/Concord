# syntax=docker/dockerfile:1
# =============================================================================
# Concord — backend
# Estagios: deps -> dev            (desenvolvimento, com hot reload)
#           deps -> builder -> runtime  (producao, JRE enxuta)
# =============================================================================

FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
# Baixa as dependencias em uma camada propria: mudanca no codigo-fonte nao
# invalida o cache do Maven.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# --- desenvolvimento ---------------------------------------------------------
FROM deps AS dev
WORKDIR /build
# curl e usado pelo healthcheck do docker-compose.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
COPY . .
EXPOSE 8080
# O DevTools observa /build/target/classes; o spring-boot:run recompila quando
# os fontes montados por volume mudam.
#
# -Dmaven.test.skip pula a COMPILACAO dos testes, nao apenas a execucao.
# Sem isso, o spring-boot:run dispara a fase test-compile e um teste que nao
# compila impede a aplicacao de subir — o container entra em loop de restart
# por um problema que nao e da aplicacao. Os testes rodam explicitamente:
#     docker compose exec backend mvn test
CMD ["mvn", "-B", "spring-boot:run", "-Dspring-boot.run.profiles=dev", "-Dmaven.test.skip=true"]

# --- build de producao -------------------------------------------------------
FROM deps AS builder
WORKDIR /build
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- runtime de producao -----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# Nunca rodar como root.
RUN addgroup -S concord && adduser -S concord -G concord
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R concord:concord /app
USER concord
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
