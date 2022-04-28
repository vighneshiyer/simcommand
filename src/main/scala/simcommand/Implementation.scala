package simcommand

import chisel3._
import chiseltest._
/*
trait Implementation {
  def getSignal[I <: Data](signal: I): I
  def setSignal[I <: Data](signal: I, value: I): Unit
  def step(cycles: Int): Unit
}

class DummyImpl extends Implementation {
  override def getSignal[I](signal: I): I = 0
  override def setSignal(signal: String, value: BigInt): Unit = {}
  override def step(cycles: Int): Unit = {}
}

class SignalListImpl(sigMap: Map[String, Seq[Int]]) extends Implementation {
  var cycle = 0

  override def getSignal(signal: String): BigInt = sigMap(signal)(cycle)
  override def setSignal(signal: String, value: BigInt): Unit = {}
  override def step(cycles: Int): Unit = cycle = cycle + cycles
}

class ChiselTestImpl(intf: Record) extends Implementation {
  override def getSignal(signal: String): BigInt = intf.elements(signal).peek().litValue
  override def setSignal(signal: String, value: BigInt): Unit = intf.elements(signal).poke(value)
}
*/