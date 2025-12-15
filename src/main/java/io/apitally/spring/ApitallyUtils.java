package io.apitally.spring;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.apitally.common.dto.Path;

final public class ApitallyUtils {
    public static List<Path> getPaths(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> {
                    RequestMappingInfo mappingInfo = entry.getKey();
                    return mappingInfo.getMethodsCondition().getMethods().stream()
                            .filter(method -> method != RequestMethod.OPTIONS && method != RequestMethod.HEAD)
                            .flatMap(method -> {
                                PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
                                if (pathPatterns != null && pathPatterns.getPatterns() != null) {
                                    return pathPatterns.getPatterns().stream()
                                            .map(pattern -> new Path(method.name(), pattern.getPatternString()));
                                }
                                PatternsRequestCondition patterns = mappingInfo.getPatternsCondition();
                                if (patterns != null && patterns.getPatterns() != null) {
                                    return patterns.getPatterns().stream()
                                            .map(pattern -> new Path(method.name(), pattern));
                                }
                                return List.<Path>of().stream();
                            });
                })
                .collect(Collectors.toList());
    }

    public static Map<String, String> getVersions() {
        return Map.of(
                "java", System.getProperty("java.version"),
                "spring", SpringVersion.getVersion(),
                "spring-boot", SpringBootVersion.getVersion());
    }

}
