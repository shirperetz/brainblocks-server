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
                return true;
            }
        }).addPathPatterns("/api/learn/**").excludePathPatterns("/api/learn/auth/**");
    }
}
