# Pricemeter у контейнері: збирання Maven Wrapper-ом (кеш залежностей між збираннями), запуск на JRE.
# Збирається з кореня репозиторію: docker compose -f docker/docker-compose.yml --profile app up -d --build

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home pricemeter
COPY --from=build /src/target/pricemeter-*.jar app.jar
USER pricemeter
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
