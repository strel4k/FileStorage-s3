package com.crudapp.filestorage.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtTokenService {
    private final byte[] secret;
    private final long expiresMinutes;

    public JwtTokenService(
            @Value("${jwt.secret:dev-secret-change-me-please-at-least-32-bytes}") String secret,
            @Value("${jwt.expiresMinutes:120}") long expiresMinutes
    ) {
        this.secret = secret.getBytes();
        this.expiresMinutes = expiresMinutes;
    }

    public String generateToken(Integer userId, String username, List<String> roles, Map<String, Object> extra) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresMinutes * 60);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(username)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("uid", userId)
                .claim("roles", roles);

        if (extra != null) extra.forEach(builder::claim);
        JWTClaimsSet claims = builder.build();

        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    public SignedJWT parseAndVerify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new RuntimeException("Invalid Signature");
            }
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new RuntimeException("Token expired");
            }
            return jwt;
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT: " + e.getMessage(), e);
        }
    }
}
