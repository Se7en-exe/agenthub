package com.lws.agenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(exclude = {
        // 为了便于大家开发调试和部署，取消数据库自动配置，需要使用 PgVector 时把 DataSourceAutoConfiguration.class 删除
        DataSourceAutoConfiguration.class
})
public class AgentHubApplication {

    public static void main(String[] args) {
        prepareDirectories();
        SpringApplication.run(AgentHubApplication.class, args);
    }

    /**
     * 启动前把要用的目录准备好。
     * <p>
     * Tomcat 只在 java.io.tmpdir 已经存在时才肯建临时目录，否则启动阶段直接抛
     * "Unable to create tempDir"，容器起不来。刚克隆下来、还没跑过任何命令的机器上
     * 这个目录通常不存在，所以在这里补齐，省得用户先去手建。
     */
    private static void prepareDirectories() {
        createQuietly(System.getProperty("user.dir") + "/tmp", "文件保存目录");
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir != null && !tmpdir.isBlank()) {
            createQuietly(tmpdir, "java.io.tmpdir");
        }
    }

    private static void createQuietly(String dir, String description) {
        try {
            Path path = Paths.get(dir).toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            // 建不出来就交给后面的组件去报错，这里不吞掉真正的启动异常
            System.err.println("无法创建" + description + ": " + dir + " (" + e.getMessage() + ")");
        }
    }
}
