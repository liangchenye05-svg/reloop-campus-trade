package com.campustrade.trade;

import com.campustrade.common.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!identity")
@RestController
@RequestMapping("/api/orders")
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    record Checkout(@NotEmpty @Size(max = 50) List<@NotNull @Positive Long> productIds) {}

    @PostMapping
    Api<TradeOrder.View> checkout(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String key,
            @Valid @RequestBody Checkout request) {
        return Api.ok(orders.checkout(Long.parseLong(jwt.getSubject()), key, request.productIds()));
    }

    @GetMapping
    Api<Api.Page<TradeOrder.View>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return Api.ok(orders.list(Long.parseLong(jwt.getSubject()), page, size));
    }

    @GetMapping("/notices")
    Api<Api.Page<OutboxMapper.Notice>> notices(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return Api.ok(orders.notices(Long.parseLong(jwt.getSubject()), page, size));
    }

    @GetMapping("/{id}")
    Api<TradeOrder.View> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Api.ok(orders.detail(Long.parseLong(jwt.getSubject()), id.toString()));
    }

    @PostMapping("/{id}/pay")
    Api<TradeOrder.View> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Api.ok(orders.change(Long.parseLong(jwt.getSubject()), id.toString(), TradeOrder.Status.PAID));
    }

    @PostMapping("/{id}/cancel")
    Api<TradeOrder.View> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Api.ok(orders.change(Long.parseLong(jwt.getSubject()), id.toString(), TradeOrder.Status.CANCELLED));
    }

    @PostMapping("/{id}/complete")
    Api<TradeOrder.View> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Api.ok(orders.change(Long.parseLong(jwt.getSubject()), id.toString(), TradeOrder.Status.COMPLETED));
    }
}
