/**
 * Copyright (c) 2010-2016 Yahoo! Inc., 2017 YCSB contributors All rights reserved.
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

import site.ycsb.measurements.Measurements;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread for executing transactions or data inserts to the database.
 */
public class ClientThread implements Runnable {
  // Counts down each of the clients completing.
  private final CountDownLatch completeLatch;

  private static final Logger LOG = LoggerFactory.getLogger(ClientThread.class);
  // Warm-up duration
  private static final String WARMUP_TIME_PROPERTY = "warmuptime";
  private long warmupTime;
  private boolean isWarmupPhase;

  private static boolean spinSleep;
  private DB db;
  private boolean dotransactions;
  private Workload workload;
  private int opcount;
  private double targetOpsPerMs;

  private int opsdone;
  private int threadid;
  private int threadcount;
  private Object workloadstate;
  private Properties props;
  private long targetOpsTickNs;
  private final Measurements measurements;
  
  // Dynamic load control
  private DynamicLoadController dynamicLoadController;
  private boolean useDynamicLoad;
  private long lastThroughputUpdate;
  // Add fields for dynamic load throttling
  private long dynamicStartTimeNanos;
  private int dynamicOpsDone;

  /**
   * Constructor.
   *
   * @param db                   the DB implementation to use
   * @param dotransactions       true to do transactions, false to insert data
   * @param workload             the workload to use
   * @param props                the properties defining the experiment
   * @param opcount              the number of operations (transactions or inserts) to do
   * @param targetperthreadperms target number of operations per thread per ms
   * @param completeLatch        The latch tracking the completion of all clients.
   */
  public ClientThread(DB db, boolean dotransactions, Workload workload, Properties props, int opcount,
                      double targetperthreadperms, CountDownLatch completeLatch) {
    this.db = db;
    this.dotransactions = dotransactions;
    this.workload = workload;
    this.opcount = opcount;
    opsdone = 0;
    if (targetperthreadperms > 0) {
      targetOpsPerMs = targetperthreadperms;
      targetOpsTickNs = (long) (1000000 / targetOpsPerMs);
    }
    this.props = props;
    this.warmupTime = Long.valueOf(this.props.getProperty(WARMUP_TIME_PROPERTY, "0"));
    measurements = Measurements.getMeasurements();
    spinSleep = Boolean.valueOf(this.props.getProperty("spin.sleep", "false"));
    this.completeLatch = completeLatch;
    
    // Initialize dynamic load control
    this.useDynamicLoad = Boolean.parseBoolean(props.getProperty("dynamicload.enabled", "false"));
    this.lastThroughputUpdate = 0;
    this.dynamicOpsDone = 0;
    if (useDynamicLoad) {
      // Dynamic load controller will be set later by the Client
      LOG.info("Dynamic load control enabled for thread {}", threadid);
    }
  }

  public void setThreadId(final int threadId) {
    threadid = threadId;
  }

  public void setThreadCount(final int threadCount) {
    threadcount = threadCount;
  }

  public long getWarmuptime() {
    return warmupTime;
  }

  public int getOpsDone() {
    return opsdone;
  }
  
  /**
   * Set the dynamic load controller for this thread.
   */
  public void setDynamicLoadController(DynamicLoadController controller) {
    this.dynamicLoadController = controller;
  }
  
  /**
   * Update target throughput based on dynamic load controller.
   */
  private void updateDynamicThroughput() {
    if (!useDynamicLoad || dynamicLoadController == null) {
      return;
    }
    
    long currentTime = System.currentTimeMillis();
    // Update throughput every 100ms to avoid too frequent updates
    if (currentTime - lastThroughputUpdate >= 100) {
      double newTotalThroughput = dynamicLoadController.getCurrentThroughput();
      double newTargetOpsPerMs = newTotalThroughput / threadcount / 1000.0;
      
      // Only reset when throughput actually changes significantly
      if (newTargetOpsPerMs > 0 && Math.abs(newTargetOpsPerMs - targetOpsPerMs) > 0.001) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Thread {} throughput changed from {:.2f} to {:.2f} ops/ms, resetting throttling", 
                   threadid, targetOpsPerMs, newTargetOpsPerMs);
        }
        
        targetOpsPerMs = newTargetOpsPerMs;
        targetOpsTickNs = (long) (1000000 / targetOpsPerMs);
        
