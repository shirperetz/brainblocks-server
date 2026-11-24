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

    /** 2026-11-24: the largest request body /api accepts (the biggest real one - a round of 10 answers - is ~3 KB). */
    static final long MAX_BODY = 100_000;


    private final String clientDist;
    /** On the public host (profile "cloud") every request comes through its proxy: trust X-Forwarded-For. */
    private final boolean trustForwarded;

    public PublicAccessConfig(@Value("${brainblocks.client-dist:../learn-client/dist/}") String clientDist,
                              @Value("${brainblocks.trust-forwarded:false}") boolean trustForwarded) {
        this.clientDist = clientDist.endsWith("/") ? clientDist : clientDist + "/";
        this.trustForwarded = trustForwarded;
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

    /**
     * Only the site's own scripts; inline styles (React style attributes) and the Rubik font from Google
     * Fonts; audio/images from the site, data: and blob: (the read-aloud WAVs are object URLs).
     */
    static final String CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; "
            + "img-src 'self' data: blob:; media-src 'self' blob: data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /** The visitor's address: Funnel's X-Forwarded-For only when the request came over loopback. */
    static String visitor(HttpServletRequest r) {
        return visitor(r, false);
    }

    static String visitor(HttpServletRequest r, boolean trustForwarded) {
        String remote = r.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        String fwd = r.getHeader("X-Forwarded-For");
        if ((loopback || trustForwarded) && fwd != null && !fwd.isBlank()) {
            // 2026-11-13: the LAST address is the one our proxy added; earlier ones are whatever the
            // visitor sent (Funnel overwrites today - checked live - but never trust a visitor's list).
            String[] parts = fwd.split(",");
            return parts[parts.length - 1].trim();
        }
        return remote;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.brainblocks.learning.service.PhoneAlerts alerts;

    @org.springframework.beans.factory.annotation.Value("${brainblocks.rounds-per-minute:6}")
    private int roundsPerMinute = 6;

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> publicLimits() {
        // 2026-11-13 (Shir chose): a whole class signs up from ONE school address - 5 an hour stopped it after
        // 5 children. 40 an hour fits a class; every new account still waits for the admin / its teacher.
        Limiter register = new Limiter(40, 60 * MINUTE);  // new accounts per address per hour
        Limiter contact = new Limiter(5, 60 * MINUTE);    // 2026-11-14: messages to the site's operator per address per hour
        // 2026-11-12 (the live game room): a whole class logs in from ONE school address at the start of a
        // lesson - 20 per 10 minutes would lock children out. Guessing passwords is still stopped per
        // user (5 wrong -> 5 minutes, AccountService).
        Limiter login = new Limiter(60, 10 * MINUTE);     // log-in tries per address
        Limiter ai = new Limiter(20, MINUTE);              // "לא הבנתי" per child (logged in) or per address
        // 2026-11-21 (security pass): a class behind one school address shared ONE "לא הבנתי" allowance and
        // one 600/min - now each child has its own, with a roomy cap per address for logged-in children.
        Limiter aiSchool = new Limiter(300, MINUTE);
        Limiter allLoggedIn = new Limiter(3000, MINUTE);
        // 2026-11-12: 🔊 reading every question aloud - a whole class behind one school address would pass
        // 20/min at once; repeats come from SpeechService's memory, so it gets its own, bigger allowance.
        Limiter speech = new Limiter(120, MINUTE);
        Limiter all = new Limiter(600, MINUTE);            // every /api call per address
        // A room polls every 2 s per child (30 children = 900/min from one school address): its own allowance.
        Limiter room = new Limiter(4000, MINUTE);
        Limiter guestJoin = new Limiter(40, 10 * MINUTE);  // guests joining rooms, per address
        // 2026-11-13 (Shir: "protection against scripts answering in a loop to collect stars"): per LOGIN,
        // so many children behind one school address don't block each other. A real child can't finish
        // more than ~6 rounds a minute or answer more than one question a second.
        Limiter rounds = new Limiter(roundsPerMinute, MINUTE); // rounds submitted per child (default 6)
        Limiter answers = new Limiter(60, MINUTE);         // single answers checked per child
        Limiter perChild = new Limiter(400, MINUTE);       // every call per child
        Clock clock = Clock.systemUTC();
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            private long lastCleanup;

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String path = req.getRequestURI();
                // 2026-11-24 (security review): a 3 MB JSON body was read whole before being refused - on a
                // 512 MB server a few of those at once could take it down. Every real request is a few KB.
                if (path.startsWith("/api/") && !"GET".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod())
                        && req.getContentLengthLong() > MAX_BODY) {
                    res.setStatus(413);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"הבקשה גדולה מדי\"}");
                    return;
                }
                // a body sent without a length (chunked) is counted as it is read and cut at the same limit
                if (path.startsWith("/api/") && req.getContentLengthLong() < 0 && !"GET".equals(req.getMethod())) {
                    req = new LimitedBody(req, MAX_BODY);
                }
                if (path.startsWith("/api/")) {
                    long now = clock.millis();
                    if (now - lastCleanup > 10 * MINUTE) {
                        lastCleanup = now;
                        for (Limiter l : new Limiter[] {register, contact, login, ai, aiSchool, allLoggedIn, speech, all, room, guestJoin, rounds, answers, perChild}) {
                            l.cleanup(now);
                        }
                    }
                    boolean post = "POST".equals(req.getMethod());
                    String who = visitor(req, trustForwarded);
                    if (post && path.startsWith("/api/learn/auth/register")) { // children and (2026-11-12) teachers
                        // who tried to sign up, and from where (Shir: "nobody gets in without me knowing")
                        org.slf4j.LoggerFactory.getLogger("brainblocks.signup").info("sign-up attempt from {}", who);
                    }
                    boolean inRoom = path.startsWith("/api/learn/rooms/") || path.startsWith("/api/learn/room-guest/");
                    String token = req.getHeader("X-Learner-Token");
                    boolean loggedIn = token != null && !token.isBlank();
                    String child = loggedIn ? "t:" + Integer.toHexString(token.hashCode()) + token.length() : null;
                    boolean help = post && path.equals("/api/learn/help");
                    boolean ok = (inRoom ? room.allow(who, now) : loggedIn ? allLoggedIn.allow(who, now) : all.allow(who, now))
                            && (!post || !path.equals("/api/learn/room-guest/join") || guestJoin.allow(who, now))
                            && (!post || !path.startsWith("/api/learn/auth/register") || register.allow(who, now))
                            && (!post || !path.equals("/api/learn/auth/login") || login.allow(who, now))
                            && (!post || !path.equals("/api/learn/auth/contact") || contact.allow(who, now))
                            && (!help || (loggedIn ? aiSchool.allow(who, now) && ai.allow(child, now) : ai.allow(who, now)))
                            && (!post || !path.equals("/api/learn/speak") || speech.allow(who, now));
                    if (ok && loggedIn) {
                        ok = perChild.allow(child, now)
                                && (!post || !path.endsWith("/sessions") || rounds.allow(child, now))
                                && (!post || !path.endsWith("/answer") || answers.allow(child, now));
                    }
                    if (!ok) {
                        if (alerts != null) alerts.send("rate-" + who, "הרבה מאוד בקשות מכתובת " + who + " - הוגבלה לכמה דקות (" + path + ")");
                        res.setStatus(429);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"message\":\"יותר מדי בקשות. נסו שוב בעוד כמה דקות\"}");
                        return;
                    }
                }
                res.setHeader("X-Content-Type-Options", "nosniff");
                res.setHeader("Referrer-Policy", "no-referrer");
                res.setHeader("X-Frame-Options", "DENY");
                // 2026-11-13 (Shir: "security so nobody can break in"): even if hostile text ever reached a
                // page, the browser runs only this site's own scripts; nothing can frame it; HTTPS only.
                res.setHeader("Content-Security-Policy", CSP);
                res.setHeader("Strict-Transport-Security", "max-age=31536000");
                res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
                res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(0);
        return bean;
    }

    /** 2026-11-24: reading past [max] bytes of a body with no declared length fails the request. */
    static final class LimitedBody extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final long max;

        LimitedBody(HttpServletRequest req, long max) {
            super(req);
            this.max = max;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            jakarta.servlet.ServletInputStream in = super.getInputStream();
            return new jakarta.servlet.ServletInputStream() {
                private long read;

                private void count(long n) throws IOException {
                    if (n > 0 && (read += n) > max) throw new IOException("request body too large");
                }

                @Override public int read() throws IOException { int b = in.read(); if (b >= 0) count(1); return b; }
                @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); count(n); return n; }
                @Override public boolean isFinished() { return in.isFinished(); }
                @Override public boolean isReady() { return in.isReady(); }
                @Override public void setReadListener(jakarta.servlet.ReadListener l) { in.setReadListener(l); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
