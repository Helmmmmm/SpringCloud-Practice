package com.hmall.gateway.filters;

import com.hmall.common.exception.UnauthorizedException;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.util.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1、获取用户信息(request)
        ServerHttpRequest request = exchange.getRequest();

        // 2、判断是否需要登录拦截
        if (isExclude(request.getPath().toString())) { // 判断当前路径是否需要被放行
            return chain.filter(exchange); // 放行
        }

        // 3、获取token(从请求头header)
        String token = null;
        List<String> headers = request.getHeaders().get("authorization");
        if (headers != null && !headers.isEmpty()) {
            token = headers.get(0);
        }

        // 4、校验并解析token
        Long userId = null;
        try {
            userId = jwtTool.parseToken(token); // 无论是null还是有值，都会去校验
        } catch (UnauthorizedException e) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED); // 拦截，设置响应状态码为401
            return response.setComplete(); // 终止，不要再往后走了，后续所有的拦截器不再执行，也就不再转发给微服务
        }

        // 5、把用户信息保存到请求头中，传递用户信息
        // 这里请求头的名字可以随意写，但一定要和其他微服务的开发者约定好，这里传什么名，他们那里接收什么名
        String userInfo = userId.toString();
        ServerWebExchange swe = exchange.mutate().request(builder -> builder.header("user-info", userInfo)).build();

        return chain.filter(swe); // 6、放行，传入新的ServerWebExchange
    }

    private boolean isExclude(String path) {
        // 在yml中写的是/search/**、/users/login、/items/**等，所以每个元素是一个路径表达式
        // 这类路径称为AntPath，不是正则，但类似正则，Spring提供了AntPathMatcher工具类，用于匹配这类路径
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern, path)) return true;
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
