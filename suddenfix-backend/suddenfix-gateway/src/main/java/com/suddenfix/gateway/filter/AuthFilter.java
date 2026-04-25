package com.suddenfix.gateway.filter;

import com.suddenfix.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static com.suddenfix.common.enums.RedisPreMessage.TOKEN_BLACKLIST;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (pathMatcher.match("/api/user/login", path)
                || pathMatcher.match("/api/user/register", path)
                || pathMatcher.match("/api/pay/mock/**", path)
                || pathMatcher.match("/api/pay/alipay/notify", path)
                || pathMatcher.match("/api/pay/success", path)
                || pathMatcher.match("/api/product/**", path)) {
            return chain.filter(exchange);
        }

        String token = request.getHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String blackListKey = TOKEN_BLACKLIST.getValue() + token;
        Boolean isBlacklisted = redisTemplate.hasKey(blackListKey);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Claims claims = JwtUtils.parseToken(token);
            if (claims == null) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            Long userId = claims.get("userId", Long.class);
            Integer role = claims.get("role", Integer.class);
            String username = claims.get("username", String.class);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("userId", String.valueOf(userId))
                    .header("user_id", String.valueOf(userId))
                    .header("role", String.valueOf(role == null ? 0 : role))
                    .header("username", username == null ? "" : username)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
