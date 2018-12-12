package com.taobao.csp.sentinel.dashboard.enums;

public enum RuleDataKeyEnum {

    FLOW("FLOW"),
    DEGRADE("DEGRADE"),
    AUTHORITY("AUTHORITY"),
    ;

    private String ruleType;

    RuleDataKeyEnum(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleType() {
        return ruleType;
    }
}
