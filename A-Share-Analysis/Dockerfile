# 第一阶段：利用 Maven 镜像在云端完成代码编译和打包
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
# 复制项目配置文件和源码
COPY pom.xml .
COPY src ./src
# 执行 Maven 打包命令，跳过测试以加快速度
RUN mvn clean package -DskipTests

# 第二阶段：构建极简的 Java 运行环境（只保留最终的 jar 包，让镜像体积暴减）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 从第一阶段的成果物里，把打包好的 jar 包复制过来
COPY --from=build /app/target/*.jar app.jar

# 暴露端口（根据你 Java 项目的实际端口修改，比如 Spring Boot 默认是 8080）
EXPOSE 8080

# 启动 Java 程序
CMD ["java", "-jar", "app.jar"]
