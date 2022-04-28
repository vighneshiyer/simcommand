package simcommand

import Command._

// Command combinators (functions that take Commands and return Commands)
object Combinators {
  def repeat(cmd: Command[_], n: Int): Command[Unit] = {
    tailRecM(0) { iteration =>
      if (iteration == n) lift(Right(()))
      else for {
        _ <- cmd
      } yield Left(iteration + 1)
    }
  }

  def repeatCollect[R](cmd: Command[R], n: Int): Command[Seq[R]] = {
    tailRecM(0, Vector.empty[R]) { case (iteration, collection) =>
      if (iteration == n) lift(Right(collection))
      else for {
        retval <- cmd
      } yield Left((iteration + 1, collection :+ retval))
    }
  }

  def doWhile(cmd: Command[Boolean]): Command[Unit] = {
    tailRecM(()) { _: Unit =>
      for {
        cond <- cmd
        retval <- {if (cond) lift(Left(())) else lift(Right(()))}
      } yield retval
    }
  }

  def doWhileCollect[R](cmd: Command[(R, Boolean)]): Command[Seq[R]] = {
    ???
  }

  def doWhileCollectLast[R](cmd: Command[(R, Boolean)]): Command[R] = {
    ???
  }

  def doWhile[R, S](cond: S => Boolean, action: Command[(R, S)], initialState: S): Command[Seq[R]] = {
    ???
  }

  def forever(cmd: Command[_]): Command[Nothing] = {
    ???
  }

  def zip[R1, R2](cmd1: Command[R1], cmd2: Command[R2]): Command[(R1, R2)] = {
    ???
  }

  def map2[R1, R2, R3](cmd1: Command[R1], cmd2: Command[R2], f: (R1, R2) => R3): Command[R3] = {
    ???
  }

  // See Cats 'Traverse' which provides 'sequence' which is exactly this type signature
  def sequence[R](cmds: Seq[Command[R]]): Command[Seq[R]] = {
    tailRecM((cmds, Vector.empty[R])) { case (cmds, retvalSeq) =>
      if (cmds.isEmpty) lift(Right(retvalSeq))
      else for {
        retval <- cmds.head
      } yield Left((cmds.tail, retvalSeq :+ retval))
    }
  }

  def traverse[R, R2](cmds: Seq[Command[R]])(f: R => Command[R2]): Seq[Command[R2]] = {
    ???
  }

  // specialization of sequence to throw away return values
  // TODO: is this called sequence_ in cats?
  def concat[R](cmds: Seq[Command[R]]): Command[Unit] = {
    tailRecM(cmds) { case cmds =>
      if (cmds.isEmpty) lift(Right(noop()))
      else for {
        _ <- cmds.head
      } yield Left(cmds.tail)
    }
  }

  def chain[R](cmds: Seq[R => Command[R]], initialRetval: R): Command[R] = {
    val initProgram: Command[R] = for {
      r <- lift(initialRetval)
      c <- cmds.head(r)
    } yield c

    cmds.tail.foldLeft(initProgram) { (c1: Command[R], c2: R => Command[R]) =>
      for {
        c1Ret <- c1
        c2Ret <- c2(c1Ret)
      } yield c2Ret
    }
  }

  // TODO: duplicate
  def ifThenElse[R](cond: Command[Boolean], ifTrue: Command[R], ifFalse: Command[R]): Command[R] = {
    for {
      c <- cond
      result <- {
        if (c) ifTrue
        else ifFalse
      }
    } yield result
  }

  // TODO: should be a member of Command[Boolean]
  def ifM[R](cond: Command[Boolean], ifTrue: Command[R], ifFalse: Command[R]): Command[R] = {
    for {
      c <- cond
      result <- {if (c) ifTrue else ifFalse}
    } yield result
  }

}
