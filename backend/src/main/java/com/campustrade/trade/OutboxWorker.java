package com.campustrade.trade;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Profile("!identity")
@Service
class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private final OutboxMapper outbox;
    private final OutboxConsumer local;
    private final RocketOrderTransport broker;

    OutboxWorker(OutboxMapper outbox, OutboxConsumer local, ObjectProvider<RocketOrderTransport> broker) {
        this.outbox = outbox;
        this.local = local;
        this.broker = broker.getIfAvailable();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean sendNext() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var event = outbox.next(now, broker != null);
        if (event == null) {
            return false;
        }
        try (var ignored = MDC.putCloseable("traceId", event.traceId())) {
            if (broker != null) {
                try {
                    broker.send(event);
                } catch (RuntimeException exception) {
                    long delay = Math.min(60, 1L << Math.min(event.attempts(), 6));
                    outbox.retry(event.id(), now.plusSeconds(delay));
                    log.warn("Order event {} send failed; retry in {}s: {}", event.id(), delay, exception.toString());
                    return true;
                }
            } else {
                local.deliver(event.id());
            }
            outbox.sent(event.id(), now);
            log.info("Dispatched order event {} {} via {}", event.id(), event.kind(), broker == null ? "local" : "RocketMQ");
        }
        return true;
    }
}
