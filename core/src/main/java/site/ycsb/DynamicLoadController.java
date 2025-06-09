/**
 * Copyright (c) 2024 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic load controller that can adjust target throughput over time.
 * Supports various load patterns like linear increase, step changes, sine wave, etc.
 */
public class DynamicLoadController {
  private static final Logger LOG = LoggerFactory.getLogger(DynamicLoadController.class);
  
  /**
   * Enumeration of supported load patterns.
   */
  public enum LoadPattern {
    CONSTANT,      // 恒定负载
    LINEAR,        // 线性增长/减少
    STEP,          // 阶梯式变化
    SINE_WAVE,     // 正弦波变化
    EXPONENTIAL,   // 指数增长/减少
    CUSTOM         // 自定义模式
  }
  
  private final LoadPattern pattern;
  private final long startTime;
  private final double initialThroughput;
  private final double finalThroughput;
  private final long duration;
  private final Map<String, Object> parameters;
  private final AtomicReference<Double> currentThroughput;
  private final List<LoadPhase> customPhases;
  
  /**
   * Load phase definition.
   */
  public static class LoadPhase {
    private final long startTime;
    private final long duration;
    private final double throughput;
    private final String description;
    
    public LoadPhase(long startTime, long duration, double throughput, String description) {
      this.startTime = startTime;
      this.duration = duration;
      this.throughput = throughput;
      this.description = description;
    }
    
    public long getStartTime() {
      return startTime;
    }
    
    public long getDuration() {
      return duration;
    }
    
    public double getThroughput() {
      return throughput;
    }
    
    public String getDescription() {
      return description;
    }
  }
  
  /**
   * Constructor for simple patterns.
   */
  public DynamicLoadController(LoadPattern pattern, double initialThroughput, 
                              double finalThroughput, long duration) {
    this.pattern = pattern;
    this.startTime = System.currentTimeMillis();
    this.initialThroughput = initialThroughput;
    this.finalThroughput = finalThroughput;
    this.duration = duration;
    this.parameters = new HashMap<>();
    this.currentThroughput = new AtomicReference<>(initialThroughput);
    this.customPhases = new ArrayList<>();
    
    LOG.info("Dynamic load controller initialized: pattern={}, initial={}, final={}, duration={}ms", 
             pattern, initialThroughput, finalThroughput, duration);
  }
  
  /**
   * Constructor for custom phase patterns.
   */
  public DynamicLoadController(List<LoadPhase> phases) {
    this.pattern = LoadPattern.CUSTOM;
    this.startTime = System.currentTimeMillis();
    this.customPhases = new ArrayList<>(phases);
    this.parameters = new HashMap<>();
    
    if (phases.isEmpty()) {
      this.currentThroughput = new AtomicReference<>(0.0);
      this.duration = 0;
      this.initialThroughput = 0.0;
      this.finalThroughput = 0.0;
    } else {
      // 计算总持续时间
      long maxEndTime = 0;
      for (LoadPhase phase : phases) {
        long phaseEndTime = phase.getStartTime() + phase.getDuration();
        maxEndTime = Math.max(maxEndTime, phaseEndTime);
      }
      this.duration = maxEndTime;
      
      // initialThroughput: 时间0时刻的实际吞吐量
      this.initialThroughput = calculateCustomThroughputAtTime(0);
      
      // finalThroughput: 结束时刻的实际吞吐量
      this.finalThroughput = calculateCustomThroughputAtTime(this.duration);
      
      this.currentThroughput = new AtomicReference<>(this.initialThroughput);
    }
    
    LOG.info("{} custom phases, total duration={}ms, initial={}ops/sec, final={}ops/sec", 
             phases.size(), duration, initialThroughput, finalThroughput);
  }
  
