package com.cn.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class TestLog4j {

    @Test
    public void test(){
        log.info("test info");
        log.warn("test warn");
        log.error("test error");
    }

}
