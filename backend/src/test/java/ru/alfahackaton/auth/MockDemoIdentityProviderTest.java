package ru.alfahackaton.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MockDemoIdentityProviderTest {
    @Test void demoLoginMustBeExplicitlyEnabled() {
        var repository = mock(UserRepository.class);
        var provider = new MockDemoIdentityProvider(repository, false);
        assertThrows(AccessDeniedException.class, () -> provider.authenticate("client_good"));
        verifyNoInteractions(repository);
    }

    @Test void acceptsOnlyTheThreeExistingDemoCodes() {
        var repository = mock(UserRepository.class);
        var provider = new MockDemoIdentityProvider(repository, true);
        assertThrows(IllegalArgumentException.class, () -> provider.authenticate("arbitrary_admin"));
        verifyNoInteractions(repository);
        var user = new UserEntity(7L, "client_good", "CLIENT", "DEMO", "0000000000", OffsetDateTime.now());
        when(repository.findByUsername("client_good")).thenReturn(Optional.of(user));
        assertSame(user, provider.authenticate("client_good"));
    }
}
