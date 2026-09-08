# ---- 构建阶段 ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests -Denforcer.skip=true

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/queqiao-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
