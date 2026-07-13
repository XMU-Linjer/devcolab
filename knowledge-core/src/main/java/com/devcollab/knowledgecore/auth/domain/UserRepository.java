package com.devcollab.knowledgecore.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByNormalizedUsername(String normalizedUsername);

    boolean existsByNormalizedUsername(String normalizedUsername);

    UserAccount save(UserAccount userAccount);
}
