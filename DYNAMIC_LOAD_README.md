# YCSB 动态负载控制功能

## 概述

YCSB 现在支持动态负载控制，允许在测试运行期间动态调整目标吞吐量。这个功能对于模拟真实世界的负载变化场景非常有用，比如：

- 从轻负载到重负载的渐进式增长
- 模拟业务高峰期的负载变化
- 测试系统在负载突变时的表现
- 模拟维护期间的负载下降和恢复

## 支持的负载模式

### 1. CONSTANT (恒定负载)
保持恒定的吞吐量，与传统的YCSB行为相同。

### 2. LINEAR (线性变化)
在指定时间内线性地从初始吞吐量变化到最终吞吐量。

### 3. STEP (阶梯式变化)
将总时间分成若干个阶段，每个阶段保持恒定吞吐量，阶段间有跳跃式变化。

### 4. SINE_WAVE (正弦波变化)
按正弦波模式变化吞吐量，可以模拟周期性的负载变化。

### 5. EXPONENTIAL (指数变化)
按指数函数变化吞吐量，可以模拟快速增长或衰减的负载。

### 6. CUSTOM (自定义阶段)
定义多个自定义阶段，每个阶段可以有不同的持续时间和吞吐量。

## 配置参数

### 基本参数
- `dynamicload.enabled`: 启用动态负载控制 (默认: false)
- `dynamicload.pattern`: 负载模式 (CONSTANT, LINEAR, STEP, SINE_WAVE, EXPONENTIAL, CUSTOM)
- `dynamicload.initial`: 初始吞吐量 (ops/sec)
- `dynamicload.final`: 最终吞吐量 (ops/sec)
- `dynamicload.duration`: 总持续时间 (毫秒)

### 模式特定参数

#### STEP 模式
- `dynamicload.stepCount`: 阶梯数量 (默认: 5)

#### SINE_WAVE 模式
- `dynamicload.frequency`: 完整周期数 (默认: 1.0)

#### EXPONENTIAL 模式
- `dynamicload.base`: 指数底数 (默认: e)

#### CUSTOM 模式
- `dynamicload.phases`: 自定义阶段定义
  - 格式: `start_time:duration:throughput:description,start_time:duration:throughput:description,...`
  - start_time: 阶段开始时间 (相对于测试开始，毫秒)
  - duration: 阶段持续时间 (毫秒)
  - throughput: 目标吞吐量 (ops/sec)
  - description: 阶段描述 (可选)

## 使用示例

### 示例 1: 线性增长负载
```properties
# 从 1000 ops/sec 线性增长到 5000 ops/sec，持续 2 分钟
dynamicload.enabled=true
dynamicload.pattern=LINEAR
dynamicload.initial=1000
dynamicload.final=5000
dynamicload.duration=120000
```

### 示例 2: 阶梯式负载
```properties
# 4 个阶梯，从 1000 ops/sec 到 8000 ops/sec
dynamicload.enabled=true
dynamicload.pattern=STEP
dynamicload.initial=1000
dynamicload.final=8000
dynamicload.duration=120000
dynamicload.stepCount=4
```

### 示例 3: 自定义阶段负载
```properties
# 模拟真实业务场景：轻负载 -> 高峰 -> 维护 -> 恢复
dynamicload.enabled=true
dynamicload.pattern=CUSTOM
dynamicload.phases=0:30000:1000:Light_Load,30000:30000:3000:Ramp_Up,60000:30000:6000:Peak_Hours,90000:15000:500:Maintenance,105000:15000:2000:Recovery
```

### 示例 4: 正弦波负载
```properties
# 正弦波变化，2个完整周期
dynamicload.enabled=true
dynamicload.pattern=SINE_WAVE
dynamicload.initial=2000
dynamicload.final=6000
dynamicload.duration=120000
dynamicload.frequency=2.0
```

## 运行示例

### 使用预定义的动态负载工作负载
```bash
# 线性增长负载
./bin/ycsb run basic -P workloads/workload_dynamic_linear -s

# 阶梯式负载
./bin/ycsb run basic -P workloads/workload_dynamic_step -s

# 自定义阶段负载
./bin/ycsb run basic -P workloads/workload_dynamic_custom -s
```

### 使用命令行参数
```bash
# 线性增长负载
./bin/ycsb run basic -P workloads/workloada \
  -p dynamicload.enabled=true \
  -p dynamicload.pattern=LINEAR \
  -p dynamicload.initial=1000 \
  -p dynamicload.final=5000 \
  -p dynamicload.duration=120000 \
  -s

# 阶梯式负载
./bin/ycsb run basic -P workloads/workloada \
  -p dynamicload.enabled=true \
  -p dynamicload.pattern=STEP \
  -p dynamicload.initial=1000 \
  -p dynamicload.final=8000 \
  -p dynamicload.duration=120000 \
  -p dynamicload.stepCount=4 \
  -s
```

## 状态监控

启用状态监控 (`-s` 参数) 时，动态负载信息会显示在状态输出中：

```
2024-01-15 10:30:15:123 60 sec: 45000 operations; 750.25 current ops/sec; [Pattern: LINEAR, Throughput: 2500.00 ops/sec, Elapsed: 60000ms/120000ms]
```

## 注意事项

1. **线程分配**: 动态负载控制器会自动将目标吞吐量分配给所有客户端线程。

2. **更新频率**: 吞吐量每100毫秒更新一次，以避免过于频繁的调整。

3. **预热期**: 动态负载控制在预热期结束后开始生效。

4. **操作计数**: 当使用动态负载时，建议设置足够大的 `operationcount` 或使用 `maxexecutiontime` 来确保测试能够完整运行。

5. **数据库连接**: 确保数据库能够处理最大预期吞吐量，避免成为瓶颈。

## 实际应用场景

### 1. 性能测试
- 测试系统在负载逐渐增加时的性能表现
- 找到系统的性能拐点和瓶颈

### 2. 容量规划
- 模拟业务增长对系统的影响
- 评估系统在不同负载水平下的资源需求

### 3. 故障恢复测试
- 模拟系统故障后的负载恢复过程
- 测试自动扩缩容机制的响应

### 4. 业务场景模拟
- 模拟电商促销期间的流量变化
- 模拟社交媒体的日常流量模式

## 故障排除

### 常见问题

1. **吞吐量没有变化**
   - 检查 `dynamicload.enabled=true` 是否设置
   - 确认动态负载参数配置正确
   - 查看日志中的动态负载初始化信息

2. **性能不如预期**
   - 检查数据库是否成为瓶颈
   - 确认网络延迟和资源限制
   - 调整线程数量和连接池设置

3. **自定义阶段配置错误**
   - 检查阶段配置格式是否正确
   - 确认时间参数使用毫秒单位
   - 验证阶段时间没有重叠或间隙

### 调试技巧

1. 启用调试日志：
```bash
-p log4j.logger.site.ycsb.DynamicLoadController=DEBUG
```

2. 使用状态监控观察实时变化：
```bash
./bin/ycsb run basic -P workloads/workload_dynamic_linear -s
```

3. 分析输出日志中的动态负载信息。 