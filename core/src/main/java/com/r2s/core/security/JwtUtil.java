package com.r2s.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    private final String SECRET_KEY = "3W86sgydpm2+4sYsQ7eBoV3LdLwuU+oKizBgS+ovqcw=";

    public String generateToken(String username){
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
        }

        public String extractUsername(String token) {
            return extractAllClaims(token).getSubject();
        }

        public boolean validateToken(String token, UserDetails userDetails) {
            try {
                String username = extractUsername(token);
                return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
            } catch (Exception e) {
                return false;
            }
        }
        private boolean isTokenExpired(String token) {
            Date expiration = extractAllClaims(token).getExpiration();
            return expiration.before(new Date());
        }
        private Claims extractAllClaims(String token) {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        }

}
