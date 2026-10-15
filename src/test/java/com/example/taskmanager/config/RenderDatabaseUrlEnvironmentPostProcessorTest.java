package com.example.taskmanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderDatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convertsRenderUrlToJdbcPropertiesAndDecodesCredentials() {
        Map<String, Object> properties = RenderDatabaseUrlEnvironmentPostProcessor.jdbcPropertiesFrom(
                "postgresql://task%40manager:p%40ss%3Aword@db.render.com:5432/task_manager"
        );

        assertThat(properties)
                .containsEntry("spring.datasource.url",
                        "jdbc:postgresql://db.render.com:5432/task_manager?sslmode=require")
                .containsEntry("spring.datasource.username", "task@manager")
                .containsEntry("spring.datasource.password", "p@ss:word")
                .containsEntry("spring.datasource.driver-class-name", "org.postgresql.Driver");
    }

    @Test
    void rejectsMalformedDatabaseUrlWithoutEchoingCredentials() {
        assertThatThrownBy(() -> RenderDatabaseUrlEnvironmentPostProcessor.jdbcPropertiesFrom(
                "mysql://private-user:private-password@db.example.com/app"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-password");
    }
}
