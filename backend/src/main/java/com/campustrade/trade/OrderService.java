package com.campustrade.trade;

import com.campustrade.common.Api.Page;
import com.campustrade.common.BusinessException;
import com.campustrade.product.Product;
import com.campustrade.product.ProductCaching;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Profile("!identity")
@Service
class OrderService {
    private final CartMapper cart;
    private final OrderMapper orders;
    private final OutboxMapper outbox;
    private final Duration timeout;
    private final ApplicationEventPublisher events;

    OrderService(CartMapper cart, OrderMapper orders, OutboxMapper outbox, ApplicationEventPublisher events, @Value("${campus.orders.timeout:PT15M}") Duration timeout) {
        this.cart = cart;
        this.orders = orders;
        this.outbox = outbox;
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(23)) > 0) {
            throw new IllegalArgumentException("ORDER_TIMEOUT must be positive and at most 23 hours");
        }
        this.timeout = timeout;
        this.events = events;
    }

    @Transactional
    public TradeOrder.View checkout(long buyer, String key, List<Long> requested) {
        List<Long> ids = requested.stream().distinct().sorted().toList();
        // Positive sorted numeric IDs form an unambiguous canonical representation.
        String hash;
        try {
            byte[] canonical = ids.toString().getBytes(StandardCharsets.UTF_8);
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java 17", exception);
        }
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        orders.allocate(id, buyer, key, hash, now, now.plus(timeout));
        TradeOrder allocated = orders.lockByKey(buyer, key);
        if (!allocated.requestHash().equals(hash)) {
            throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "幂等键已用于不同商品");
        }
        if (!allocated.id().equals(id)) {
            return new TradeOrder.View(allocated, orders.items(allocated.id()));
        }

        List<CartMapper.Selection> selected = cart.lockSelection(buyer, ids);
        if (selected.size() != ids.size()) {
            throw BusinessException.conflict("CART_CHANGED", "结算商品已从购物车移除");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CartMapper.Selection line : selected) {
            Product product = orders.lockProduct(line.productId());
            if (product.sellerId() == buyer) {
                throw BusinessException.forbidden("不能购买自己发布的商品");
            }
            if (orders.reserve(product.id(), line.quantity()) == 0) {
                throw BusinessException.conflict("OUT_OF_STOCK", "商品库存不足或已下架");
            }
            orders.addItem(new TradeOrder.Item(id, product.id(), product.sellerId(), product.title(),
                    product.price(), line.quantity()));
            cart.remove(buyer, product.id());
            total = total.add(product.price().multiply(BigDecimal.valueOf(line.quantity())));
        }
        orders.setTotal(id, total);
        enqueue(id, OutboxMapper.Kind.TIMEOUT, allocated.expiresAt(), now);
        events.publishEvent(new ProductCaching.Changed(ids));
        return detail(buyer, id);
    }

    public Page<TradeOrder.View> list(long buyer, int page, int size) {
        var items = orders.list(buyer, size, (page - 1) * size).stream()
                .map(order -> new TradeOrder.View(order, List.of())).toList();
        return new Page<>(items, orders.count(buyer), page, size);
    }

    public TradeOrder.View detail(long buyer, String id) {
        TradeOrder order = orders.find(buyer, id);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return new TradeOrder.View(order, orders.items(id));
    }

    @Transactional
    public TradeOrder.View change(long buyer, String id, TradeOrder.Status target) {
        TradeOrder order = orders.lock(id);
        if (order == null || order.buyerId() != buyer) {
            throw BusinessException.notFound("订单不存在");
        }
        if (order.status() == target || target == TradeOrder.Status.PAID && order.status() == TradeOrder.Status.COMPLETED) {
            return new TradeOrder.View(order, orders.items(id));
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (target == TradeOrder.Status.COMPLETED) {
            if (order.status() != TradeOrder.Status.PAID) {
                throw BusinessException.conflict("INVALID_STATE", "已支付订单才能确认完成");
            }
        } else {
            if (order.status() != TradeOrder.Status.PENDING_PAYMENT) {
                throw BusinessException.conflict("INVALID_STATE", "仅待支付订单可执行此操作");
            }
            if (target == TradeOrder.Status.PAID && !now.isBefore(order.expiresAt())) {
                throw BusinessException.conflict("ORDER_EXPIRED", "订单已超过支付时间");
            }
            var items = orders.items(id);
            for (TradeOrder.Item item : items) {
                if (target == TradeOrder.Status.PAID) {
                    orders.consume(item.productId(), item.quantity());
                } else {
                    orders.release(item.productId(), item.quantity());
                }
            }
        }
        orders.transition(id, target, now);
        enqueue(id, OutboxMapper.Kind.valueOf(target.name()), now, now);
        if (target == TradeOrder.Status.CANCELLED) {
            events.publishEvent(new ProductCaching.Changed(orders.items(id).stream().map(TradeOrder.Item::productId).toList()));
        }
        return detail(buyer, id);
    }

    public Page<OutboxMapper.Notice> notices(long buyer, int page, int size) {
        return new Page<>(outbox.notices(buyer, size, (page - 1) * size), outbox.noticeCount(buyer), page, size);
    }

    private void enqueue(String id, OutboxMapper.Kind kind, LocalDateTime delivery, LocalDateTime now) {
        String trace = MDC.get("traceId");
        if (trace == null) {
            trace = UUID.randomUUID().toString().replace("-", "");
        }
        outbox.enqueue(id, kind, trace, delivery, now);
    }

    @Transactional
    public boolean expire(String id) {
        TradeOrder order = orders.lock(id);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (order == null || order.status() != TradeOrder.Status.PENDING_PAYMENT || now.isBefore(order.expiresAt())) {
            return false;
        }
        var items = orders.items(id);
        for (TradeOrder.Item item : items) {
            orders.release(item.productId(), item.quantity());
        }
        orders.transition(id, TradeOrder.Status.EXPIRED, now);
        enqueue(id, OutboxMapper.Kind.EXPIRED, now, now);
        events.publishEvent(new ProductCaching.Changed(items.stream().map(TradeOrder.Item::productId).toList()));
        return true;
    }
}
