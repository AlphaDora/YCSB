#!/bin/bash

# YCSB Dynamic Load Test Script
# This script tests the dynamic load control functionality
HBASE_HOME="/root/repos/hbase-2.3.1"       # HBase 安装目录

YCSB_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "YCSB Home: $YCSB_HOME"

# Test 1: Linear Load Growth
echo "=========================================="
echo "Test 1: Linear Load Growth (1000->3000 ops/sec over 30s)"
echo "=========================================="
echo "Starting HBase cluster..."
"$HBASE_HOME/bin/start-cluster.sh"
$YCSB_HOME/bin/ycsb run hbase1 \
  -P $YCSB_HOME/workloads/workloada \
  -p table="usertable" \
  -p columnfamily="family"
echo "Stopping HBase cluster..."
"$HBASE_HOME/bin/stop-cluster.sh"
echo ""
echo "Test 1 completed."
echo ""

# Test 2: Step Load Changes
echo "=========================================="
echo "Test 2: Step Load Changes (1000->4000 ops/sec in 3 steps over 30s)"
echo "=========================================="
echo "Starting HBase cluster..."
"$HBASE_HOME/bin/start-cluster.sh"
$YCSB_HOME/bin/ycsb run hbase1 \
  -P $YCSB_HOME/workloads/workload2 \
  -p table="usertable" \
  -p columnfamily="family"
echo "Stopping HBase cluster..."
"$HBASE_HOME/bin/stop-cluster.sh"
echo ""
echo "Test 2 completed."
echo ""

# Test 3: Custom Phases
echo "=========================================="
echo "Test 3: Custom Phases (Light->Peak->Maintenance->Recovery)"
echo "=========================================="
echo "Starting HBase cluster..."
"$HBASE_HOME/bin/start-cluster.sh"
$YCSB_HOME/bin/ycsb run hbase1 \
  -P $YCSB_HOME/workloads/workload3 \
  -p table="usertable" \
  -p columnfamily="family"
echo "Stopping HBase cluster..."
"$HBASE_HOME/bin/stop-cluster.sh"
echo ""
echo "Test 3 completed."
echo ""

echo "All dynamic load tests completed successfully!"
echo "Check the output above to verify that throughput changes as expected." 