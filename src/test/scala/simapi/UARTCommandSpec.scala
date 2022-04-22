package simapi

import Command._
import chisel3._
import chiseltest.{ChiselScalatestTester, WriteVcdAnnotation}
import chisel3.util.log2Ceil
import org.scalatest.flatspec.AnyFlatSpec

class UARTCommandSpec extends AnyFlatSpec with ChiselScalatestTester {
  class UARTMock(txBytes: Seq[Int], bitDelay: Int) extends Module {
    val rx = IO(Input(Bool()))
    val tx = IO(Output(Bool()))

    def byteToPerCycleBits(bitDelay: Int, byte: Int): Seq[Bool] = {
      Seq.fill(bitDelay)(1.B) ++ // Idle
        Seq.fill(bitDelay)(0.B) ++ // start bit
        (0 until 8).flatMap { i => Seq.fill(bitDelay)(((byte >> i) & 0x1).B) } ++
        Seq.fill(bitDelay)(1.B) // stop bit
    }

    if (txBytes.isEmpty) {
      tx := 1.B
    } else {
      val bitsToSend = VecInit(txBytes.flatMap(byteToPerCycleBits(bitDelay, _)))
      val cycle = RegInit(0.U(log2Ceil(bitsToSend.length).W))
      cycle := cycle + 1.U
      tx := bitsToSend(cycle)
    }
  }

  class UARTLoopback() extends Module {
    val rx = IO(Input(Bool()))
    val tx = IO(Output(Bool()))

    tx := RegNext(rx, 1.B)
  }

  "sendByte" should "produce the right sequence" in {
    val testByte = 0x55
    val bitDelay = 4
    test(new UARTMock(Seq.empty, bitDelay)) { c =>
      val cmds = new UARTCommands(uartIn=c.rx, uartOut=c.tx)
      val chkr = new UARTChecker(c.tx)
      val program = for {
        checkerHandle <- fork(chkr.checkBytes(1, bitDelay), "checker")
        _ <- cmds.sendByte(bitDelay, testByte)
        _ <- step(bitDelay*5)
        j <- join(checkerHandle)
      } yield j
      val retval = Command.unsafeRun(program, c.clock, print=false)
      assert(retval)
    }
    // TODO: no checks can be run since sendByte only pokes
  }

  /*
  "receiveByte" should "receive a single byte sent by the UART" in {
    val testByte = Seq(0x55)
    val bitDelay = 4
    test(new UARTMock(testByte, bitDelay)) { c =>
      val cmds = new UARTCommands(uartIn = c.rx, uartOut = c.tx)
      val byteReceived = Command.run(cmds.receiveByte(bitDelay), c.clock, print=false)
      assert(byteReceived == testByte.head)
    }
  }

  "receiveBytes" should "receive multiple bytes sent by the UART" in {
    val testBytes = Seq(0x55, 0xff, 0x00, 0xaa)
    val bitDelay = 4
    test(new UARTMock(testBytes, bitDelay)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val cmds = new UARTCommands(uartIn = c.rx, uartOut = c.tx)
      val bytesReceived = Command.run(cmds.receiveBytes(bitDelay, testBytes.length), c.clock, print=false)
      assert(bytesReceived == testBytes)
    }
  }

  "sendBytes" should "successfully send bytes to a forked receiveBytes through a UART loopback" in {
    val testBytes = Seq(0x00, 0x00, 0x55, 0xff, 0x00, 0xaa)
    val bitDelay = 4
    test(new UARTLoopback()).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      val cmds = new UARTCommands(uartIn = c.rx, uartOut = c.tx)
      val rxChk = new UARTChecker(c.rx)
      val txChk = new UARTChecker(c.tx)

      val sender = Concat(cmds.sendReset(bitDelay), (_: Unit) => cmds.sendBytes(bitDelay, testBytes))
      val receiver = cmds.receiveBytes(bitDelay, testBytes.length)
      val rxChecker = rxChk.check(bitDelay, testBytes.length)
      val txChecker = txChk.check(bitDelay, testBytes.length)

      val program =
        Fork(sender, "sender", (h1: ThreadHandle[Unit]) =>
          Fork(receiver, "receiver", (h2: ThreadHandle[Seq[Int]]) =>
            Fork(rxChecker, "rxChecker", (h3: ThreadHandle[Unit]) =>
              Fork(txChecker, "txChecker", (h4: ThreadHandle[Unit]) =>
                Step(bitDelay*cmds.bitsPerSymbol*(testBytes.length + 1), () =>
                  Join(h2, (retval: Seq[Int]) => Return(retval))
                )
              )
            )
          )
        )
      val bytesReceived = Command.run(program, c.clock, print=false)
      assert(bytesReceived == testBytes)
    }
  }
   */
}
