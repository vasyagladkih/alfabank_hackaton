package ru.alfahackaton.stub.authservice.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.alfahackaton.stub.authservice.config.RsaKeyConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Сервис генерации тестовых токенов заглушки Alfa-ID.
 * Имитирует выпуск токена корпоративным сервером авторизации банка по стандарту OAuth 2.0 / OpenID Connect (RS256).
 */
@Service
public class StubTokenService {

    private final RsaKeyConfiguration rsaKeyConfiguration;
    private final long expirationHours;

    public StubTokenService(
            RsaKeyConfiguration rsaKeyConfiguration,
            @Value("${stub.idp.expiration-hours:24}") long expirationHours
    ) {
        this.rsaKeyConfiguration = rsaKeyConfiguration;
        this.expirationHours = expirationHours;
    }

    public String generateToken(Long userId, String username, String role, String inn) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plus(expirationHours, ChronoUnit.HOURS);

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .issuer("https://id.stubAuthService.ru")
                    .claim("username", username)
                    .claim("role", role)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry));

            if (inn != null) claimsBuilder.claim("inn", inn);

            JWTClaimsSet claimsSet = claimsBuilder.build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            signedJWT.sign(new RSASSASigner(rsaKeyConfiguration.getPrivateKey()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Сбой генерации токена в эмуляторе Auth-сервиса", e);
        }
    }
}
