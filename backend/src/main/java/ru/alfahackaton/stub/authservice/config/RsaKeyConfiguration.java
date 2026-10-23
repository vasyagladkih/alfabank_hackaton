package ru.alfahackaton.stub.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Конфигурация криптографических ключей эмулятора Alfa-ID.
 * Генерирует пару асимметричных ключей RSA (2048 бит):
 * - Private Key: остается внутри стаб-пакета для подписи токенов (RS256);
 * - Public Key: публикуется в виде Spring-бина для проверки токенов сервером кредитного кабинета.
 */
@Configuration
public class RsaKeyConfiguration {

    private final KeyPair keyPair;

    public RsaKeyConfiguration() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Не удалось инициализировать генератор ключей RSA", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    @Bean
    public RSAPublicKey rsaPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }
}
