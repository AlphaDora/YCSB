#!/bin/bash

# YCSB Dynamic Load Test Script
# This script tests the dynamic load control functionality

YCSB_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "YCSB Home: $YCSB_HOME"

# Test 1: Linear Load Growth
echo "=========================================="
echo "Test 1: Linear Load Growth (1000->3000 ops/sec over 30s)"
echo "=========================================="

$YCSB_HOME/bin/ycsb run basic \
  -P $YCSB_HOME/workloads/workloada \
  -p recordcount=1000 \
  -p operationcount=100000 \
  -p threadcount=5 \
  -p dynamicload.enabled=true \
  -p dynamicload.pattern=LINEAR \
  -p dynamicload.initial=1000 \
  -p dynamicload.final=3000 \
  -p dynamicload.duration=30000 \
  -p warmuptime=5000 \
  -s

echo ""
echo "Test 1 completed."
echo ""

# Test 2: Step Load Changes
echo "=========================================="
echo "Test 2: Step Load Changes (1000->4000 ops/sec in 3 steps over 30s)"
echo "=========================================="

$YCSB_HOME/bin/ycsb run basic \
  -P $YCSB_HOME/workloads/workloada \
  -p recordcount=1000 \
  -p operationcount=100000 \
  -p threadcount=5 \
  -p dynamicload.enabled=true \
  -p dynamicload.pattern=STEP \
  -p dynamicload.initial=1000 \
  -p dynamicload.final=4000 \
  -p dynamicload.duration=30000 \
  -p dynamicload.stepCount=3 \
  -p warmuptime=5000 \
  -s

echo ""
echo "Test 2 completed."
echo ""

# Test 3: Custom Phases
echo "=========================================="
echo "Test 3: Custom Phases (Light->Peak->Maintenance->Recovery)"
echo "=========================================="

$YCSB_HOME/bin/ycsb run basic \
  -P $YCSB_HOME/workloads/workloada \
  -p recordcount=1000 \
  -p operationcount=100000 \
  -p threadcount=5 \
  -p dynamicload.enabled=true \
  -p dynamicload.pattern=CUSTOM \
  -p "dynamicload.phases=0:10000:1000:Light,10000:10000:3000:Peak,20000:5000:500:Maintenance,25000:5000:2000:Recovery" \
  -p warmuptime=5000 \
  -s

echo ""
echo "Test 3 completed."
echo ""

echo "All dynamic load tests completed successfully!"
echo "Check the output above to verify that throughput changes as expected." 