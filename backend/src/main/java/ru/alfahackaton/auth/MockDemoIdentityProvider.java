package ru.alfahackaton.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.UserRepository;

import java.util.Set;

/** Explicitly enabled role switch for synthetic demonstration identities only. */
@Component
@ConditionalOnProperty(name = "demo.auth-provider", havingValue = "mock", matchIfMissing = true)
public class MockDemoIdentityProvider implements DemoIdentityProvider {
    private final UserRepository users;
    private final boolean enabled;

    public MockDemoIdentityProvider(UserRepository users, @Value("${demo.enabled:false}") boolean enabled) {
        this.users = users;
        this.enabled = enabled;
    }

    @Override
    public UserEntity authenticate(String userCode) {
        if (!enabled) throw new AccessDeniedException("Демонстрационный вход отключён");
        if (userCode == null || !Set.of("client_good", "client_bad", "bank_employee").contains(userCode)) {
            throw new IllegalArgumentException("Неизвестный демонстрационный профиль");
        }
        return users.findByUsername(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Демонстрационный профиль отсутствует"));
    }
}
