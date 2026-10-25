package ru.alfahackaton.api.models;

import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.ApplicationFnsSnapshotEntity;
import ru.alfahackaton.persistance.entity.UserEntity;

/**
 * Полное кредитное досье заемщика, сформированное из сущностей БД.
 */
public record BorrowerDossier(
        UserEntity user,
        ApplicationEntity application,
        ApplicationFnsSnapshotEntity fns,
        ApplicationBkiSnapshotEntity bki
) {}