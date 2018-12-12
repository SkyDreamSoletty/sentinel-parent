package com.taobao.csp.sentinel.dashboard.util;

import com.alibaba.fastjson.JSONObject;
import com.taobao.csp.sentinel.dashboard.discovery.MachineInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaveRulesUtils {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void saveRuleForRedis(MachineInfo machineInfo, List allByMachine, String type) {
        StringBuilder key = new StringBuilder(machineInfo.getApp());
        key.append("_");
        key.append(machineInfo.getIp());
        key.append("_");
        key.append(machineInfo.getPort());
        Boolean aBoolean = redisTemplate.hasKey(key.toString());
        if(aBoolean){
            key.append("_");
            key.append(type);
            redisTemplate.opsForValue().set(key.toString(), JSONObject.toJSONString(allByMachine));
        }
    }

}
