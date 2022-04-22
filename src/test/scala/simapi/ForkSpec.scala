package simapi

import chisel3._
import chisel3.util.{ShiftRegister, Valid}
import chiseltest.{ChiselScalatestTester, WriteVcdAnnotation}
import chisel3.experimental.BundleLiterals._
import org.scalatest.flatspec.AnyFlatSpec

/*
class ForkSpec extends AnyFlatSpec with ChiselScalatestTester {
  class ValidDelayLine extends Module {
    val a = IO(Input(Valid(UInt(10.W))))
    val b = IO(Output(Valid(UInt(10.W))))
    val default = Wire(Valid(UInt(10.W)))
    default.valid := false.B
    default.bits := 0.U
    b := ShiftRegister(a, 20, default, en=1.B)
  }

  class ForkExample(a: Valid[UInt], b: Valid[UInt], proto: Valid[UInt]) {
    def poker(nElems: Int, cycles: Int = 0): Command[Unit] = {
      if (cycles == nElems) {
        Poke(a, proto.Lit(_.valid -> 0.B, _.bits -> 0.U), () =>
          Return(Unit)
        )
      }
      else
        Poke(a, proto.Lit(_.valid -> 1.B, _.bits -> cycles.U), () =>
          Step(1, () =>
            poker(nElems, cycles + 1)
          )
        )
    }

    def peeker(nElems: Int, values: Seq[Int] = Seq.empty): Command[Seq[Int]] = {
      if (values.length == nElems) Return(values)
      else
        Peek(b, (value: Valid[UInt]) =>
          if (value.valid.litToBoolean) {
            Step(1, () =>
              peeker(nElems, values :+ value.bits.litValue.toInt)
            )
          } else {
            Step(1, () => peeker(nElems, values))
          }
        )
    }

    def program(nElems: Int): Command[Seq[Int]] = {
      Step(1, () =>
        Fork(peeker(nElems), "peeker", (h2: ThreadHandle[Seq[Int]]) => // fork off peeking thread
          Fork(poker(nElems), "poker", (h1: ThreadHandle[Unit]) => // fork off poking thread
            Join(h1, (_: Unit) =>
              Join(h2, (peeked: Seq[Int]) =>
                Return(peeked)
              )
            )
          )
        )
      )
    }
  }

  "Fork" should "create a thread that operates independently of the main thread" in {
    test(new ValidDelayLine()).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val cmds = new ForkExample(c.a, c.b, Valid(UInt(10.W)))
      val retval = Command.run(cmds.program(10), c.clock, print=true)
      Predef.assert(retval == Seq.tabulate(10)(i => i))
    }
  }
}
 */
