package com.campustrade.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Profile("!identity")
@Service
class OutboxConsumer {
    private static final Logger log = LoggerFactory.getLogger(OutboxConsumer.class);
    private final OutboxMapper outbox;
    private final OrderService orders;

    OutboxConsumer(OutboxMapper outbox, OrderService orders) {
        this.outbox = outbox;
        this.orders = orders;
    }

    @Transactional
    public void deliver(long id) {
        var event = outbox.event(id);
        if (event == null) {
            return;
        }
        try (var ignored = MDC.putCloseable("traceId", event.traceId())) {
            if (event.kind() == OutboxMapper.Kind.TIMEOUT) {
                orders.expire(event.orderId());
            } else {
                outbox.recordNotice(id);
            }
            log.info("Consumed order event {} {} order={}", id, event.kind(), event.orderId());
        }
    }
}
