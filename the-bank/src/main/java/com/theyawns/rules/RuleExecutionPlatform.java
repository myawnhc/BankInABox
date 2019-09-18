package com.theyawns.rules;

public enum RuleExecutionPlatform {
    Jet,
    IMDG,
    Both,       // Some work done on each
    Either      // All work done on one platform, but can be sent either way (typically odd-even modulo of txn id)
}
