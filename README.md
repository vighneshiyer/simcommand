# Simulation Command API for Fast Multithreaded RTL Testbenches

SimCommand is a library for writing multi-threaded high-performance RTL testbenches in Scala.
It is primarily designed for testing circuits [written in Chisel](https://github.com/chipsalliance/chisel3), but can also be used with [Chisel's Verilog blackboxes](https://www.chisel-lang.org/chisel3/docs/explanations/blackboxes.html) to test any single-clock synchronous Verilog RTL.
This library depends on [chiseltest](https://www.chisel-lang.org/chiseltest/) ([repo](https://github.com/ucb-bar/chiseltest)), which is a Scala library for interacting with RTL simulators (including treadle, Verilator, and VCS).

## Docs

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
The type parameter `R` is the type of the value that a `Command` will terminate with.
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

Of course, this is tedious, so we want a way to describe running multiple `Command`s sequentially so that we can call `unsafeRun` only once at the very end of our testbench description.

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
val correctBehavior: Boolean = unsafeRun(program, dut.clock)
assert(correctBehavior)
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

Now our `program` looks a lot like a sequence of imperative statements - *but* it actually is just a description of a simulation program - it is a *value* which can be interpreted and executed by the SimCommand runtime.

### Command Combinators
Expressing simulation programs as *values* means we can write functions that take *programs* as arguments and return new *programs*.
The SimCommand library comes with a set of combinators that makes it easy to compose larger testbench programs from smaller programs.

```scala
// Return a program that repeats a command n times
def repeat(cmd: Command[_], n: Int): Command[Unit]
val tenInteractions: Command[Unit] = repeat(program, n=10)

// Take a list of programs and return a new program that executes each one back-to-back
def concat[R](cmds: Seq[Command[R]]): Command[Unit]
val fiftyInteractions: Command[Unit] = concat(Seq.fill(50)(program))

// Take a list of programs and return a new program that executes each one AND aggregates their results
def sequence[R](cmds: Seq[Command[R]]): Command[Seq[R]]
val hundredInteractions: Command[Seq[Boolean]] = sequence(Seq.fill(100)(program))
```

Note that implementing looping constructs using this functional style needs to be done using recursion.
However, `flatMap` isn't stack safe, so nested recursive calls to `flatMap` will eventually blow the stack.
We have provided a set of stack safe looping constructs:

```scala
// Run this program until it returns false
def doWhile(cmd: Command[Boolean]): Command[Unit]

val program: Command[Boolean] = for {
  value <- peek(dut.out)
  _ <- step(1)
} yield value == 0
doWhile(program)

// Run this program continuously
def forever(cmd: Command[_]): Command[Nothing]
forever(program)
```

The SimCommand library also has a set of small programs that you can use to build larger ones:
```scala
// Step the clock until the signal == value
def waitForValue[I <: Data](signal: I, value: I): Command[Unit]

val program = for {
  _ <- waitForValue(dut.out, 100.U)
  _ <- poke(dut.in, 101.U)
} yield ()
```

### Multithreading
A `Command` can be forked off to begin its execution on a different simulation thread using `fork`.
This gives you a thread handle that can be used to block on that thread's return value using `join`.

Let's create one thread to drive the DUT's input while another thread observes the DUT's output.

```scala
def driver(nElems: Int, start: Int): Command[Unit] = {
  def driveOne(value: Int): Command[Unit] = for {
    _ <- poke(dut.in, i.U)
    _ <- step(1)
  } yield ()
  
  concat((start until start + nElems).map { i => driveOne(i) })
}

def receiver(nElems: Int): Command[Seq[Int]] = {
  val receiveOne(): Command[Int] = for {
    _ <- step(1)
    value <- peek(dut.out)
  } yield value.litValue.toInt
  
  concat(Seq.fill(nElems)(receiveOne()))
}
```

There are 2 user functions available:
  - `fork[R](cmd: Command[R], name: String): Command[ThreadHandle[R]]`
    - Calling `fork` gives you a `Command` that returns a `ThreadHandle` which you can use when joining
  - `join[R](handle: ThreadHandle[R]): Command[R]`
    - Calling `join` with a `ThreadHandle` describes blocking the current thread until the joining thread has termianted with a value of type `R`

```scala
val program: Command[Boolean] = for {
  drvThread: ThreadHandle[Unit] <- fork(driver(100, 0), "driver")
  recvThread: ThreadHandle[Seq[Int]] <- fork(receiver(100), "receiver")
  _ <- join(drvThread)
  vals <- join(recvThread)
} yield vals == Seq.tabulate(100)(i => i)
```
This is a `Command` that describes running these `driver` and `receiver` in parallel, waiting on both of them to complete, extracting the terminating return value from the receiver, and checking the results.

Note that you can pass `ThreadHandle`s outside the `Command` that `fork`ed the thread, but each thread can only be `join`ed once.

### Debugging and Errors
WIP

### Runtime / Scheduler
You can use `Command` and its combinators to build up a description of a complete simulation program.
Then, at the very end of your test function, you can call `unsafeRun` to execute the `Command` and get back a `Result` object, which contains the return value and some metadata about the simulation.
**You should use** the `NoThreadingAnnotation` on `chiseltest`'s test function to achieve the best performance.

```scala
import chiseltest.internal.NoThreadingAnnotation
test(new Register()).withAnnotations(Seq(NoThreadingAnnotation)) { dut =>
  val program: Command[Boolean]
  val result = unsafeRun(program, dut.clock, print=false)
  println(result.retval) // retval: Boolean
  println(result.cycles)
  println(result.threadsSpawned)
}
```

#### Algorithm
The `unsafeRun` function invokes the SimCommand interpreter which performs the following event loop:

- while(true)
  - Iterate through the list of running threads, for each thread `t` do the following until it has hit a synchronization point (`step`, `join`, or `return`) for this timestep
    - As `t` is being run, proxy any calls to `peek` or `poke` to the chiseltest API
    - If `t` hits a `fork`, add the forked thread to the list of running threads and continue running `t`
    - If `t` hits a `step`, overwrite the `t` pointer with the `Command` that comes after the `step` and record the time at which `t` will be woken up
    - If `t` hits a `join`, mark `t` as asleep until the thread being joined has returned
    - If `t` hits a `return`, add `t`'s return value to a global map and mark any threads which have requested a join on `t` to be awakened on this timestep
  - If the main thread has returned with `retval`, *exit the loop* with `retval`
  - Step the clock in the simulator

Note that the interpreter doesn't prevent intra-cycle race conditions when two threads are peeking and poking the same DUT IO.
Support is being considered for catching these issues at runtime, like the threaded chiseltest backend does.

### VIPs
There is a small library of verification IPs built using SimCommand.
They are just functions that return a `Command` that describes an interaction for a given bus specification.

#### UART
```scala
// Create an instance of UARTCommands with bindings to your DUT's UART IOs and specify the baud rate
val uartCmds = new UARTCommands(dut.uartTx, dut.uartRx, cyclesPerBit)

// Then call functions from the instance to produce Command's you can use in your testbench
uartCmds.sendByte(24) // : Command[Unit]
uartCmds.sendBytes(Seq(4, 23, 12)) // : Command[Unit]
uartCmds.receiveByte() // : Command[Int]
uartCmds.receiveBytes(n: Int) // : Command[Seq[Int]]
```

#### Ready/Valid (Decoupled)
```scala
val dut = Queue(UInt(32.W))
val enqCmds = new DecoupledCommands(dut.enq)
enqCmds.enqueue(100.U) // : Command[Unit]
enqCmds.enqueueSeq(Seq(1.U, 2.U, 3.U)) // : Command[Unit]

val deqCmds = new DecoupledCommands(dut.deq)
deqCmds.dequeue() // : Command[UInt]
deqCmds.dequeueN(n: Int) // : Command[Seq[UInt]]
```

#### TileLink
WIP

## Testbenches
This repo contains an implementation and benchmarks of a simulation command monad in Scala that is used with [chiseltest](https://github.com/ucb-bar/chiseltest) to describe and execute multithreaded RTL simulations with minimal threading overhead.

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