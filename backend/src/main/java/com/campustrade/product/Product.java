package com.campustrade.product;

import com.campustrade.identity.SellerProfile;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Product(long id, long sellerId, String title, String description, Category category, String imageUrl,
                      BigDecimal price, int stock, int reserved, Status status, LocalDateTime createdAt,
                      LocalDateTime updatedAt) {
    public enum Category { BOOKS, ELECTRONICS, DAILY, SPORTS, OTHER }
    public enum Status { ACTIVE, INACTIVE }

    public record Details(View product, SellerProfile seller) {}

    public record View(long id, long sellerId, String title, String description, Category category, String imageUrl,
                       BigDecimal price, int stock, Status status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public View(Product product) {
            this(product.id, product.sellerId, product.title, product.description, product.category,
                    product.imageUrl, product.price, product.stock, product.status, product.createdAt, product.updatedAt);
        }
    }
}
