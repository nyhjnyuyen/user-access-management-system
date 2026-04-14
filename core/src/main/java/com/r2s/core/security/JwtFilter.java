package com.r2s.core.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Value("${app.internal-secret}")
    private String internalSecret;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService uds) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = uds;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null, username = null;

        String path = request.getRequestURI();

        try {
            if (path.startsWith("/internal/")){
                handleInternalRequest(request, response, chain);
                return;
            }

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                username = jwtUtil.extractUsername(token);//tra thong tin
            }
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtUtil.validateToken(token, userDetails)) { //neu token expire/invalid=> catch & throw exception
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            chain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is expired");
        } catch (IllegalArgumentException | JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is invalid");
        }
    }

    //handle internal request
    private void handleInternalRequest(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException{
        String internalHeader = request.getHeader("X-Internal-Secret");

        if(internalHeader == null || !internalHeader.equals(internalSecret)){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized internal request");
            return;
        }
        UsernamePasswordAuthenticationToken internalAuth = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                AuthorityUtils.createAuthorityList("ROLE_INTERNAL")
        );
        SecurityContextHolder.getContext().setAuthentication(internalAuth);
        chain.doFilter(request, response);
    }

}