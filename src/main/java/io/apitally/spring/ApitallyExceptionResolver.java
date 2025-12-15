package io.apitally.spring;

import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApitallyExceptionResolver implements HandlerExceptionResolver, Ordered {
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public @Nullable ModelAndView resolveException(
            final @NonNull HttpServletRequest request,
            final @NonNull HttpServletResponse response,
            final @Nullable Object handler,
            final @NonNull Exception ex) {
        request.setAttribute("apitallyCapturedException", ex);
        return null;
    }
}
