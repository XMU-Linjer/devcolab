package com.devcollab.knowledgecore.auth.infrastructure;
import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.auth.domain.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
@Repository
public class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserAccount> usersByNormalizedUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<UserAccount> findByNormalizedUsername(String normalizedUsername) {
        return Optional.ofNullable(usersByNormalizedUsername.get(normalizedUsername));
    }

    @Override
    public boolean existsByNormalizedUsername(String normalizedUsername) {
        return usersByNormalizedUsername.containsKey(normalizedUsername);
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        usersByNormalizedUsername.put(userAccount.normalizedUsername(), userAccount);
        return userAccount;
    }
}