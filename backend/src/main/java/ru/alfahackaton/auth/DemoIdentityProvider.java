package ru.alfahackaton.auth;

import ru.alfahackaton.persistance.entity.UserEntity;

/** Replace this boundary when connecting a bank's identity system; never weaken JWT verification. */
public interface DemoIdentityProvider {
    UserEntity authenticate(String userCode);
}
