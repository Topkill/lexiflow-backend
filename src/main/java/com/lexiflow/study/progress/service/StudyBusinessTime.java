package com.lexiflow.study.progress.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class StudyBusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public LocalDateTime now() { return LocalDateTime.now(ZONE); }
}
