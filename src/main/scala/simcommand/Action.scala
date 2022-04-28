package simcommand

sealed trait Action
case class StepAction(cycles: Int) extends Action
case class PokeAction(signal: String, value: BigInt) extends Action
