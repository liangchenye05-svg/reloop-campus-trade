package com.campustrade.product;

import com.campustrade.common.Api;
import com.campustrade.common.BusinessException;
import com.campustrade.identity.SellerProfile;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!identity")
@RestController
@RequestMapping("/api/products")
class ProductController {
    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    Api<Api.Page<Product.View>> search(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Product.Category category,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return Api.ok(service.search(q, category, page, size));
    }

    record Detail(@JsonUnwrapped Product.View product, SellerProfile seller) {}

    @GetMapping("/{id}")
    Api<Detail> detail(@PathVariable @Positive long id) {
        Product.Details detail = service.detail(id);
        if (detail == null) {
            throw BusinessException.notFound("商品不存在或已下架");
        }
        return Api.ok(new Detail(detail.product(), detail.seller()));
    }

    @GetMapping("/mine")
    Api<Api.Page<Product.View>> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return Api.ok(service.mine(Long.parseLong(jwt.getSubject()), page, size));
    }

    @PostMapping
    Api<Product.View> publish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductInput input) {
        return Api.ok(service.publish(Long.parseLong(jwt.getSubject()), input));
    }

    @PutMapping("/{id}")
    Api<Product.View> edit(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody ProductInput input) {
        return Api.ok(service.edit(Long.parseLong(jwt.getSubject()), id, input));
    }

    @PatchMapping("/{id}/status")
    Api<Product.View> status(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody ProductInput.StatusChange input) {
        return Api.ok(service.status(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")),
                id, input.status()));
    }
}
