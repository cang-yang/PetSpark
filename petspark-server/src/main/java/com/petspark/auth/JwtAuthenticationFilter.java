package com.petspark.auth;

import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.SecurityErrorHandlers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SecurityErrorHandlers errorHandlers;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SecurityErrorHandlers errorHandlers,
            UserRepository userRepository) {
        this.jwtService = jwtService;
        this.errorHandlers = errorHandlers;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.AuthenticatedToken token;
            try {
                token = jwtService.verify(header.substring("Bearer ".length()).trim());
                SysUser current = userRepository.findById(token.userId())
                        .filter(user -> "ACTIVE".equals(user.status()))
                        .filter(user -> user.tokenVersion() == token.tokenVersion())
                        .orElseThrow(() -> new IllegalArgumentException("stale access token"));
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
                errorHandlers.commence(request, response, new BadCredentialsException("invalid access token", ex));
                return;
            }
            AuthenticatedUser user = new AuthenticatedUser(
                    token.userId(),
                    token.username(),
                    token.authorities().stream().map(SimpleGrantedAuthority::new).toList());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
