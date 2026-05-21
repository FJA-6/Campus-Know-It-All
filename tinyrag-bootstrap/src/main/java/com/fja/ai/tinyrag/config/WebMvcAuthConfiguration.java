package com.fja.ai.tinyrag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebMvcAuthConfiguration(AuthInterceptor authInterceptor,
                                   AdminInterceptor adminInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**"
                );

        // 管理员能力：仅 ADMIN 可访问
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns(
                        "/api/admin/**",
                        "/api/rag/knowledge/**"
                );
    }
}
