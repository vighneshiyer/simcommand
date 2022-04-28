package simcommand

import chisel3._
import chisel3.experimental.{DataMirror, Direction}
import Command._
import Combinators._
import Helpers._

class UARTCommands(uartIn: chisel3.Bool, uartOut: chisel3.Bool, cyclesPerBit: Int) {
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

  def sendReset(): Command[Unit] = {
    // Keep idle high for an entire symbol period to reset any downstream receivers
    for {
      _ <- poke(uartIn, 1.B)
      _ <- step(cyclesPerBit * (bitsPerSymbol + 1))
    } yield ()
  }

  def sendBit(bit: Int): Command[Unit] = {
    for {
      _ <- poke(uartIn, bit.B)
      _ <- step(cyclesPerBit)
    } yield ()
  }

  def sendByte(byte: Int): Command[Unit] = {
    for {
      _ <- sendBit(0)
      _ <- concat((0 until 8).map(i => sendBit((byte >> i) & 0x1)))
      _ <- sendBit(1)
      _ <- {
        println(s"Sent byte $byte")
        noop()
      }
    } yield ()
  }

  def sendBytes(bytes: Seq[Int]): Command[Unit] = {
    val cmds = bytes.map(b => sendByte(b))
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

  def receiveBit(): Command[Int] = {
    // Assuming that a start bit has already been seen and current time is at the midpoint of the start bit
    for {
      _ <- step(cyclesPerBit)
      b <- peek(uartOut)
    } yield b.litValue.toInt
  }

  def receiveByte(): Command[Int] =
    for {
      _ <- waitForValue(uartOut, 0.U) // wait until start bit is seen // TODO: reduce polling frequency
      _ <- step(cyclesPerBit / 2) // shift time to center-of-symbol
      bits <- sequence(Seq.fill(8)(receiveBit()))
      _ <- step(cyclesPerBit + cyclesPerBit / 2) // advance time past 1/2 of last data bit and stop bit
      byte = bits.zipWithIndex.foldLeft(0) {
        case (byte, (bit, index)) => byte | (bit << index)
      }
      _ <- {
        println(s"Received byte $byte")
        noop()
      }
    } yield byte

  def receiveBytes(nBytes: Int): Command[Seq[Int]] = {
    val cmds = Seq.fill(nBytes)(receiveByte())
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
