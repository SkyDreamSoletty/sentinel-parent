package com.taobao.csp.sentinel.dashboard.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Function:
 *
 * @author crossoverJie
 *         Date: 21/05/2018 00:30
 * @since JDK 1.8
 */
@Component
public class InitFlowData {

    private final static Logger LOGGER = LoggerFactory.getLogger(InitFlowData.class);

    @PostConstruct
    public void start() {
        LOGGER.info("规则数据初始化启动成功");
    }

}
