#!/bin/bash

# Random range between end - start 
for i in `seq 500`
do
	R=`shuf -i 0-9848926 -n 1`
	#echo $R
	echo "bib.payments.amazon $R `date +%s`" | nc localhost 2003
done
