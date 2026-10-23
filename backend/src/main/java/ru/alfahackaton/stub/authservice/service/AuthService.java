package ru.alfahackaton.stub.authservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.dto.UserProfile;
import ru.alfahackaton.mapper.UserMapper;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.UserRepository;

/**
 * Сервис эмуляции сквозной авторизации Alfa-ID.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final StubTokenService stubTokenService;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, StubTokenService stubTokenService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.stubTokenService = stubTokenService;
        this.userMapper = userMapper;
    }

    public DemoLoginResponse demoLogin(DemoLoginRequest request) {
        String username = request.getUserCode().getValue();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + username));

        String token = stubTokenService.generateToken(user.id(), user.username(), user.role(), user.inn());

        UserProfile profile = userMapper.toDto(user);

        return new DemoLoginResponse(token, profile);
    }
}