  /**
   * Helper method to calculate throughput at a specific time (used for initialization).
   */
  private double calculateCustomThroughputAtTime(long timeMs) {
    // 使用相同的逻辑处理重叠phases
    double throughput = 0.0;
    boolean foundPhase = false;
    
    for (LoadPhase phase : customPhases) {
      if (timeMs >= phase.getStartTime() && timeMs < phase.getStartTime() + phase.getDuration()) {
        throughput = phase.getThroughput();
        foundPhase = true;
        // 不要break，继续查找，这样后定义的phase会覆盖前面的
      }
    }
    
    return foundPhase ? throughput : 0.0;
  }
  
  /**
   * Set parameter for the load controller.
   */
  public void setParameter(String key, Object value) {
    parameters.put(key, value);
  }
  
  /**
   * Get current target throughput.
   */
  public double getCurrentThroughput() {
    long elapsed = System.currentTimeMillis() - startTime;
    double throughput = calculateThroughput(elapsed);
    currentThroughput.set(throughput);
    return throughput;
  }
  
  /**
   * Calculate throughput based on elapsed time.
   */
  private double calculateThroughput(long elapsedMs) {
    switch (pattern) {
    case CONSTANT:
      return initialThroughput;
      
    case LINEAR:
      if (elapsedMs >= duration) {
        return finalThroughput;
      }
      if (elapsedMs <= 0) {
        return initialThroughput;
      }
      double progress = (double) elapsedMs / duration;
      return initialThroughput + (finalThroughput - initialThroughput) * progress;
      
    case STEP:
      if (elapsedMs >= duration) {
        return finalThroughput;
      }
      if (elapsedMs <= 0) {
        return initialThroughput;
      }
      int stepCount = (Integer) parameters.getOrDefault("stepCount", 5);
      long stepDuration = duration / stepCount;
      int currentStep = (int) (elapsedMs / stepDuration);
      if (currentStep >= stepCount) {
        currentStep = stepCount - 1;
      }
      double stepProgress = (double) currentStep / (stepCount - 1);
      return initialThroughput + (finalThroughput - initialThroughput) * stepProgress;
      
    case SINE_WAVE:
      if (elapsedMs >= duration) {
        return finalThroughput;
      }
      if (elapsedMs <= 0) {
        return initialThroughput;
      }
      double amplitude = (finalThroughput - initialThroughput) / 2.0;
      double baseline = (initialThroughput + finalThroughput) / 2.0;
      double frequency = (Double) parameters.getOrDefault("frequency", 1.0);
      double phase = 2 * Math.PI * frequency * elapsedMs / duration;
      return baseline + amplitude * Math.sin(phase);
      
    case EXPONENTIAL:
      if (elapsedMs >= duration) {
        return finalThroughput;
      }
      if (elapsedMs <= 0) {
        return initialThroughput;
      }
      double base = (Double) parameters.getOrDefault("base", Math.E);
      double normalizedTime = (double) elapsedMs / duration;
      double expFactor = Math.pow(base, normalizedTime) - 1;
      double maxExpFactor = Math.pow(base, 1.0) - 1;
      return initialThroughput + (finalThroughput - initialThroughput) * (expFactor / maxExpFactor);
      
    case CUSTOM:
      return calculateCustomThroughput(elapsedMs);
      
    default:
      return initialThroughput;
    }
  }
  
  /**
   * Calculate throughput for custom phases.
   */
  private double calculateCustomThroughput(long elapsedMs) {
    // 如果还没开始，返回0
    if (elapsedMs < 0) {
      return 0.0;
    }
    
    // 查找当前时间对应的phase
    // 如果有多个phases重叠，返回最后定义的phase的throughput
    double throughput = 0.0;
    boolean foundPhase = false;
    
    for (LoadPhase phase : customPhases) {
      if (elapsedMs >= phase.getStartTime() && elapsedMs < phase.getStartTime() + phase.getDuration()) {
        throughput = phase.getThroughput();
        foundPhase = true;
        // 不要break，继续查找，这样后定义的phase会覆盖前面的
      }
    }
    
    return foundPhase ? throughput : 0.0;
  }
  
