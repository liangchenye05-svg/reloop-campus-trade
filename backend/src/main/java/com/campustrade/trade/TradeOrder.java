package com.campustrade.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TradeOrder(String id, long buyerId, String idempotencyKey, String requestHash,
                         Status status, BigDecimal total, LocalDateTime createdAt, LocalDateTime expiresAt,
                         LocalDateTime paidAt, LocalDateTime finishedAt) {
    public enum Status { PENDING_PAYMENT, PAID, COMPLETED, CANCELLED, EXPIRED }

    public record Item(String orderId, long productId, long sellerId, String title, BigDecimal unitPrice, int quantity) {}

    public record View(String id, Status status, BigDecimal total, LocalDateTime createdAt, LocalDateTime expiresAt,
                       LocalDateTime paidAt, LocalDateTime finishedAt, List<Item> items) {
        View(TradeOrder order, List<Item> items) {
            this(order.id, order.status, order.total, order.createdAt, order.expiresAt,
                    order.paidAt, order.finishedAt, items);
        }
    }
}
