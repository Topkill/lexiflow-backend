package com.lexiflow.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 配置 MyBatis-Plus 插件，包括乐观锁拦截器和分页拦截器。
 * 分页插件针对 MySQL 数据库配置，启用溢出处理并设置每页最大 200 条记录。
 * </p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 创建 MyBatis-Plus 拦截器链。
     * <p>
     * 包含乐观锁拦截器和分页拦截器，分页插件针对 MySQL 配置。
     * </p>
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setOverflow(true);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }
}
