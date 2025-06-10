#!/bin/bash

echo "=== 快速HBase状态诊断 ==="

# 启动HBase
echo "启动HBase..."
/root/repos/hbase-2.3.1/bin/start-cluster.sh
sleep 15

echo "检查当前表状态..."

# 检查表是否存在
echo "=== 检查表是否存在 ==="
echo "list" | /root/repos/hbase-2.3.1/bin/hbase shell

echo
echo "=== 检查usertable详细信息 ==="
echo "describe 'usertable'" | /root/repos/hbase-2.3.1/bin/hbase shell

echo
echo "=== 检查表中是否有残留数据 ==="
echo "count 'usertable', CACHE => 100" | /root/repos/hbase-2.3.1/bin/hbase shell

echo
echo "=== 检查Region分布 ==="
echo "list_regions 'usertable'" | /root/repos/hbase-2.3.1/bin/hbase shell

echo
echo "=== 如果有数据，显示前几行 ==="
echo "scan 'usertable', {LIMIT => 5}" | /root/repos/hbase-2.3.1/bin/hbase shell

# 停止HBase
echo
echo "停止HBase..."
/root/repos/hbase-2.3.1/bin/stop-cluster.sh

echo "=== 诊断完成 ===" 