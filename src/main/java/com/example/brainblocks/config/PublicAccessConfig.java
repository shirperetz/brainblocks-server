package com.example.brainblocks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-11, Shir: "a link to the math game I can use on other computers" - published with
 * Tailscale Funnel (her choice). The server listens on 127.0.0.1 only; Funnel is the one door in.
 * <ul>
 * <li>The built learn-client (learn-client/dist) is served from here, so no dev server is public;
 * any non-file path falls back to index.html (React routes like /topics).</li>
 * <li>Limits per visitor (the real address Funnel passes in X-Forwarded-For - trusted only from
 * loopback): sign-ups, log-ins, the local AI (help / speech) and requests overall.</li>
 * </ul>
 */
@Configuration
public class PublicAccessConfig implements WebMvcConfigurer {

    private final String clientDist;

    public PublicAccessConfig(@Value("${brainblocks.client-dist:../learn-client/dist/}") String clientDist) {
        this.clientDist = clientDist.endsWith("/") ? clientDist : clientDist + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + clientDist)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.startsWith("api/")) {
                            return null;
                        }
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        // A React route (no dot = not a file) gets the app itself.
                        return path.contains(".") ? null : new FileSystemResource(clientDist + "index.html");
                    }
                });
    }

    /** "/" itself never reaches the resource handler - send it to the app. */
    @Override
    public void addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /** Window-limited counters: at most {@code max} events per key in {@code windowMs}. */
    static final class Limiter {
        private final int max;
        private final long windowMs;
        private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

        Limiter(int max, long windowMs) {
            this.max = max;
            this.windowMs = windowMs;
        }

        boolean allow(String key, long now) {
            Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
                    q.pollFirst();
                }
                if (q.size() >= max) {
                    return false;
                }
                q.addLast(now);
                return true;
            }
        }

        void cleanup(long now) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || now - e.getValue().peekLast() >= windowMs;
                }
            });
        }
    }

    static final long MINUTE = 60_000;

    /** The visitor's address: Funnel's X-Forwarded-For only when the request came over loopback. */
    static String visitor(HttpServletRequest r) {
        String remote = r.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        String fwd = r.getHeader("X-Forwarded-For");
        if (loopback && fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return remote;
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> publicLimits() {
        Limiter register = new Limiter(5, 60 * MINUTE);   // new children per address per hour
        Limiter login = new Limiter(20, 10 * MINUTE);     // log-in tries per address
        Limiter ai = new Limiter(20, MINUTE);              // "לא הבנתי" / speech (local AI) per address
        Limiter all = new Limiter(600, MINUTE);            // every /api call per address
        Clock clock = Clock.systemUTC();
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            private long lastCleanup;

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String path = req.getRequestURI();
                if (path.startsWith("/api/")) {
                    long now = clock.millis();
                    if (now - lastCleanup > 10 * MINUTE) {
                        lastCleanup = now;
                        for (Limiter l : new Limiter[] {register, login, ai, all}) {
                            l.cleanup(now);
                        }
                    }
                    boolean post = "POST".equals(req.getMethod());
                    String who = visitor(req);
                    if (post && path.equals("/api/learn/auth/register")) {
                        // who tried to sign up, and from where (Shir: "nobody gets in without me knowing")
                        org.slf4j.LoggerFactory.getLogger("brainblocks.signup").info("sign-up attempt from {}", who);
                    }
                    boolean ok = all.allow(who, now)
                            && (!post || !path.equals("/api/learn/auth/register") || register.allow(who, now))
                            && (!post || !path.equals("/api/learn/auth/login") || login.allow(who, now))
                            && (!post || !(path.equals("/api/learn/help") || path.equals("/api/learn/speak")) || ai.allow(who, now));
                    if (!ok) {
                        res.setStatus(429);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"message\":\"יותר מדי בקשות. נסו שוב בעוד כמה דקות\"}");
                        return;
                    }
                }
                res.setHeader("X-Content-Type-Options", "nosniff");
                res.setHeader("Referrer-Policy", "no-referrer");
                res.setHeader("X-Frame-Options", "DENY");
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(0);
        return bean;
    }
}
