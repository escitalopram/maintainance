package com.maintainance.config;

import com.maintainance.domain.scheduling.DueFunctionEvaluator;
import com.maintainance.domain.scheduling.SchedulingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    SchedulingService schedulingService(DueFunctionEvaluator dueFunctionEvaluator) {
        return new SchedulingService(dueFunctionEvaluator);
    }
}
