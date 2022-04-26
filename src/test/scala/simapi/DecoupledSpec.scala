package simapi

import chisel3._
import chisel3.util.Queue
import chiseltest.{ChiselScalatestTester, WriteVcdAnnotation}
import chiseltest.internal.NoThreadingAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import Command._

class DecoupledSpec extends AnyFlatSpec with ChiselScalatestTester {
  "decoupled commands" should "drive and fetch from a FIFO" in {
    val proto = UInt(16.W)
    val data = (0 until 100).map(_.U)
    test(new Queue(proto, 16)).withAnnotations(Seq(NoThreadingAnnotation, WriteVcdAnnotation)) { c =>
      val enqCmds = new DecoupledCommands(c.io.enq)
      val deqCmds = new DecoupledCommands(c.io.deq)
      val test = for {
        enqThread <- fork(enqCmds.enqueueSeq(data), "enqueue")
        _ <- step(10)
        deqThread <- fork(deqCmds.dequeueN(data.length), "dequeue")
        _ <- join(enqThread)
        out <- join(deqThread)
      } yield out
      val result = Command.unsafeRun(test, c.clock, false)
      assert(result.retval.map(_.litValue) == data.map(_.litValue))
    }
  }
}
