package com.campustrade.trade;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
interface OutboxMapper {
    enum Kind { TIMEOUT, PAID, CANCELLED, COMPLETED, EXPIRED }
    record Event(long id, String orderId, Kind kind, String traceId, LocalDateTime deliverAt, int attempts) {}
    record Notice(long eventId, String orderId, Kind kind, LocalDateTime createdAt) {}

    String EVENT_FIELDS = "id,order_id,kind,trace_id,deliver_at,attempts";

    @Insert("""
            INSERT INTO order_outbox(order_id,kind,trace_id,deliver_at,next_attempt_at)
            VALUES(#{orderId},#{kind},#{trace},#{deliverAt},#{now})
            """)
    void enqueue(String orderId, Kind kind, String trace, LocalDateTime deliverAt, LocalDateTime now);

    @Select("""
            SELECT id,order_id,kind,trace_id,deliver_at,attempts FROM order_outbox
            WHERE sent_at IS NULL AND next_attempt_at <= #{now} AND (#{allowDelay} OR deliver_at <= #{now})
            ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    Event next(LocalDateTime now, boolean allowDelay);

    @Select("SELECT " + EVENT_FIELDS + " FROM order_outbox WHERE id=#{id}")
    Event event(long id);

    @Update("UPDATE order_outbox SET sent_at=#{now} WHERE id=#{id}")
    void sent(long id, LocalDateTime now);

    @Update("UPDATE order_outbox SET attempts=attempts+1,next_attempt_at=#{next} WHERE id=#{id}")
    void retry(long id, LocalDateTime next);

    @Insert("""
            INSERT INTO order_notices(event_id,buyer_id,order_id,kind)
            SELECT e.id,o.buyer_id,e.order_id,e.kind FROM order_outbox e JOIN trade_orders o ON o.id=e.order_id
            WHERE e.id=#{id}
            ON DUPLICATE KEY UPDATE event_id=order_notices.event_id
            """)
    void recordNotice(long id);

    @Select("""
            SELECT event_id,order_id,kind,created_at FROM order_notices WHERE buyer_id=#{buyer}
            ORDER BY event_id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<Notice> notices(long buyer, int size, int offset);

    @Select("SELECT COUNT(*) FROM order_notices WHERE buyer_id=#{buyer}")
    long noticeCount(long buyer);
}
