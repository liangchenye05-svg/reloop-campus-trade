package com.campustrade.product;

import com.campustrade.common.Api.Page;
import com.campustrade.common.BusinessException;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Profile("!identity")
@Service
public class ProductService {
    private final ProductMapper mapper;
    private final SellerDirectory sellers;

    private final ApplicationEventPublisher events;

    ProductService(ProductMapper mapper, ApplicationEventPublisher events, SellerDirectory sellers) {
        this.events = events;
        this.mapper = mapper;
        this.sellers = sellers;
    }

    @Cacheable(cacheNames = ProductCaching.HOT, key = "#category == null ? 'all' : #category.name()",
            condition = "(#q == null || #q.isBlank()) && #page == 1 && #size == 12")
    public Page<Product.View> search(String q, Product.Category category, int page, int size) {
        String term = q == null || q.isBlank() ? null : q.strip();
        return new Page<>(mapper.search(term, category, size, (page - 1) * size).stream()
                .map(Product.View::new).toList(), mapper.count(term, category), page, size);
    }

    @Cacheable(cacheNames = ProductCaching.DETAILS, key = "#id")
    public Product.Details detail(long id) {
        Product product = mapper.find(id);
        if (product == null || product.status() != Product.Status.ACTIVE) {
            return null;
        }
        return new Product.Details(new Product.View(product), sellers.profile(product.sellerId()));
    }

    public Page<Product.View> mine(long userId, int page, int size) {
        return new Page<>(mapper.owned(userId, size, (page - 1) * size).stream()
                .map(Product.View::new).toList(), mapper.countOwned(userId), page, size);
    }

    @Transactional
    public Product.View publish(long userId, ProductInput input) {
        mapper.insert(userId, input);
        long id = mapper.lastId();
        events.publishEvent(new ProductCaching.Changed(List.of(id)));
        return new Product.View(mapper.find(id));
    }

    @Transactional
    public Product.View edit(long userId, long id, ProductInput input) {
        checkOwner(id, userId, false);
        if (mapper.edit(id, userId, input) == 0) {
            throw BusinessException.conflict("STOCK_LIMIT", "可售库存与已预留库存之和超出限制");
        }
        events.publishEvent(new ProductCaching.Changed(List.of(id)));
        return new Product.View(mapper.find(id));
    }

    @Transactional
    public Product.View status(long userId, boolean admin, long id, Product.Status status) {
        checkOwner(id, userId, admin && status == Product.Status.INACTIVE);
        mapper.changeStatus(id, status);
        events.publishEvent(new ProductCaching.Changed(List.of(id)));
        return new Product.View(mapper.find(id));
    }

    private void checkOwner(long id, long userId, boolean moderator) {
        Product product = mapper.find(id);
        if (product == null) {
            throw BusinessException.notFound("商品不存在");
        }
        if (product.sellerId() != userId && !moderator) {
            throw BusinessException.forbidden("只能管理自己发布的商品");
        }
    }
}
