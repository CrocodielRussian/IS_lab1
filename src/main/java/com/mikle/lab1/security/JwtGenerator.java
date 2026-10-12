package com.mikle.lab1.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtGenerator {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtGenerator(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getUsername()).issuer("lab1")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public String extractUsername(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer("lab1")
                .sig().clear().add(Jwts.SIG.HS256).and().build()
                .parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Missing required claims");
        }
        return claims.getSubject();
    }
}
