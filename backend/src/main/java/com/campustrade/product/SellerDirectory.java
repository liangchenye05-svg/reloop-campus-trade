package com.campustrade.product;

import com.campustrade.common.Api;
import com.campustrade.common.BusinessException;
import com.campustrade.common.TraceFilter;
import com.campustrade.identity.AccountService;
import com.campustrade.identity.SellerProfile;
import feign.FeignException;
import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface SellerDirectory {
    SellerProfile profile(long id);

    @Configuration(proxyBeanMethods = false)
    @Profile("!identity & !commerce")
    class Local {
        @Bean
        SellerDirectory sellerDirectory(AccountService accounts) {
            return accounts::profile;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("commerce")
    @EnableFeignClients(clients = Remote.class)
    class Distributed {
        private static final Logger log = LoggerFactory.getLogger(Distributed.class);

        @Bean
        RequestInterceptor sellerClientHeaders(@Value("${campus.internal-key}") String key) {
            if (key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("INTERNAL_SERVICE_KEY must contain at least 32 UTF-8 bytes");
            }
            return request -> {
                request.header(TraceFilter.HEADER, MDC.get("traceId"));
                request.header("X-Internal-Key", key);
            };
        }

        @Bean
        SellerDirectory sellerDirectory(Remote remote) {
            return id -> {
                try {
                    return remote.profile(id).data();
                } catch (FeignException exception) {
                    log.warn("Identity service call failed for seller {}: HTTP {}", id, exception.status());
                    throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", "卖家资料暂时不可用");
                }
            };
        }
    }

    @FeignClient(name = "campus-identity")
    interface Remote {
        @GetMapping("/internal/sellers/{id}")
        Api<SellerProfile> profile(@PathVariable("id") long id);
    }
}
