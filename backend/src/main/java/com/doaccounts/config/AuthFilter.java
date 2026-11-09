package com.doaccounts.config;

import com.doaccounts.model.User;
import com.doaccounts.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 所有 /api/** 都要帶 Authorization: Bearer &lt;通行證&gt;，且帳號為使用中（SA FR-1.4）。
 * 通過後把使用者放進 request attribute {@link #USER}，controller 用 @RequestAttribute 取得。
 * 登入與 ping、CORS preflight 不需要。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String USER = "currentUser";
    private static final Set<String> PUBLIC = Set.of("/api/auth/google", "/api/ping");

    private final UserService users;

    public AuthFilter(UserService users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod()) || !uri.startsWith("/api/") || PUBLIC.contains(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        User user = header != null && header.startsWith("Bearer ") ? users.authenticate(header.substring(7)) : null;
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"登入已過期，請重新登入\"}");
            return;
        }
        request.setAttribute(USER, user);
        chain.doFilter(request, response);
    }
}
