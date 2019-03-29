# RuleEngine
This is a simple (trivial at this point - simple is aspirational) implementation of a flexible Rule Engine showing the capabilities of Hazelcast Jet. 

To run:
First start up the Transaction Generator (com.theyawns.domain.payments.TransactionGeneratorMain).   It will create
1 million accounts, and one transaction for each account, and add them to appropriate IMaps (account and pendingTransactions).
A separate thread will listen for changes to the map (which will happen when Jet finishes processing each transaction), and 
will then delete the 'pending' transaction and add the transaction to an 'approved' or 'rejected' map. 

Once Transaction Generator is started, start the Jet processing job (com.theyawns.domain.payments.JetMain).  This will
perform processing of payment rules for each transaction.   Currently only one rule is implemented (A credit limit check),
but more robust rules in multiple rulesets are planned. 

The processes do not currently terminate until manually stopped (as this is modeling an always-available compute function), but 
an option to cleanly shut down will likely be added. 
