package simapi

import chisel3._
import chisel3.experimental.{DataMirror, Direction}
import Command._
import Combinators._
import Helpers._

class UARTCommands(uartIn: chisel3.Bool, uartOut: chisel3.Bool) {
  assert(DataMirror.directionOf(uartIn) == Direction.Input)
  assert(DataMirror.directionOf(uartOut) == Direction.Output)
  val bitsPerSymbol = 10
  // sending a UART byte using cocotb
  /*
  async def receiveByte(dut, bitDelay: int, byte: int):
    print("Sending byte {byte}")
    # Start bit
    dut.io_uartRx.value = 0
    await ClockCycles(dut.clock, bitDelay)
    # Byte
    for i in range(8):
      dut.io_uartRx.value = (byte >> i) & 0x1
      await ClockCycles(dut.clock, bitDelay)
    # Stop bit
    dut.io_uartRx.value = 1
    await ClockCycles(dut.clock, bitDelay)
    print("Sent byte {byte}")
  */

  def sendReset(bitDelay: Int): Command[Unit] = {
    // Keep idle high for an entire symbol period to reset any downstream receivers
    for {
      _ <- poke(uartIn, 1.B)
      _ <- step(bitDelay * (bitsPerSymbol + 1))
    } yield ()
  }

  def sendBit(bit: Int, bitDelay: Int): Command[Unit] = {
    for {
      _ <- poke(uartIn, bit.B)
      _ <- step(bitDelay)
    } yield ()
  }

  def sendByte(byte: Int, bitDelay: Int): Command[Unit] = {
    for {
      _ <- sendBit(0, bitDelay)
      _ <- concat((0 until 8).map(i => sendBit((byte >> i) & 0x1, bitDelay)))
      _ <- sendBit(1, bitDelay)
      _ <- {
        println(s"Sent byte $byte")
        noop()
      }
    } yield ()
  }

  def sendBytes(bytes: Seq[Int], bitDelay: Int): Command[Unit] = {
    val cmds = bytes.map(b => sendByte(b, bitDelay))
    concat(cmds)
  }

  // receiving a UART byte using cocotb
  /*
  async def transferByte(dut, bitDelay: int) -> int:
    print("Receiving a byte")
    byte = 0
    # Assumes start bit has already been seen
    await ClockCycles(dut.clock, bitDelay)
    # Byte
    for i in range(8):
      byte = dut.io_uartTx.value << i | byte
    await ClockCycles(dut.clock, bitDelay)
    # Stop bit
      assert dut.io_uartTx.value == 1
    await ClockCycles(dut.clock, bitDelay)
    print("Received {byte}")
    return byte
   */

  def receiveBit(bitDelay: Int): Command[Int] = {
    // Assuming that a start bit has already been seen and current time is at the midpoint of the start bit
    for {
      _ <- step(bitDelay)
      b <- peek(uartOut)
    } yield b.litValue.toInt
  }

  def receiveByte(bitDelay: Int): Command[Int] =
    for {
      _ <- waitForValue(uartOut, 0.U) // wait until start bit is seen // TODO: reduce polling frequency
      _ <- step(bitDelay / 2) // shift time to center-of-symbol
      bits <- sequence(Seq.fill(8)(receiveBit(bitDelay)))
      _ <- step(bitDelay + bitDelay / 2) // advance time past 1/2 of last data bit and stop bit
      byte = bits.zipWithIndex.foldLeft(0) {
        case (byte, (bit, index)) => byte | (bit << index)
      }
      _ <- {
        println(s"Received byte $byte")
        noop()
      }
    } yield byte

  def receiveBytes(nBytes: Int, bitDelay: Int): Command[Seq[Int]] = {
    val cmds = Seq.fill(nBytes)(receiveByte(bitDelay))
    sequence(cmds)
  }
}

class UARTChecker(serialLine: chisel3.Bool) {
  def checkByte(bitDelay: Int): Command[Boolean] = {
    for {
      _ <- waitForValue(serialLine, 0.U) // Wait for the start bit
      stableStartBit <- checkStable(serialLine, 0.U, bitDelay) // Start bit should be stable until symbol edge
      _ <- step(bitDelay*8) // Let the 8 data bits pass
      stableStopBit <- checkStable(serialLine, 1.U, bitDelay) // Stop bit should be stable until byte finished
    } yield stableStartBit && stableStopBit
  }

  def checkBytes(nBytes: Int, bitDelay: Int): Command[Boolean] = {
    val checks = Seq.fill(nBytes)(checkByte(bitDelay))
    for {
      checks <- sequence(checks)
    } yield checks.forall(b => b)
  }
}
