package simapi

import chisel3._
import chisel3.util.DecoupledIO

import Command._
import Combinators._
import Helpers._

class DecoupledCommands[T <: Data](io: DecoupledIO[T]) {
  def enqueue(data: T): Command[Unit] = for {
    _ <- poke(io.bits, data)
    _ <- poke(io.valid, true.B)
    _ <- waitForValue(io.ready, true.B)
    _ <- step(1)
    _ <- poke(io.valid, false.B)
  } yield ()

  def enqueueSeq(data: Seq[T]): Command[Unit] = {
    // TODO: replace with repeat
    val enqueueCmds: Seq[Command[Unit]] = data.map(d => enqueue(d))
    sequence(enqueueCmds.toVector).map(_ => ())
  }

  def dequeue(): Command[T] = for {
    _ <- waitForValue(io.valid, true.B)
    _ <- poke(io.ready, true.B)
    value <- peek(io.bits)
    _ <- step(1)
  } yield value

  def dequeueN(n: Int): Command[Seq[T]] = {
    // TODO: replace with repeatAndGet
    val dequeueCmds = Seq.fill(n)(dequeue())
    sequence(dequeueCmds.toVector)
  }
}
