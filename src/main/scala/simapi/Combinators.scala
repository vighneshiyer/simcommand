package simapi

import Command._

// Command combinators (functions that take Commands and return Commands)
object Combinators {
  def repeat(cmd: Command[Unit], n: Int): Command[Unit] = {
    def inner(cmd: Command[Unit], n: Int, count: Int): Command[Unit] = {
      if (count == n) noop()
      else
        for {
          _ <- cmd
          _ <- inner(cmd, n, count + 1)
        } yield ()
    }
    inner(cmd, n, 0)
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

  def concat[R](cmds: Seq[Command[R]]): Command[Unit] = {
    sequence(cmds).map(_ => ())
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

  def ifThenElse[R](cond: Command[Boolean], ifTrue: Command[R], ifFalse: Command[R]): Command[R] = {
    for {
      c <- cond
      result <- {
        if (c) ifTrue
        else ifFalse
      }
    } yield result
  }
}
