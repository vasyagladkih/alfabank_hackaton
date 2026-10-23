package ru.alfahackaton.service;

import org.springframework.stereotype.Service;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.dto.UserProfile;
import ru.alfahackaton.dto.UserRole;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.auth.DemoIdentityProvider;
import ru.alfahackaton.security.JwtTokenProvider;

@Service
public class AuthService {

    private final DemoIdentityProvider identityProvider;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(DemoIdentityProvider identityProvider, JwtTokenProvider jwtTokenProvider) {
        this.identityProvider = identityProvider;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public DemoLoginResponse demoLogin(DemoLoginRequest request) {
        String username = request.getUserCode().getValue();
        UserEntity user = identityProvider.authenticate(username);

        String token = jwtTokenProvider.generateToken(user.id(), user.username(), user.role(), user.inn());

        UserRole roleEnum = UserRole.fromValue(user.role());
        UserProfile profile = new UserProfile(user.id(), user.username(), roleEnum);
        profile.setCompanyName(user.companyName());
        profile.setInn(user.inn());

        return new DemoLoginResponse(token, profile);
    }
}
