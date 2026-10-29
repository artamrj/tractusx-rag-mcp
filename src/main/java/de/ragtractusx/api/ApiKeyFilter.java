package de.ragtractusx.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import de.ragtractusx.config.RagProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Minimal shared-secret protection:
 * <ul>
 * <li>/api/** and /mcp require {@code X-Api-Key} when {@code RAG_API_KEY} is set (open otherwise, for local use).</li>
 * <li>/admin/** requires {@code X-Admin-Token}; without a configured {@code ADMIN_TOKEN} admin endpoints are off.</li>
 * </ul>
 * Actuator endpoints stay open for probes and Prometheus; restrict them at the network level.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

    private final RagProperties.Security security;

    public ApiKeyFilter(RagProperties props) {
        this.security = props.security();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/admin")) {
            if (!StringUtils.hasText(security.adminToken())
                    || !matches(security.adminToken(), request.getHeader("X-Admin-Token"))) {
                reject(response, "missing or invalid X-Admin-Token");
                return;
            }
        } else if ((path.startsWith("/api/") || path.startsWith("/mcp")) && StringUtils.hasText(security.apiKey())
                && !matches(security.apiKey(), request.getHeader("X-Api-Key"))) {
            reject(response, "missing or invalid X-Api-Key");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + detail + "\"}");
    }
}
