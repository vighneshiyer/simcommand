package simapi

import chisel3._
import Command._
import Combinators._

object Helpers {
  def doWhile(cmd: Command[Boolean]): Command[Unit] = {
    tailRecM(()) { _: Unit =>
      for {
        cond <- cmd
        retval <- {if (cond) lift(Left(())) else lift(Right(()))}
      } yield retval
    }
  }

  def doWhile[R, S](cond: S => Boolean, action: Command[(R, S)], initialState: S): Command[Seq[R]] = {
    ???
  }

  def waitForValue[I <: Data](signal: I, value: I): Command[Unit] = {
    // TODO: return # of cycles this program waited
    val check: Command[Boolean] = for {
      peekedValue <- peek(signal)
      _ <- {
        if (peekedValue.litValue != value.litValue) // TODO: this won't work for record types
          step(1)
        else
          noop()
      }
    } yield peekedValue.litValue != value.litValue
    doWhile(check)
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
