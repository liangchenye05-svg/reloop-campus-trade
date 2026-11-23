package com.campustrade.product;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper {
    String FILTER = """
            WHERE status = 'ACTIVE'
            <if test="category != null">AND category = #{category}</if>
            <if test="q != null">AND (LOCATE(#{q}, title) > 0 OR LOCATE(#{q}, description) > 0)</if>
            """;

    @Select("SELECT * FROM products WHERE id = #{id}")
    Product find(long id);

    @Select("<script>SELECT * FROM products " + FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Product> search(String q, Product.Category category, int size, int offset);

    @Select("<script>SELECT COUNT(*) FROM products " + FILTER + "</script>")
    long count(String q, Product.Category category);

    @Select("SELECT * FROM products WHERE seller_id = #{sellerId} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Product> owned(long sellerId, int size, int offset);

    @Select("SELECT COUNT(*) FROM products WHERE seller_id = #{sellerId}")
    long countOwned(long sellerId);

    @Insert("""
            INSERT INTO products(seller_id,title,description,category,image_url,price,stock)
            VALUES(#{sellerId},#{p.title},#{p.description},#{p.category},#{p.imageUrl},#{p.price},#{p.stock})
            """)
    void insert(long sellerId, ProductInput p);

    @Select("SELECT LAST_INSERT_ID()")
    long lastId();

    @Update("""
            UPDATE products SET title=#{p.title}, description=#{p.description}, category=#{p.category},
              image_url=#{p.imageUrl}, price=#{p.price}, stock=#{p.stock}
            WHERE id=#{id} AND seller_id=#{sellerId} AND reserved + #{p.stock} <= 100000
            """)
    int edit(long id, long sellerId, ProductInput p);

    @Update("UPDATE products SET status=#{status} WHERE id=#{id}")
    void changeStatus(long id, Product.Status status);
}
