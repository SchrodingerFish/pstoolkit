package com.cn.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class TestLog4j {

    @Test
    public void test(){
        log.trace("=== TRACE message ===");
        log.debug("=== DEBUG message ===");
        log.info("=== INFO message ===");
        log.warn("=== WARN message ===");
        log.error("=== ERROR message ===");
    }

    @Test
    public void testConfig(){

    }

}
