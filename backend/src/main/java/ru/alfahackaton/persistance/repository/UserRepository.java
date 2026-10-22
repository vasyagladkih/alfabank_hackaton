package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.UserEntity;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserEntity> findByUsername(String username) {
        return jdbcClient.sql("SELECT id, username, role, company_name, inn, created_at FROM users WHERE username = :username")
                .param("username", username)
                .query((rs, rowNum) -> new UserEntity(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("company_name"),
                        rs.getString("inn"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ))
                .optional();
    }

    public Optional<UserEntity> findById(Long id) {
        return jdbcClient.sql("SELECT id, username, role, company_name, inn, created_at FROM users WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new UserEntity(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("company_name"),
                        rs.getString("inn"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ))
                .optional();
    }
}