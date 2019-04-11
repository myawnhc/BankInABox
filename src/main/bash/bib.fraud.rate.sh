#!/bin/bash

# Random range between end - start 
for i in `seq 50`
do
	R=`shuf -i 0-20 -n 1`
	#echo $R
	echo "bib.fraud.rate $R `date +%s`" | nc localhost 2003
done
