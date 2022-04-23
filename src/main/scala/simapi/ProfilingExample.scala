package simapi

import chisel3._
import chisel3.util.Counter
import chiseltest.RawTester._
import chiseltest.{WriteVcdAnnotation, VerilatorBackendAnnotation, testableClock}
import chiseltest.internal.NoThreadingAnnotation
import Helpers._

object ProfilingExample extends App {
  class LongDelay extends Module {
    val a = IO(Output(Bool()))
    val c = Counter(2 << 16)
    c.inc()
    a := c.value > 100000.U
  }

  test(new LongDelay(), Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, NoThreadingAnnotation)) { c =>
    val program = waitForValue(c.a, 1.B)
    c.clock.setTimeout(200000)
    val result = Command.unsafeRun(program, c.clock, false)
    assert(result.cycles > 100000)
    assert(result.threadsSpawned == 1)
  }
}
