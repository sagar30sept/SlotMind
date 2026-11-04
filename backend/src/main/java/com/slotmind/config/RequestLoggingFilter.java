package com.slotmind.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/h2-console");
    }

    /** Logs method, path, status, and elapsed time for each API request. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            log.error("{} {} failed", request.getMethod(), request.getRequestURI(), exception);
            throw exception;
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else if (!"GET".equals(request.getMethod())) {
                log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else {
                log.debug("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, elapsedMs);
            }
        }
    }
}
