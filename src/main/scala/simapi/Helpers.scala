package simapi

import chisel3._
import Command._
import Combinators._

object Helpers {
  // Helper commands
  def waitForValue[I <: Data](signal: I, value: I): Command[Unit] = {
    // TODO: return # of cycles this program waited
    for {
      peekedValue <- peek(signal)
      next <- {
        if (peekedValue.litValue != value.litValue) { // TODO: this won't work for record types
          for {
            _ <- step(1)
            _ <- waitForValue(signal, value)
          } yield ()
        } else {
          noop()
        }
      }
    } yield next
  }

  def checkSignal[I <: Data](signal: I, value: I): Command[Boolean] = {
    for {
      peeked <- peek(signal)
      _ <- {
        if (peeked.litValue != value.litValue) {Predef.assert(false, s"Signal $signal wasn't the expected value $value")}
        step(1)
      }
    } yield peeked.litValue == value.litValue
  }

  def checkStable[I <: Data](signal: I, value: I, cycles: Int): Command[Boolean] = {
    val checks = Seq.fill(cycles)(checkSignal(signal, value))
    val allPass = sequence(checks).map(_.forall(b => b))
    allPass // TODO: move 'expect' into Command sum type, unify with print (e.g. info), and other debug prints
  }
}
