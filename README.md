# Simulation Command API for Fast Multithreaded RTL Testbenches

This repo contains an implementation of a simulation command monad in Scala that is used with [chiseltest](https://github.com/ucb-bar/chiseltest) to describe and execute multithreaded RTL simulations with minimal threading overhead.

## Command API


## Testbenches
- DecoupledGCD: artificial example with 2 ready-valid interfaces
  - `gcd.DecoupledGcdChiseltestTester` (chiseltest with standard threading, chiseltest with manually interleaved threads, chiseltest with Command API, chiseltest with manually interleaved threads + raw simulator API) (verilator + treadle)
  - `cocotb/gcd_tb` (cocotb)
- NeuromorphicProcessor: system-level testbench with top-level UART interaction
  - `neuroproc.systemtests.NeuromorphicProcessorChiseltestTester` (chiseltest with standard threading API)
  - `neuroproc.systemtests.NeuromorphicProcessorManualThreadTester` (chiseltest with single threaded backend, manually interleaved threading, and Chisel API)
  - `neuroproc.systemtests.NeuromorphicProcessorRawSimulatorTester` (chiseltest with single threaded backend, manually interleaved threading, and raw FIRRTL API)
  - `neuroproc.systemtests.NeuromorphicProcessorCommandTester` (chiseltest with single threaded backend, and Command interpreter providing threading support)
  - `cocotb/testbench` (cocotb)

### Benchmark Results
| Simulation API                                     | DecoupledGCD      | NeuromorphicProcessor |
|----------------------------------------------------|-------------------|-----------------------|
| cocotb                                             | 3.8 kHz, 43.2 sec | 9.9 kHz, 89:38 min    |
| Chiseltest with threading                          | 7.8 kHz, 21 sec   | 32.6 kHz, 27:21 min   |
| Command API                                        | 67 kHz, 2.4 sec   | 165 kHz, 5:23 min     |
| Chiseltest with manual threading                   | 218 kHz, 0.75 sec | 432 kHz, 2:03 min     |
| Chiseltest with manual threading + raw FIRRTL API} |                   | 453 kHz, 1:58 min     |

## Repository Notes
This repo was forked from [hansemandse/KWSonSNN](https://github.com/hansemandse/KWSonSNN) for the purposes of benchmarking the system-level chiseltest testbench `neuroproc.systemtests.NeuromorphicProcessorChiseltestTester` against other implementations (manually interleaved threads with chiseltest, the Command API, and cocotb).
Once the Command API library is mature it will be moved into its own repo.

## Caveats for cocotb NeuromorphicProcessor Testbench
- Use `timescale 1ps/1ps` at the top of `NeuromorphicProcessor.sv` to match chiseltest
- Use a 2ps period clock to match chiseltest
- For iverilog
  - Remove `@(*)` after `always_latch` in `ClockBufferBB.sv` (event sensitivity lists are automatically inferred)
  - Add iverilog vcd dumping to `NeuromorphicProcessor.sv` if needed
  ```verilog
  `ifdef COCOTB_SIM
  initial begin
    $dumpfile ("NeuromorphicProcessor.vcd");
    $dumpvars (0, NeuromorphicProcessor);
    #1;
  end
  `endif
  ```