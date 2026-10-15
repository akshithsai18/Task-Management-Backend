package com.example.taskmanager.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class RenderDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("DB_URL"))) {
            return;
        }

        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl)) {
            return;
        }

        Map<String, Object> jdbcProperties = jdbcPropertiesFrom(databaseUrl);
        environment.getPropertySources().addFirst(
                new MapPropertySource("renderPostgresDatabaseUrl", jdbcProperties)
        );
    }

    static Map<String, Object> jdbcPropertiesFrom(String databaseUrl) {
        try {
            URI uri = URI.create(databaseUrl);
            String scheme = uri.getScheme();
            if (!"postgresql".equalsIgnoreCase(scheme) && !"postgres".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException();
            }

            String rawUserInfo = uri.getRawUserInfo();
            int separator = rawUserInfo == null ? -1 : rawUserInfo.indexOf(':');
            String rawPath = uri.getRawPath();
            if (uri.getHost() == null || separator < 1 || rawPath == null || rawPath.length() < 2) {
                throw new IllegalArgumentException();
            }

            String username = decode(rawUserInfo.substring(0, separator));
            String password = decode(rawUserInfo.substring(separator + 1));
            String database = decode(rawPath.substring(1));
            String host = uri.getHost();
            if (host.contains(":")) {
                host = "[" + host + "]";
            }
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=require";

            return Map.of(
                    "spring.datasource.url", jdbcUrl,
                    "spring.datasource.username", username,
                    "spring.datasource.password", password,
                    "spring.datasource.driver-class-name", "org.postgresql.Driver"
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "DATABASE_URL must be a valid PostgreSQL connection URL with credentials and a database name."
            );
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
