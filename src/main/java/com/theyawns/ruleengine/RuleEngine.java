package com.theyawns.ruleengine;

public interface RuleEngine<T extends HasID> {


//    default public void init() {}
//    public void init(RuleSet rules);
//
//
//    public void init(Set<Rule> rules);

    // Do we want RuleEngine to be aware of Transaction class, or is this an implementation detail?
    T getNextInput();

    //public void setInputTransactionQueue(IQueue queue);

    //public void start();

    //void process(Transaction x);   // for Jet
    //RuleEvaluationResult process(Transaction x); // for IMDG
    // version that returns a Future - processAsync()


    //public void setInputSource(IQueue queue);  // Used for IMDG and Jet both
    //public void setOutputSink(IMap )
}

