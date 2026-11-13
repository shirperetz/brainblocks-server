package com.example.brainblocks.config;

import com.example.brainblocks.learning.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Every /api/learn call except sign-up / log-in needs the device token of a logged-in child
 * (header {@value #HEADER}) - 2026-11-11, children's accounts. The controller then checks that
 * the profile a call touches is that child's own ({@link #ATTRIBUTE}).
 */
@Configuration
public class LearnerAuthConfig implements WebMvcConfigurer {

    public static final String HEADER = "X-Learner-Token";
    public static final String ATTRIBUTE = "learnerId";

    private final AccountService accounts;

    public LearnerAuthConfig(AccountService accounts) {
        this.accounts = accounts;
    }

    @org.springframework.beans.factory.annotation.Value("${brainblocks.admin-network:log}")
    private String adminNetwork = "log";
    private volatile String lastAdminFrom = "";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.brainblocks.learning.service.PhoneAlerts alerts;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.brainblocks.learning.service.AuditService audit;

    /** This computer, or a Tailscale address (100.64.0.0/10, fd7a:115c:a1e0::/48). */
    static boolean ownDevice(String ip) {
        if (ip == null) return false;
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) return true;
        if (ip.toLowerCase().startsWith("fd7a:115c:a1e0:")) return true;
        String[] p = ip.split("\\.");
        if (p.length != 4) return false;
        try {
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            return a == 100 && b >= 64 && b <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                var id = accounts.profileFor(request.getHeader(HEADER));
                if (id.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return false;
                }
                request.setAttribute(ATTRIBUTE, id.get());
                // 2026-11-13 (Shir: security #1): the admin account only from her own devices.
                // mode "log" = only write where it came from; "on" = refuse from anywhere else.
                if (!"off".equals(adminNetwork) && accounts.isAdmin(id.get())) {
                    String from = PublicAccessConfig.visitor(request, false);
                    if (!ownDevice(from)) {
                        org.slf4j.LoggerFactory.getLogger("brainblocks.admin").warn("admin request from outside her devices: {} {} (mode {})",
                                from, request.getRequestURI(), adminNetwork);
                        if (alerts != null) alerts.send("admin-outside", "ניסיון להשתמש בחשבון המנהלת מכתובת שאינה שלך: " + from
                                + ("on".equals(adminNetwork) ? " - נחסם" : ""));
                        if ("on".equals(adminNetwork)) {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            return false;
                        }
                    } else if (!from.equals(lastAdminFrom)) {
                        lastAdminFrom = from;
                        org.slf4j.LoggerFactory.getLogger("brainblocks.admin").info("admin request from her device: {}", from);
                    }
                }
                return true;
            }
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                // 2026-11-13 (Shir: security - "a log of who did what"): every change made through the
                // staff / admin screens that succeeded.
                String path = request.getRequestURI();
                if (audit == null || "GET".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) return;
                if (!(path.startsWith("/api/learn/staff/") || path.startsWith("/api/learn/admin/"))) return;
                if (response.getStatus() >= 400) return;
                Object who = request.getAttribute(ATTRIBUTE);
                if (who instanceof Long id) audit.record(id, request.getMethod(), path);
            }
        }).addPathPatterns("/api/learn/**").excludePathPatterns("/api/learn/auth/**", "/api/learn/room-guest/**");
        // 2026-11-12: a game-room guest has no account - RoomGuestController checks its own per-room token.
    }
}
