#!/bin/bash

# Random range between end - start 
for i in `seq 50`
do
	R=`shuf -i 66-100 -n 1`
	#echo $R
	echo "bib.payment.rate $R `date +%s`" | nc localhost 2003
done
