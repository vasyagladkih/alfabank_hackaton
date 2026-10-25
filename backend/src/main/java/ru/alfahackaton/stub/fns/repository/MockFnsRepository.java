package ru.alfahackaton.stub.fns.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.stub.fns.entity.MockFnsEntity;

import java.util.Optional;

@Repository
public interface MockFnsRepository extends CrudRepository<MockFnsEntity, String> {
    Optional<MockFnsEntity> findByInn(String inn);
}
