package com.devcollab.knowledgecore.common.outbox.worker;

import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayResult;
import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OutboxWorkerService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxWorkerService.class);

    private final OutboxRelayService relayService;
    private final OutboxWorkerProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxWorkerService(
            OutboxRelayService relayService,
            OutboxWorkerProperties properties
    ) {
        this.relayService = relayService;
        this.properties = properties;
    }

    public OutboxRelayResult runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Outbox worker tick skipped because previous tick is still running");
            return new OutboxRelayResult(0, 0, 0);
        }

        try {
            OutboxRelayResult result = relayService.relayRetryableEvents(
                    properties.batchSize(),
                    properties.maxRetryCount()
            );
            if (result.scanned() > 0 || result.failed() > 0) {
                log.info(
                        "Outbox worker tick completed: scanned={}, published={}, failed={}",
                        result.scanned(),
                        result.published(),
                        result.failed()
                );
            }
            return result;
        } finally {
            running.set(false);
        }
    }
}
