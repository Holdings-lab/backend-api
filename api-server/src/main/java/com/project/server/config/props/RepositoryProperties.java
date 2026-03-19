package com.project.server.config.props;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "repository")
@Getter @Setter
public class RepositoryProperties {
    private String eventsFile;
    private String usersFile;
}
