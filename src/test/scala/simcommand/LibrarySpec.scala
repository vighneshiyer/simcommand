package simcommand

import chisel3._
import chisel3.util.Counter
import chiseltest.{ChiselScalatestTester, VerilatorBackendAnnotation, WriteVcdAnnotation, testableClock}
import chiseltest.internal.NoThreadingAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import Helpers._

class LibrarySpec extends AnyFlatSpec with ChiselScalatestTester {
  class LongDelay extends Module {
    val a = IO(Output(Bool()))
    val c = Counter(2 << 16)
    c.inc()
    a := c.value > 100000.U
  }

  "doWhile" should "not overflow the stack" in {
    test(new LongDelay()).withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation, NoThreadingAnnotation)) { c =>
      val program = waitForValue(c.a, 1.B)
      c.clock.setTimeout(200000)
      val result = Command.unsafeRun(program, c.clock, false)
      assert(result.cycles > 100000)
      assert(result.threadsSpawned == 1)
    }
  }
}