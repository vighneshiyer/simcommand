package simapi

import Command._
import chisel3._
import chiseltest._
import chisel3.util.Counter
import org.scalatest.flatspec.AnyFlatSpec

class PrimitivesSpec extends AnyFlatSpec with ChiselScalatestTester {
  "peek" should "inspect the circuit IO and get its value" in {
    class Peekable extends Module {
      val a = IO(Output(UInt(32.W)))
      val c = Counter(16)
      c.inc() // free running counter
      a := c.value
    }

    test(new Peekable()) { c =>
      val program = for {
        v1 <- peek(c.a)
        _ <- step(10)
        v2 <- peek(c.a)
        _ <- step(6)
        v3 <- peek(c.a)
        _ <- step(1)
        v4 <- peek(c.a)
      } yield (v1.litValue, v2.litValue, v3.litValue, v4.litValue)
      val retval = Command.unsafeRun(program, c.clock, false)
      assert(retval == (0, 10, 0, 1))
    }
  }

  "poke" should "drive the circuit IO" in {
    class Pokable extends Module {
      val a = IO(Input(UInt(32.W)))
      val aOut = IO(Output(UInt(32.W)))
      val aReg = RegNext(a) // pipelined loopback
      aOut := aReg
    }

    def pokeOne(signal: UInt, value: UInt): Command[Unit] =
      for {
        _ <- poke(signal, value)
        _ <- step(1)
      } yield ()

    test(new Pokable()) { c =>
      val program = for {
        _ <- pokeOne(c.a, 100.U)
        v1 <- peek(c.aOut)
        _ <- step(100)
        v2 <- peek(c.aOut)
        _ <- pokeOne(c.a, 200.U)
        v3 <- peek(c.aOut)
      } yield (v1.litValue, v2.litValue, v3.litValue)
      val retval = Command.unsafeRun(program, c.clock, false)
      assert(retval == (100, 100, 200))
    }
  }
}
