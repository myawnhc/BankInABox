./bib.fraud.rate.sh
sleep 1
./bib.payment.rate.sh
sleep 1
./bib.payments.amazon.sh &
./bib.payments.walmart.sh &
./runall.sh
