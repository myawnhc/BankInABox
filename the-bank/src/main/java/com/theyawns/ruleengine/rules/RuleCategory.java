package com.theyawns.ruleengine.rules;

public enum RuleCategory {

    FraudRules("Fraud Rules"),
    CreditRules ("Credit Rules");

    private String name;

    RuleCategory(String name) {
        this.name = name;
    }
}
