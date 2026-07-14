package com.devcollab.knowledgecore.common.outbox.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "devcollab.worker.outbox.enabled",
        havingValue = "true"
)
public class OutboxWorkerScheduler {

    private final OutboxWorkerService workerService;

    public OutboxWorkerScheduler(OutboxWorkerService workerService) {
        this.workerService = workerService;
    }

    @Scheduled(
            fixedDelayString = "${devcollab.worker.outbox.fixed-delay-ms:5000}",
            initialDelayString = "${devcollab.worker.outbox.initial-delay-ms:5000}"
    )
    public void relayOutboxEvents() {
        workerService.runOnce();
    }
}
