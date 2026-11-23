package com.campustrade.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GatewayApplication {
    private static final String TRACE = "X-Trace-Id";
    private static final Pattern VALID_TRACE = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("identity", route -> route.path("/api/auth/**").uri("lb://campus-identity"))
                .route("commerce", route -> route.path("/api/products", "/api/products/**", "/api/cart",
                        "/api/cart/**", "/api/orders", "/api/orders/**").uri("lb://campus-commerce"))
                .build();
    }

    @Bean
    @Order(-100)
    GlobalFilter trace() {
        return (exchange, chain) -> {
            String trace = traceId(exchange);
            exchange.getAttributes().put(TRACE, trace);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(TRACE, trace);
                return Mono.empty();
            });
            var request = exchange.getRequest().mutate().headers(headers -> headers.set(TRACE, trace)).build();
            long started = System.nanoTime();
            return chain.filter(exchange.mutate().request(request).build()).doFinally(signal -> {
                try (var ignored = MDC.putCloseable("traceId", trace)) {
                    log.info("{} {} {} {}ms", request.getMethod(), request.getPath(),
                            exchange.getResponse().getStatusCode(), (System.nanoTime() - started) / 1_000_000);
                }
            });
        };
    }

    @Bean
    @Order(-2)
    ErrorWebExceptionHandler apiErrors(ObjectMapper mapper) {
        return (exchange, exception) -> {
            var response = exchange.getResponse();
            if (response.isCommitted()) {
                return Mono.error(exception);
            }
            HttpStatusCode status = exception instanceof ResponseStatusException problem
                    ? problem.getStatusCode() : HttpStatus.BAD_GATEWAY;
            String trace = traceId(exchange);
            response.setStatusCode(status);
            response.getHeaders().set(TRACE, trace);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            var error = new LinkedHashMap<String, Object>();
            error.put("code", status.is5xxServerError() ? "UPSTREAM_UNAVAILABLE" : "REQUEST_REJECTED");
            error.put("message", status.is5xxServerError() ? "后端服务暂时不可用" : "无法处理此请求");
            error.put("data", null);
            error.put("traceId", trace);
            try (var ignored = MDC.putCloseable("traceId", trace)) {
                log.warn("Gateway status {} for {} {}", status.value(), exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath());
                return response.writeWith(Mono.just(response.bufferFactory().wrap(mapper.writeValueAsBytes(error))));
            } catch (JsonProcessingException encodingFailure) {
                return Mono.error(encodingFailure);
            }
        };
    }

    private static String traceId(ServerWebExchange exchange) {
        String trace = exchange.getAttribute(TRACE);
        if (trace != null) {
            return trace;
        }
        String incoming = exchange.getRequest().getHeaders().getFirst(TRACE);
        return incoming != null && VALID_TRACE.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString().replace("-", "");
    }
}
