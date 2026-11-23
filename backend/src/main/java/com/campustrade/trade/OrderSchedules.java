package com.campustrade.trade;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.context.annotation.Profile("!identity")
@Component
class OrderSchedules {
    private final OutboxWorker worker;
    private final OrderMapper mapper;
    private final OrderService orders;

    OrderSchedules(OutboxWorker worker, OrderMapper mapper, OrderService orders) {
        this.worker = worker;
        this.mapper = mapper;
        this.orders = orders;
    }

    @Scheduled(fixedDelay = 500)
    void deliver() {
        for (int i = 0; i < 20 && worker.sendNext(); i++) {
            // Drain one bounded batch on each invocation.
        }
    }

    @Scheduled(fixedDelay = 5000)
    void sweep() {
        mapper.due(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30)).forEach(orders::expire);
    }
}
