package com.campustrade.trade;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@org.springframework.context.annotation.Profile("!identity")
@Component
@ConditionalOnProperty(name = "campus.mq.enabled", havingValue = "true")
class RocketOrderTransport {
    static final String NOTICES = "CampusTradeNotices";
    static final String TIMEOUTS = "CampusTradeTimeouts";
    private static final Logger log = LoggerFactory.getLogger(RocketOrderTransport.class);
    private final DefaultMQProducer producer = new DefaultMQProducer("campus-trade-outbox");
    private final DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("campus-trade-orders");
    private final OutboxConsumer events;

    RocketOrderTransport(OutboxConsumer events, @Value("${campus.mq.namesrv}") String namesrv) {
        this.events = events;
        producer.setNamesrvAddr(namesrv);
        producer.setVipChannelEnabled(false);
        producer.setRetryTimesWhenSendFailed(0);
        consumer.setNamesrvAddr(namesrv);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeFromWhere(org.apache.rocketmq.common.consumer.ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }

    @PostConstruct
    void start() throws Exception {
        producer.start();
        try {
            consumer.subscribe(NOTICES, "*");
            consumer.subscribe(TIMEOUTS, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (var message : messages) {
                    try (var ignored = MDC.putCloseable("traceId", message.getUserProperty("traceId"))) {
                        events.deliver(Long.parseLong(new String(message.getBody(), StandardCharsets.US_ASCII)));
                    } catch (RuntimeException exception) {
                        log.warn("RocketMQ consumer will retry event {} after delivery attempt {} traceId={}: {}",
                                message.getKeys(), message.getReconsumeTimes(), message.getUserProperty("traceId"), exception.toString());
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
        } catch (Exception exception) {
            producer.shutdown();
            throw exception;
        }
    }

    void send(OutboxMapper.Event event) {
        boolean timeout = event.kind() == OutboxMapper.Kind.TIMEOUT;
        String id = Long.toString(event.id());
        var message = new Message(timeout ? TIMEOUTS : NOTICES, event.kind().name(), id,
                id.getBytes(StandardCharsets.US_ASCII));
        message.putUserProperty("traceId", event.traceId());
        long timestamp = event.deliverAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        if (timeout) {
            message.setDeliverTimeMs(Math.max(timestamp, System.currentTimeMillis() + 1500));
        }
        try {
            var result = producer.send(message, 1500);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("RocketMQ send status " + result.getSendStatus());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RocketMQ send interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("RocketMQ send failed: " + exception.getMessage(), exception);
        }
    }

    @PreDestroy
    void stop() {
        consumer.shutdown();
        producer.shutdown();
    }
}
