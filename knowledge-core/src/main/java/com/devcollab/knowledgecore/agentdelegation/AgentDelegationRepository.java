package com.devcollab.knowledgecore.agentdelegation;

import java.util.Optional;
import java.util.UUID;

public interface AgentDelegationRepository {
    AgentDelegation save(AgentDelegation delegation);

    Optional<AgentDelegation> findById(UUID id);
}