        // Reset dynamic throttling counters only when throughput actually changes
        dynamicStartTimeNanos = System.nanoTime();
        dynamicOpsDone = 0;
      }
      
      lastThroughputUpdate = currentTime;
    }
  }

  public void doWarmup() {
    isWarmupPhase = true;
    long warmupStartTime = System.currentTimeMillis();
    while (isWarmupPhase && (System.currentTimeMillis() - warmupStartTime) < warmupTime) {
      try {
        if (dotransactions) {
          if (!workload.doTransaction(db, workloadstate)) {
            break;
          }
        } else {
          if (!workload.doInsert(db, workloadstate)) {
            break;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        break;
      }
    }
    isWarmupPhase = false;
    opsdone = 0;
    LOG.info("Warmup end for {} ms.", this.warmupTime);
  }

  @Override
  public void run() {
    try {
      db.init();
    } catch (DBException e) {
      e.printStackTrace();
      e.printStackTrace(System.out);
      return;
    }

    try {
      workloadstate = workload.initThread(props, threadid, threadcount);
    } catch (WorkloadException e) {
      e.printStackTrace();
      e.printStackTrace(System.out);
      return;
    }

    // do warmup to ensure that the system has reached a steady state
    doWarmup();

    //NOTE: Switching to using nanoTime and parkNanos for time management here such that the measurements
    // and the client thread have the same view on time.

    //spread the thread operations out so they don't all hit the DB at the same time
    // GH issue 4 - throws exception if _target>1 because random.nextInt argument must be >0
    // and the sleep() doesn't make sense for granularities < 1 ms anyway
    if ((targetOpsPerMs > 0) && (targetOpsPerMs <= 1.0)) {
      long randomMinorDelay = ThreadLocalRandom.current().nextInt((int) targetOpsTickNs);
      sleepUntil(System.nanoTime() + randomMinorDelay);
    }
    try {
      if (dotransactions) {
        long startTimeNanos = System.nanoTime();
        // Dynamic vs Static operation control
        if (useDynamicLoad && dynamicLoadController != null) {
          // Start the dynamic load controller now that we're past warmup
          dynamicLoadController.start();
          
          // Initialize dynamic throttling
          dynamicStartTimeNanos = startTimeNanos;
          dynamicOpsDone = 0;
          
          // Dynamic mode: ignore opcount, run until dynamic load completes
          while (!dynamicLoadController.isCompleted() && !workload.isStopRequested()) {
            updateDynamicThroughput();

            if (!workload.doTransaction(db, workloadstate)) {
              break;
            }

            opsdone++;
            dynamicOpsDone++;
            throttleNanosDynamic();
          }
        } else {
          // Static mode: traditional opcount-based control
          while (((opcount == 0) || (opsdone < opcount)) && !workload.isStopRequested()) {

            if (!workload.doTransaction(db, workloadstate)) {
              break;
            }

            opsdone++;
            throttleNanos(startTimeNanos);
          }
        }
      } else {
        long startTimeNanos = System.nanoTime();

        // Dynamic vs Static operation control
        if (useDynamicLoad && dynamicLoadController != null) {
          // Start the dynamic load controller now that we're past warmup
          dynamicLoadController.start();
          
          // Initialize dynamic throttling
          dynamicStartTimeNanos = startTimeNanos;
          dynamicOpsDone = 0;
          
          // Dynamic mode: ignore opcount, run until dynamic load completes
          while (!dynamicLoadController.isCompleted() && !workload.isStopRequested()) {
            updateDynamicThroughput();

            if (!workload.doInsert(db, workloadstate)) {
              break;
            }

            opsdone++;
            dynamicOpsDone++;
            throttleNanosDynamic();
          }
        } else {
          // Static mode: traditional opcount-based control
          while (((opcount == 0) || (opsdone < opcount)) && !workload.isStopRequested()) {

            if (!workload.doInsert(db, workloadstate)) {
              break;
            }

            opsdone++;
            throttleNanos(startTimeNanos);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      e.printStackTrace(System.out);
      System.exit(0);
    }

    try {
      measurements.setIntendedStartTimeNs(0);
      db.cleanup();
    } catch (DBException e) {
      e.printStackTrace();
      e.printStackTrace(System.out);
    } finally {
      completeLatch.countDown();
    }
  }

  private static void sleepUntil(long deadline) {
    while (System.nanoTime() < deadline) {
      if (!spinSleep) {
        LockSupport.parkNanos(deadline - System.nanoTime());
      }
    }
  }

  private void throttleNanos(long startTimeNanos) {
    //throttle the operations
    if (targetOpsPerMs > 0) {
      // delay until next tick
      long deadline = startTimeNanos + opsdone * targetOpsTickNs;
      sleepUntil(deadline);
      measurements.setIntendedStartTimeNs(deadline);
    }
  }
  
  /**
   * Dynamic throttling method that uses separate counters for dynamic load control.
   */
  private void throttleNanosDynamic() {
    //throttle the operations for dynamic load
    if (targetOpsPerMs > 0) {
      // delay until next tick using dynamic counters
      long deadline = dynamicStartTimeNanos + dynamicOpsDone * targetOpsTickNs;
      sleepUntil(deadline);
      measurements.setIntendedStartTimeNs(deadline);
    }
  }

  /**
   * The total amount of work this thread is still expected to do.
   */
  int getOpsTodo() {
    int todo = opcount - opsdone;
    return todo < 0 ? 0 : todo;
  }
}
