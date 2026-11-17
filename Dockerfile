# ---------- 构建阶段 ----------
# 前端构建产物已提交在 src/main/resources/static/ 里，
# 所以镜像内不需要再跑一次 npm，直接用 Maven 打包后端即可
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom，让依赖层可以被缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区与编码：中文日志和 cmd 输出都依赖这两项
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

COPY --from=build /build/target/agenthub-0.0.1-SNAPSHOT.jar app.jar

# 临时目录、对话记忆、上传的知识库都写在这里，挂卷才能持久化
RUN mkdir -p /app/tmp
VOLUME ["/app/tmp"]

EXPOSE 8123

# API Key 走环境变量或页面「模型设置」，不要写进镜像
ENTRYPOINT ["java", "-Djava.io.tmpdir=/app/tmp", "-jar", "/app/app.jar"]
