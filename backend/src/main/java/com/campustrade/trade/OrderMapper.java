package com.campustrade.trade;

import com.campustrade.product.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
interface OrderMapper {
    @Insert("""
            INSERT INTO trade_orders(id,buyer_id,idempotency_key,request_hash,status,total,created_at,expires_at)
            VALUES(#{id},#{buyer},#{key},#{hash},'PENDING_PAYMENT',0,#{created},#{expires})
            ON DUPLICATE KEY UPDATE id=id
            """)
    void allocate(String id, long buyer, String key, String hash, LocalDateTime created, LocalDateTime expires);

    @Select("SELECT * FROM trade_orders WHERE buyer_id=#{buyer} AND idempotency_key=#{key} FOR UPDATE")
    TradeOrder lockByKey(long buyer, String key);

    @Select("SELECT * FROM trade_orders WHERE id=#{id} AND buyer_id=#{buyer}")
    TradeOrder find(long buyer, String id);

    @Select("SELECT * FROM trade_orders WHERE id=#{id} FOR UPDATE")
    TradeOrder lock(String id);

    @Select("""
            SELECT * FROM trade_orders WHERE buyer_id=#{buyer}
            ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<TradeOrder> list(long buyer, int size, int offset);

    @Select("SELECT COUNT(*) FROM trade_orders WHERE buyer_id=#{buyer}")
    long count(long buyer);

    @Select("SELECT * FROM order_items WHERE order_id=#{id} ORDER BY product_id")
    List<TradeOrder.Item> items(String id);

    @Select("SELECT * FROM products WHERE id=#{id} FOR UPDATE")
    Product lockProduct(long id);

    @Update("""
            UPDATE products SET stock=stock-#{quantity}, reserved=reserved+#{quantity}
            WHERE id=#{id} AND status='ACTIVE' AND stock >= #{quantity}
            """)
    int reserve(long id, int quantity);

    @Update("UPDATE products SET reserved=reserved-#{quantity} WHERE id=#{id}")
    void consume(long id, int quantity);

    @Update("UPDATE products SET stock=stock+#{quantity}, reserved=reserved-#{quantity} WHERE id=#{id}")
    void release(long id, int quantity);

    @Insert("""
            INSERT INTO order_items(order_id,product_id,seller_id,title,unit_price,quantity)
            VALUES(#{orderId},#{productId},#{sellerId},#{title},#{unitPrice},#{quantity})
            """)
    void addItem(TradeOrder.Item item);

    @Update("UPDATE trade_orders SET total=#{total} WHERE id=#{id}")
    void setTotal(String id, BigDecimal total);

    @Update("""
            UPDATE trade_orders SET status=#{status},
              paid_at=CASE WHEN #{status}='PAID' THEN #{now} ELSE paid_at END,
              finished_at=CASE WHEN #{status} IN ('COMPLETED','CANCELLED','EXPIRED') THEN #{now} ELSE finished_at END
            WHERE id=#{id}
            """)
    void transition(String id, TradeOrder.Status status, LocalDateTime now);

    @Select("""
            SELECT id FROM trade_orders WHERE status='PENDING_PAYMENT' AND expires_at <= #{now}
            ORDER BY expires_at,id LIMIT 100
            """)
    List<String> due(LocalDateTime now);
}
