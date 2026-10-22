package com.waleed.naseej;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class NaseejApplication {

    public static void main(String[] args) {
        Dotenv dotenv = loadDotenv();
        Map<String, Object> defaultsFromEnvFile = new HashMap<>();
        for (DotenvEntry entry : dotenv.entries()) {
            String key = entry.getKey();
            String value = normalizeEnvValue(entry.getValue());
            defaultsFromEnvFile.put(key, value);
            if (System.getenv(key) == null) {
                System.setProperty(key, value);
            }
        }

        SpringApplication application = new SpringApplication(NaseejApplication.class);
        application.setDefaultProperties(defaultsFromEnvFile);
        application.run(args);
    }

    private static Dotenv loadDotenv() {
        Path search = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 6 && search != null; depth++) {
            Path envFile = search.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                return Dotenv.configure()
                        .directory(search.toString())
                        .ignoreIfMissing()
                        .load();
            }
            search = search.getParent();
        }
        return Dotenv.configure().ignoreIfMissing().load();
    }

    private static String normalizeEnvValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
