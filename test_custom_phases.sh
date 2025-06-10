#!/bin/bash

# 测试修复后的custom phases逻辑
echo "Testing fixed custom phases logic..."

# 编译项目
echo "Building project..."
mvn clean compile -q

# 运行测试
echo "Running test with custom phases..."
echo "Configuration: 0:5000:500:Startup,5000:5000:2000:Normal,10000:5000:5000:Peak,15000:5000:1500:Recovery,20000:5000:800:Maintenance"
echo ""
echo "Expected behavior (no overlapping phases):"
echo "- Time 0-5s: 500 ops/sec (Startup phase)"
echo "- Time 5-10s: 2000 ops/sec (Normal phase)"
echo "- Time 10-15s: 5000 ops/sec (Peak phase)"
echo "- Time 15-20s: 1500 ops/sec (Recovery phase)"
echo "- Time 20-25s: 800 ops/sec (Maintenance phase)"
echo "- Time 25s+: 0 ops/sec (no phase defined)"
echo "- Initial throughput: 500 ops/sec"
echo "- Final throughput: 0 ops/sec (no phase at end time)"
echo "- Total duration: 25000ms"
echo ""

# 运行YCSB测试
./bin/ycsb run basic -P workloads/workload3 -p status.interval=2 -s

echo ""
echo "Test completed. Check the logs above for correct phase transitions." 