#!/bin/bash

python3 process-results.py processed-contention.csv \
results/contention/counter-mixed-10/:1 results/contention/counter-strong-10/:1 \
results/contention/counter-mixed-50/:1 results/contention/counter-strong-50/:1 \
results/contention/counter-mixed-100/:1 results/contention/counter-strong-100/:1 \
results/contention/counter-mixed-200/:1 results/contention/counter-strong-200/:1 \
results/contention/counter-mixed-500/:1 results/contention/counter-strong-500/:1 \
results/contention/counter-mixed-20/:1 results/contention/counter-strong-20/:1 \
results/contention/eshop-mixed-10/:1 results/contention/eshop-mixed-50/:1 results/contention/eshop-mixed-100/:1 results/contention/eshop-mixed-500/:1 \
results/contention/eshop-strong-50/:1 \
results/contention/local/counter-mixed-10ms/:1 results/contention/local/counter-strong-10ms/:1 \
results/contention/counter-mixed-10-rerun/:1 \
results/contention/counter-mixed-50-rerun/:1 \
results/contention/counter-mixed-100-rerun/:1 \
results/contention/counter-mixed-200-rerun/:1 \
results/contention/counter-mixed-500-rerun/:1 \
results/contention/counter-mixed-20-rerun/:1 results/contention/counter-strong-20-rerun/:1
#results/contention/mgrp-cont-mixed-1/:1000 results/contention/mgrp-cont-strong-1/:1000 results/contention/mgrp-cont-mixed-10/:1000 results/contention/mgrp-cont-strong-10/:1000 results/contention/mgrp-cont-mixed-100/:1000 results/contention/mgrp-cont-strong-100/:1000 results/contention/mgrp-cont-mixed-1000/:1000 results/contention/mgrp-cont-strong-1000/:1000 \


#python3 generate-graphs.py processed-contention.csv normalized-contention.csv results/contention/mgrp-cont-mixed-1/:results/contention/mgrp-cont-strong-1/ results/contention/mgrp-cont-mixed-10/:results/contention/mgrp-cont-strong-10/ results/contention/mgrp-cont-mixed-100/:results/contention/mgrp-cont-strong-100/ results/contention/mgrp-cont-mixed-1000/:results/contention/mgrp-cont-strong-1000/ results/contention/counter-mixed-10/:results/contention/counter-strong-10/ results/contention/counter-mixed-50/:results/contention/counter-strong-50/ results/contention/counter-mixed-100/:results/contention/counter-strong-100/ results/contention/counter-mixed-200/:results/contention/counter-strong-200/ results/contention/counter-mixed-500/:results/contention/counter-strong-500/
