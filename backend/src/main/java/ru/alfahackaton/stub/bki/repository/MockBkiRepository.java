package ru.alfahackaton.stub.bki.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.stub.bki.entity.MockBkiEntity;

import java.util.Optional;

@Repository
public interface MockBkiRepository extends CrudRepository<MockBkiEntity, String> {
    Optional<MockBkiEntity> findByInn(String inn);
}
