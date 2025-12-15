package io.apitally.spring;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Enables Apitally integration for a Spring Boot application.
 * Registers a filter that captures requests and responses, and creates an
 * ApitallyClient instance that handles background synchronization with
 * Apitally.
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Import({ ApitallyAutoConfiguration.class })
public @interface UseApitally {
    Class<? extends Annotation> annotation() default Annotation.class;
}
