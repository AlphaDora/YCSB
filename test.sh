#!/bin/bash
YCSB_HOME="/root/repos/YCSB"
HBASE_HOME="/root/repos/hbase-2.3.1"
"$HBASE_HOME/bin/start-cluster.sh"
"$YCSB_HOME/bin/ycsb" run hbase1 \
    -P "$YCSB_HOME/workloads/workload3" \
    -p table=usertable \
    -p columnfamily=family

"$HBASE_HOME/bin/stop-cluster.sh"