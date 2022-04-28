# Simulation Command API for Fast Multithreaded RTL Testbenches

This repo contains an implementation and benchmarks of a simulation command monad in Scala that is used with [chiseltest](https://github.com/ucb-bar/chiseltest) to describe and execute multithreaded RTL simulations with minimal threading overhead.

## Docs
### Intro
SimCommand is a library for writing multi-threaded high-performance RTL testbenches in Scala.
It is primarily designed for testing circuits [written in Chisel](https://github.com/chipsalliance/chisel3), but can also be used with [Chisel's Verilog blackboxes](https://www.chisel-lang.org/chisel3/docs/explanations/blackboxes.html) to test any single-clock synchronous Verilog RTL.
This library depends on [chiseltest](https://www.chisel-lang.org/chiseltest/) ([repo](https://github.com/ucb-bar/chiseltest)), which is a Scala library for interacting with RTL simulators (including treadle, Verilator, VCS).

### A Simple Example
Let's test this simple Chisel circuit of a register sitting between a 32-bit input and output.
```scala
import chisel3._
class Register extends Module {
  val in = IO(Input(UInt(32.W)))
  val out = IO(Output(UInt(32.W)))
  out := RegNext(in)
}
```

You can use the chiseltest `test` function to elaborate the `Register` circuit, compile an RTL simulation, and get access to a handle to the DUT:
```scala
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
class RegisterTester extends AnyFlatSpec with ChiselScalatestTester {
  test(new Register()) { dut =>
    // testing code here
  }
}
```

The core datatype of SimCommand is `Command[R]` which is a *description* of an interaction with the DUT.
The type parameter `R` is type of the value that a `Command` will terminate with.
There are several user functions that can be used to construct a `Command` such as `peek(signal)`, `poke(signal, value)` and `step(numCycles)`.

```scala
import chiseltest._
import simcommand._
test(new Register()) { dut =>
  val poker: Command[Unit] = poke(dut.in, 100.U)
  val stepper: Command[Unit] = step(cycles=1)
  val peeker: Command[UInt] = peek(dut.out)
}
```

Note that constructing the `peeker`, `poker`, and `stepper` `Command`s doesn't actually do anything - each of these values just *describes* a simulator interaction, but doesn't perform it.
This is in contrast to chiseltest's `poke`, `peek` and `step` functions which *eagerly perform* their associated actions.

Note that `poke` and `step` both return `Command[Unit]` which indicates that they terminate with `(): Unit` (since they have no information to return to the testbench).
In contrast, `peek` returns `Command[I]` where `I` is the type of the signal being peeked.
In this example, `I` is a Chisel `UInt`: a hardware unsigned integer.

To actually run any of these commands, we have to explicitly call `unsafeRun` which calls the underlying command in chiseltest.
```scala
val poker: Command[Unit] = poke(dut.in, 100.U)
val dummy1: Unit = unsafeRun(poker, dut.clock)

val stepper: Command[Unit] = step(cycles=1)
val dummy2: Unit = unsafeRun(stepper, dut.clock)

val peeker: Command[UInt] = peek(dut.out)
val value: UInt = unsafeRun(peeker, dut.clock)
val correctBehavior = value.litValue == 100
```

Of course, this is tedious, so we want a way to group multiple `Command`s sequentially so that we can call `unsafeRun` only once at the very end of our testbench description.

### Chaining Commands
`Command[R]` has two functions defined on it:
  - `flatMap[R2](f: R => Command[R2]): Command[R2]` which allows one to 'unwrap' the `R` from a `Command[R]` and continue with another `Command[R2]`
  - `map[R2](f: R => R2): Command[R2]` which maps the inner value of type `R` to a value of type `R2` via `f`

Let's use these functions to chain the `Command`s from the previous example into a single `Command`, which terminates with a Boolean which is true if the circuit behaved correctly.
```scala
val program: Command[Boolean] =
  poke(dut.in, 100.U).flatMap { _: Unit =>
    step(1).flatMap { _: Unit =>
      peek(dut.out).map { value: UInt =>
        value.litValue == 100
      }
    }
  }
```

Notice how `flatMap` is used to 'extract' the return value from a `Command` and follow it up with another `Command`.
The inner-most call to `peek` is followed by a `map` which extracts the return value of the peek and evaluates a function to return a `Boolean`.

In Scala, for-comprehensions are syntactic sugar for expressing nested calls to `flatMap` followed by a final call to `map`.
The code above can be expressed like this:
```scala
val program: Command[Boolean] = for {
  _ <- poke(dut.in, 100.U)
  _ <- step(1)
  value <- peek(dut.out)
} yield value.litValue == 100
```

Now our `program` looks a lot like a sequence of imperative statements - *but* it actually is just a description of a simulation program - it is a *value* which can be interpreted by the SimCommand runtime.

### Multithreading

### Runtime / Scheduler


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