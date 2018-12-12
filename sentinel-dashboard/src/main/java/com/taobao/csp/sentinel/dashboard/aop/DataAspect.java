package com.taobao.csp.sentinel.dashboard.aop;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.taobao.csp.sentinel.dashboard.client.SentinelApiClient;
import com.taobao.csp.sentinel.dashboard.datasource.entity.rule.AuthorityRuleEntity;
import com.taobao.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.taobao.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.taobao.csp.sentinel.dashboard.discovery.MachineInfo;
import com.taobao.csp.sentinel.dashboard.enums.RuleDataKeyEnum;
import com.taobao.csp.sentinel.dashboard.repository.rule.InMemAuthorityRuleStore;
import com.taobao.csp.sentinel.dashboard.repository.rule.InMemDegradeRuleStore;
import com.taobao.csp.sentinel.dashboard.repository.rule.InMemFlowRuleStore;
import com.taobao.csp.sentinel.dashboard.util.SaveRulesUtils;
import com.taobao.csp.sentinel.dashboard.util.ThreadUtils;
import com.taobao.csp.sentinel.dashboard.view.Result;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class DataAspect {

    private Logger log = LoggerFactory.getLogger(DataAspect.class);

    private static List<String> rules;

    static{
        rules = new ArrayList<>();
        rules.add("FLOW");
        rules.add("DEGRADE");
        rules.add("AUTHORITY");
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private InMemFlowRuleStore flowRuleStore;
    @Autowired
    private InMemDegradeRuleStore degradeRuleStore;
    @Autowired
    private InMemAuthorityRuleStore authorityRuleStore;
    @Autowired
    private SentinelApiClient sentinelApiClient;
    @Resource
    private SaveRulesUtils saveRuleForRedis;

    @Pointcut("execution(* com.taobao.csp.sentinel.dashboard.view.MachineRegistryController.*(..))")
    private void registry(){}

    @Pointcut("execution(* com.taobao.csp.sentinel.dashboard.repository.rule.*.findAllByMachine(..))")
    private void findAllByMachine(){}

    @Around("registry()")
    public void registryAround(ProceedingJoinPoint pjp){
        try {
            Object[] args = pjp.getArgs();
            Result result = (Result) pjp.proceed(pjp.getArgs());
//            Result{success=true, code=0, msg='success', data=null}
            if(result.isSuccess()){
                String app = args[0].toString();
                String ip = args[4].toString();
                Integer port = Integer.valueOf(args[5].toString());
                StringBuilder key = new StringBuilder(app);
                key.append("_");
                key.append(ip);
                key.append("_");
                key.append(port);
                if(!stringRedisTemplate.hasKey(key.toString())){
                    setFlow(app, ip, port, key);
//                    ThreadUtils.getThreadPool().execute(() -> setFlow(app, ip, port, key));
                }
                stringRedisTemplate.opsForValue().set(key.toString(),args[1].toString(),20, TimeUnit.SECONDS);
                log.info("成功保存设备：{}",key.toString());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AfterReturning(returning = "o",pointcut = "findAllByMachine()")
    public void findAllByMachineReturing(Object o ){
        List list = (List) o;
        if(list!=null && list.size()>0){
            Object o1 = list.get(0);
            JSONObject jsonObject = JSONObject.parseObject(JSONObject.toJSONString(o1));
            MachineInfo machineInfo = new MachineInfo();
            machineInfo.setApp(jsonObject.getString("app"));
            machineInfo.setIp(jsonObject.getString("ip"));
            machineInfo.setPort(jsonObject.getInteger("port"));
            if(o1 instanceof FlowRuleEntity){
                saveRuleForRedis.saveRuleForRedis(machineInfo, list, RuleDataKeyEnum.FLOW.getRuleType());
                log.info("成功保存服务流控规则");
            }
            if(o1 instanceof DegradeRuleEntity){
                saveRuleForRedis.saveRuleForRedis(machineInfo, list, RuleDataKeyEnum.DEGRADE.getRuleType());
                log.info("成功保存服务降级规则");
            }
            if(o1 instanceof AuthorityRuleEntity){
                saveRuleForRedis.saveRuleForRedis(machineInfo, list, RuleDataKeyEnum.AUTHORITY.getRuleType());
                log.info("成功保存服务授权规则");
            }
        }
        log.info("Response内容:"+ JSONObject.toJSONString(o));
    }


    private void setFlow(String app, String ip, Integer port, StringBuilder key) {
        String flowKey = key.toString()+"_"+ RuleDataKeyEnum.FLOW.getRuleType();
        String degradeKey = key.toString()+"_"+ RuleDataKeyEnum.DEGRADE.getRuleType();
        String authorityKey = key.toString()+"_"+ RuleDataKeyEnum.AUTHORITY.getRuleType();
        String flowStr = stringRedisTemplate.opsForValue().get(flowKey);
        String degradeStr = stringRedisTemplate.opsForValue().get(degradeKey);
        String authorityStr = stringRedisTemplate.opsForValue().get(authorityKey);
        JSONArray jsonArray = JSONObject.parseArray(flowStr);
        if(StringUtils.isNotBlank(flowStr)){
            List<FlowRuleEntity> flowList = getFlowList(jsonArray);
            sentinelApiClient.setFlowRuleOfMachine(app,ip,port,flowList);
            log.info("成功加载 {} 服务授权规则,ip为 {}",app,ip);
        }
        if(StringUtils.isNotBlank(degradeStr)){
            List<DegradeRuleEntity> degradeList = getDegradeList(jsonArray);
            sentinelApiClient.setDegradeRuleOfMachine(app,ip,port,degradeList);
            log.info("成功加载 {} 服务降级规则,ip为 {}",app,ip);
        }
        if(StringUtils.isNotBlank(authorityStr)){
            List<AuthorityRuleEntity> authorityList = getAuthorityList(jsonArray);
            sentinelApiClient.setAuthorityRuleOfMachine(app,ip,port,authorityList);
            log.info("成功加载 {} 服务授权规则,ip为 {}",app,ip);
        }
    }

    private List<FlowRuleEntity> getFlowList(JSONArray jsonArray) {
        List<FlowRuleEntity> list = new ArrayList<>();
        jsonArray.stream().filter(Objects::nonNull).forEach(item -> {
            FlowRuleEntity flowRuleEntity = JSONObject.parseObject(item.toString(), FlowRuleEntity.class);
            list.add(flowRuleEntity);
            flowRuleStore.save(flowRuleEntity);
        });
        return list;
    }
    private List<DegradeRuleEntity> getDegradeList(JSONArray jsonArray) {
        List<DegradeRuleEntity> list = new ArrayList<>();
        jsonArray.stream().filter(Objects::nonNull).forEach(item -> {
            DegradeRuleEntity degradeRuleEntity = JSONObject.parseObject(item.toString(), DegradeRuleEntity.class);
            list.add(degradeRuleEntity);
            degradeRuleStore.save(degradeRuleEntity);
        });
        return list;
    }
    private List<AuthorityRuleEntity> getAuthorityList(JSONArray jsonArray) {
        List<AuthorityRuleEntity> list = new ArrayList<>();
        jsonArray.stream().filter(Objects::nonNull).forEach(item -> {
            AuthorityRuleEntity authorityRuleEntity = JSONObject.parseObject(item.toString(), AuthorityRuleEntity.class);
            list.add(authorityRuleEntity);
            authorityRuleStore.save(authorityRuleEntity);
        });
        return list;
    }

}
