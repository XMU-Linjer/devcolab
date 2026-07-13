package com.devcollab.knowledgecore.auth.domain;
import java.util.Optional;
public interface UserRepository {
    Optional<UserAccount> findByNormalizedUsername(String normalizedUsername);
    boolean existsByNormalizedUsername(String normalizedUsername);
    UserAccount save(UserAccount userAccount);
}