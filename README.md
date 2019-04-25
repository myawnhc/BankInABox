# BankInABox
This is a simple implementation of a Retail Banking application, intended to show features of Hazelcast IMDG and Hazelcast Jet being used cooperatively.

The initial implementation is of a single Fraud Detection rule.   Additional rules and features will be added over time.

To run:
First, start one or more IMDG Cluster nodes using the script runCluster.sh
Next, start the processing engine using the script runLauncher.sh.
Finally, start generating test data by running the script runDataLoader.sh

Hazelcast Management Center can be used to monitor the maps and watch how data moves between the maps.   (Generated
transactions go into the preAuth map; once they have been processed they will move to either approved, rejectedForCredit, or rejectedForFraud). 

Jet Management Center can be used to monitor the Jet pipeline job.  The pipeline watches the transaction stream and calculates updated average transaction amounts per merchant.   The Jet Management Center startup script needs to be modified to connect to port 5710 rather than default 5701, so that Jet and IMDG can run on the same server without collision.

(This demo will eventually be containerized with Docker and possibly Kubernetes, and at that time port assignments will be set up so that no user intervention is needed). 