  /**
   * Get current phase information.
   */
  public String getCurrentPhaseInfo() {
    long elapsed = System.currentTimeMillis() - startTime;
    double throughput = getCurrentThroughput();
    
    if (pattern == LoadPattern.CUSTOM) {
      for (LoadPhase phase : customPhases) {
        if (elapsed >= phase.getStartTime() && elapsed < phase.getStartTime() + phase.getDuration()) {
          return String.format("Phase: %s, Throughput: %.2f ops/sec, Elapsed: %dms", 
                             phase.getDescription(), throughput, elapsed);
        }
      }
    }
    
    return String.format("Pattern: %s, Throughput: %.2f ops/sec, Elapsed: %dms/%dms", 
                        pattern, throughput, elapsed, duration);
  }
  
  /**
   * Check if the load pattern is completed.
   */
  public boolean isCompleted() {
    return (System.currentTimeMillis() - startTime) >= duration;
  }
  
  /**
   * Get remaining time in milliseconds.
   */
  public long getRemainingTime() {
    long elapsed = System.currentTimeMillis() - startTime;
    return Math.max(0, duration - elapsed);
  }
  
  /**
   * Create dynamic load controller from properties.
   */
  public static DynamicLoadController fromProperties(Properties props) {
    String patternStr = props.getProperty("dynamicload.pattern", "CONSTANT");
    LoadPattern pattern;
    try {
      pattern = LoadPattern.valueOf(patternStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      LOG.warn("Unknown load pattern: {}, using CONSTANT", patternStr);
      pattern = LoadPattern.CONSTANT;
    }
    
    double initialThroughput = Double.parseDouble(props.getProperty("dynamicload.initial", "1000"));
    double finalThroughput = Double.parseDouble(props.getProperty("dynamicload.final", "1000"));
    long duration = Long.parseLong(props.getProperty("dynamicload.duration", "60000")); // 默认60秒
    
    if (pattern == LoadPattern.CUSTOM) {
      return createCustomController(props);
    }
    
    DynamicLoadController controller = new DynamicLoadController(pattern, initialThroughput, finalThroughput, duration);
    
    switch (pattern) {
    case STEP:
      int stepCount = Integer.parseInt(props.getProperty("dynamicload.stepCount", "5"));
      controller.setParameter("stepCount", stepCount);
      break;
    case SINE_WAVE:
      double frequency = Double.parseDouble(props.getProperty("dynamicload.frequency", "1.0"));
      controller.setParameter("frequency", frequency);
      break;
    case EXPONENTIAL:
      double base = Double.parseDouble(props.getProperty("dynamicload.base", String.valueOf(Math.E)));
      controller.setParameter("base", base);
      break;
    default:
      // No additional parameters needed for other patterns
      break;
    }
    
    return controller;
  }
  
  /**
   * Create custom phase controller.
   */
  private static DynamicLoadController createCustomController(Properties props) {
    List<LoadPhase> phases = new ArrayList<>();
    
    // 解析自定义阶段配置
    // 格式: dynamicload.phases=phase1_start:phase1_duration:phase1_throughput:phase1_desc,phase2_start:...
    String phasesStr = props.getProperty("dynamicload.phases", "");
    if (!phasesStr.isEmpty()) {
      String[] phaseConfigs = phasesStr.split(",");
      for (String phaseConfig : phaseConfigs) {
        String[] parts = phaseConfig.trim().split(":");
        if (parts.length >= 3) {
          long startTime = Long.parseLong(parts[0]);
          long duration = Long.parseLong(parts[1]);
          double throughput = Double.parseDouble(parts[2]);
          String description = parts.length > 3 ? parts[3] : "Phase";
          phases.add(new LoadPhase(startTime, duration, throughput, description));
        }
      }
    }
    
    if (phases.isEmpty()) {
      LOG.warn("No custom phases defined, using default phase");
      phases.add(new LoadPhase(0, 60000, 1000, "Default"));
    }
    
    return new DynamicLoadController(phases);
  }
} 