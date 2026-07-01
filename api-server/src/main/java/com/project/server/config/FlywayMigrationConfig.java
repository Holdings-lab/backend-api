package com.project.server.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway가 JPA(Hibernate) 스키마 생성 이후에 실행되도록 순서를 조정합니다.
 * broker_accounts 등이 users FK를 참조할 수 있게 합니다.
 */
@Configuration
public class FlywayMigrationConfig {

    @Bean
    public static BeanFactoryPostProcessor flywayAfterJpa() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("flywayInitializer")) {
                BeanDefinition definition = beanFactory.getBeanDefinition("flywayInitializer");
                definition.setDependsOn("entityManagerFactory");
            }
        };
    }
}
