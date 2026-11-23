package com.campustrade.trade;

import com.campustrade.common.Api;
import com.campustrade.common.BusinessException;
import com.campustrade.product.Product;
import com.campustrade.product.ProductMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!identity")
@RestController
@RequestMapping("/api/cart")
class CartController {
    private final CartMapper cart;
    private final ProductMapper products;

    CartController(CartMapper cart, ProductMapper products) {
        this.cart = cart;
        this.products = products;
    }

    record Quantity(@NotNull @Min(1) @Max(99) Integer quantity) {}

    @GetMapping
    Api<List<CartMapper.Item>> list(@AuthenticationPrincipal Jwt jwt) {
        return Api.ok(cart.items(Long.parseLong(jwt.getSubject())));
    }

    @PutMapping("/{productId}")
    Api<Void> put(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId,
            @Valid @RequestBody Quantity input) {
        long buyer = Long.parseLong(jwt.getSubject());
        Product product = products.find(productId);
        if (product == null || product.status() != Product.Status.ACTIVE) {
            throw BusinessException.notFound("商品不存在或已下架");
        }
        if (product.sellerId() == buyer) {
            throw BusinessException.forbidden("不能购买自己发布的商品");
        }
        if (product.stock() < input.quantity()) {
            throw BusinessException.conflict("OUT_OF_STOCK", "商品库存不足");
        }
        cart.setQuantity(buyer, productId, input.quantity());
        return Api.ok(null);
    }

    @DeleteMapping("/{productId}")
    Api<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long productId) {
        cart.remove(Long.parseLong(jwt.getSubject()), productId);
        return Api.ok(null);
    }
}
