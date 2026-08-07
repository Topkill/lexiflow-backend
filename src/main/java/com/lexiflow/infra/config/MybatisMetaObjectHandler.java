package com.lexiflow.infra.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 元数据自动填充处理器。
 * <p>
 * 在插入时自动填充 {@code createdAt} 和 {@code updatedAt} 字段，
 * 在更新时自动填充 {@code updatedAt} 字段。
 * </p>
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    /** 插入时自动填充创建时间和更新时间。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /** 更新时自动填充更新时间。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
