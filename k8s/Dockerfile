# 通用 Spring Boot 服务镜像
# 构建上下文为仓库根。JAR_SRC 指定 jar 路径（默认本地 target/，CI 可覆盖为平铺根目录）
# 用法：docker build --build-arg SERVICE=order -f k8s/Dockerfile .
ARG SERVICE=order
FROM eclipse-temurin:17-jre
ARG SERVICE
# CI：jar 平铺在构建上下文根 exchange-<svc>-1.0.0.jar；本地：exchange-<svc>/target/exchange-<svc>-1.0.0.jar
ARG JAR_SRC=exchange-${SERVICE}/target/exchange-${SERVICE}-1.0.0.jar
RUN mkdir -p /app
COPY ${JAR_SRC} /app/app.jar
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
