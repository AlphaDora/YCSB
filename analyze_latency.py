#!/usr/bin/env python3
"""
YCSB Dynamic Load Latency Analysis Tool

This script runs multiple YCSB tests and analyzes the latency patterns
over time during dynamic load phases.
"""

import subprocess
import re
import os
import sys
import time
import matplotlib.pyplot as plt
import numpy as np
from datetime import datetime
import json

class YCSBLatencyAnalyzer:
    def __init__(self, test_script="./test.sh", num_runs=3):
        self.test_script = test_script
        self.num_runs = num_runs
        self.results = []
        
    def run_single_test(self, run_id):
        """Run a single test and capture output"""
        print(f"Running test {run_id + 1}/{self.num_runs}...")
        
        try:
            # Run the test script and capture output
            result = subprocess.run(
                [self.test_script], 
                capture_output=True, 
                text=True, 
                timeout=300  # 5 minute timeout
            )
            
            if result.returncode != 0:
                print(f"Test {run_id + 1} failed with return code {result.returncode}")
                print(f"Error output: {result.stderr}")
                return None
                
            return result.stdout
            
        except subprocess.TimeoutExpired:
            print(f"Test {run_id + 1} timed out")
            return None
        except Exception as e:
            print(f"Error running test {run_id + 1}: {e}")
            return None
    
    def extract_timeseries_data(self, output):
        """Extract time series latency data from YCSB output"""
        timeseries_data = []
        
        # Look for UPDATE time series data
        # Format: [UPDATE], timestamp, latency
        pattern = r'\[UPDATE\],\s*(\d+),\s*([\d.]+)'
        
        matches = re.findall(pattern, output)
        
        for timestamp_str, latency_str in matches:
            try:
                timestamp = int(timestamp_str)
                latency = float(latency_str)
                timeseries_data.append((timestamp, latency))
            except ValueError:
                continue
                
        return sorted(timeseries_data)
    
    def extract_phase_info(self, output):
        """Extract dynamic load phase information"""
        phases = []
        
        # Look for phase information in status output
        # Pattern: Phase: PhaseName, Throughput: X ops/sec, Elapsed: Xms
        phase_pattern = r'Phase:\s*(\w+),\s*Throughput:\s*([\d.]+)\s*ops/sec,\s*Elapsed:\s*(\d+)ms'
        
        matches = re.findall(phase_pattern, output)
        
        for phase_name, throughput_str, elapsed_str in matches:
            try:
                throughput = float(throughput_str)
                elapsed = int(elapsed_str)
                phases.append({
                    'name': phase_name,
                    'throughput': throughput,
                    'elapsed': elapsed
                })
            except ValueError:
                continue
                
        return phases
    
    def run_all_tests(self):
        """Run all tests and collect data"""
        print(f"Starting {self.num_runs} test runs...")
        
        for i in range(self.num_runs):
            output = self.run_single_test(i)
            
            if output is None:
                continue
                
            # Extract data
            timeseries_data = self.extract_timeseries_data(output)
            phase_info = self.extract_phase_info(output)
            
            if timeseries_data:
                self.results.append({
                    'run_id': i + 1,
                    'timeseries': timeseries_data,
                    'phases': phase_info,
                    'timestamp': datetime.now().isoformat()
                })
                print(f"Test {i + 1} completed: {len(timeseries_data)} data points")
            else:
                print(f"Test {i + 1} completed but no time series data found")
            
            # Wait between tests
            if i < self.num_runs - 1:
                print("Waiting 10 seconds before next test...")
                time.sleep(10)
    
    def plot_latency_over_time(self, save_file="latency_analysis.png"):
        """Plot latency over time for all runs"""
        if not self.results:
            print("No data to plot")
            return
            
        plt.figure(figsize=(15, 10))
        
        # Define phase boundaries (from workload3 configuration)
        phase_boundaries = [
            (0, 10000, "Baseline (1K ops/sec)"),
            (10000, 20000, "Moderate (5K ops/sec)"),
            (20000, 30000, "High (20K ops/sec)"),
            (30000, 40000, "VeryHigh (50K ops/sec)"),
            (40000, 50000, "Extreme (100K ops/sec)")
        ]
        
        # Plot each run
        colors = ['blue', 'red', 'green', 'orange', 'purple']
        
        for i, result in enumerate(self.results):
            timeseries = result['timeseries']
            if not timeseries:
                continue
                
            timestamps = [t[0] for t in timeseries]
            latencies = [t[1] for t in timeseries]
            
            color = colors[i % len(colors)]
            plt.plot(timestamps, latencies, 
                    label=f'Run {result["run_id"]}', 
                    color=color, alpha=0.7, linewidth=1)
        
        # Add phase boundaries
        for start, end, label in phase_boundaries:
            plt.axvline(x=start, color='gray', linestyle='--', alpha=0.5)
            plt.axvline(x=end, color='gray', linestyle='--', alpha=0.5)
            
            # Add phase labels
            mid_point = (start + end) / 2
            plt.text(mid_point, plt.ylim()[1] * 0.9, label, 
                    rotation=90, ha='center', va='top', fontsize=8)
        
        plt.xlabel('Time (ms)')
        plt.ylabel('Latency (μs)')
        plt.title('YCSB Dynamic Load Test - Latency Over Time')
        plt.legend()
        plt.grid(True, alpha=0.3)
        
        # Set reasonable y-axis limits
        if self.results:
            all_latencies = []
            for result in self.results:
                all_latencies.extend([t[1] for t in result['timeseries']])
            
            if all_latencies:
                max_latency = np.percentile(all_latencies, 95)  # Use 95th percentile to avoid outliers
                plt.ylim(0, max_latency * 1.1)
        
        plt.tight_layout()
        plt.savefig(save_file, dpi=300, bbox_inches='tight')
        print(f"Plot saved to {save_file}")
        plt.show()
    
    def plot_average_latency(self, save_file="average_latency.png"):
        """Plot average latency across all runs"""
        if not self.results:
            print("No data to plot")
            return
            
        # Collect all data points by timestamp
        timestamp_latencies = {}
        
        for result in self.results:
            for timestamp, latency in result['timeseries']:
                if timestamp not in timestamp_latencies:
                    timestamp_latencies[timestamp] = []
                timestamp_latencies[timestamp].append(latency)
        
        # Calculate averages and standard deviations
        timestamps = sorted(timestamp_latencies.keys())
        avg_latencies = []
        std_latencies = []
        
        for ts in timestamps:
            latencies = timestamp_latencies[ts]
            avg_latencies.append(np.mean(latencies))
            std_latencies.append(np.std(latencies))
        
        plt.figure(figsize=(15, 8))
        
        # Plot average with error bars
        plt.errorbar(timestamps, avg_latencies, yerr=std_latencies, 
                    capsize=2, capthick=1, alpha=0.7)
        
        # Add phase boundaries
        phase_boundaries = [
            (0, 10000, "Baseline (1K ops/sec)"),
            (10000, 20000, "Moderate (5K ops/sec)"),
            (20000, 30000, "High (20K ops/sec)"),
            (30000, 40000, "VeryHigh (50K ops/sec)"),
            (40000, 50000, "Extreme (100K ops/sec)")
        ]
        
        for start, end, label in phase_boundaries:
            plt.axvline(x=start, color='red', linestyle='--', alpha=0.5)
            plt.axvline(x=end, color='red', linestyle='--', alpha=0.5)
            
            # Add phase labels
            mid_point = (start + end) / 2
            plt.text(mid_point, max(avg_latencies) * 0.9, label, 
                    rotation=90, ha='center', va='top', fontsize=8)
        
        plt.xlabel('Time (ms)')
        plt.ylabel('Average Latency (μs)')
        plt.title(f'YCSB Dynamic Load Test - Average Latency Over Time ({self.num_runs} runs)')
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig(save_file, dpi=300, bbox_inches='tight')
        print(f"Average plot saved to {save_file}")
        plt.show()
    
    def save_raw_data(self, save_file="latency_data.json"):
        """Save raw data to JSON file"""
        with open(save_file, 'w') as f:
            json.dump(self.results, f, indent=2)
        print(f"Raw data saved to {save_file}")
    
    def print_summary(self):
        """Print summary statistics"""
        if not self.results:
            print("No data to summarize")
            return
            
        print("\n" + "="*50)
        print("LATENCY ANALYSIS SUMMARY")
        print("="*50)
        
        for result in self.results:
            timeseries = result['timeseries']
            if not timeseries:
                continue
                
            latencies = [t[1] for t in timeseries]
            
            print(f"\nRun {result['run_id']}:")
            print(f"  Data points: {len(latencies)}")
            print(f"  Min latency: {min(latencies):.2f} μs")
            print(f"  Max latency: {max(latencies):.2f} μs")
            print(f"  Avg latency: {np.mean(latencies):.2f} μs")
            print(f"  95th percentile: {np.percentile(latencies, 95):.2f} μs")
        
        # Overall statistics
        all_latencies = []
        for result in self.results:
            all_latencies.extend([t[1] for t in result['timeseries']])
        
        if all_latencies:
            print(f"\nOverall Statistics ({len(all_latencies)} total data points):")
            print(f"  Min latency: {min(all_latencies):.2f} μs")
            print(f"  Max latency: {max(all_latencies):.2f} μs")
            print(f"  Avg latency: {np.mean(all_latencies):.2f} μs")
            print(f"  95th percentile: {np.percentile(all_latencies, 95):.2f} μs")


def main():
    """Main function"""
    import argparse
    
    parser = argparse.ArgumentParser(description='YCSB Dynamic Load Latency Analyzer')
    parser.add_argument('--runs', type=int, default=4, help='Number of test runs (default: 3)')
    parser.add_argument('--script', default='./test.sh', help='Test script path (default: ./test.sh)')
    parser.add_argument('--no-plot', action='store_true', help='Skip plotting')
    
    args = parser.parse_args()
    
    # Check if test script exists
    if not os.path.exists(args.script):
        print(f"Error: Test script '{args.script}' not found")
        sys.exit(1)
    
    # Make sure test script is executable
    os.chmod(args.script, 0o755)
    
    # Create analyzer
    analyzer = YCSBLatencyAnalyzer(test_script=args.script, num_runs=args.runs)
    
    try:
        # Run tests
        analyzer.run_all_tests()
        
        # Save raw data
        analyzer.save_raw_data()
        
        # Print summary
        analyzer.print_summary()
        
        # Generate plots
        if not args.no_plot:
            analyzer.plot_latency_over_time()
            analyzer.plot_average_latency()
        
    except KeyboardInterrupt:
        print("\nAnalysis interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"Error during analysis: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main() 