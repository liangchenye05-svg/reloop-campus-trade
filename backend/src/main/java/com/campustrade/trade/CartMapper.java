package com.campustrade.trade;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
interface CartMapper {
    record Item(long productId, String title, String imageUrl, BigDecimal price, int stock, String status, int quantity) {}
    record Selection(long productId, int quantity) {}

    @Select("""
            SELECT p.id AS product_id,p.title,p.image_url,p.price,p.stock,p.status,c.quantity
            FROM cart_items c JOIN products p ON p.id=c.product_id
            WHERE c.buyer_id=#{buyer} ORDER BY c.product_id DESC
            """)
    List<Item> items(long buyer);

    @Insert("""
            INSERT INTO cart_items(buyer_id,product_id,quantity) VALUES(#{buyer},#{product},#{quantity})
            ON DUPLICATE KEY UPDATE quantity=#{quantity}
            """)
    void setQuantity(long buyer, long product, int quantity);

    @Delete("DELETE FROM cart_items WHERE buyer_id=#{buyer} AND product_id=#{product}")
    void remove(long buyer, long product);

    @Select("""
            <script>
            SELECT product_id, quantity FROM cart_items WHERE buyer_id=#{buyer} AND product_id IN
            <foreach collection="products" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY product_id FOR UPDATE
            </script>
            """)
    List<Selection> lockSelection(long buyer, List<Long> products);
}
