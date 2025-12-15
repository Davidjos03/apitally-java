package io.apitally.spring;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.apitally.common.ApitallyClient;
import io.apitally.common.dto.Path;

@Configuration
@EnableConfigurationProperties(ApitallyProperties.class)
public class ApitallyAutoConfiguration {
    @Bean
    public ApitallyClient apitallyClient(ApitallyProperties properties,
            RequestMappingHandlerMapping requestMappingHandlerMapping) {
        ApitallyClient client = new ApitallyClient(properties.getClientId(), properties.getEnv(),
                properties.getRequestLogging());
        List<Path> paths = ApitallyUtils.getPaths(requestMappingHandlerMapping);
        Map<String, String> versions = ApitallyUtils.getVersions();
        client.setStartupData(paths, versions, "java:spring");
        client.startSync();
        return client;
    }

    @Bean
    public FilterRegistrationBean<ApitallyFilter> apitallyFilterRegistration(ApitallyClient apitallyClient) {
        final FilterRegistrationBean<ApitallyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ApitallyFilter(apitallyClient));
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registrationBean;
    }

    @Bean
    public ApitallyExceptionResolver apitallyExceptionResolver() {
        return new ApitallyExceptionResolver();
    }
}
