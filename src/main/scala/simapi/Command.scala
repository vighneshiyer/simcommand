package simapi

import chisel3._
import chiseltest._

import collection.mutable

// Below is inspired by the trampolined continuation in TailCalls.scala (in the Scala stdlib)
object Command {
  /**
    * This class represents an RTL simulation command and its return value
    * @tparam R Type of the command's return value
    */
  sealed abstract class Command[+R] { // TODO: this should be a covariant type param
    // def fork(next => Command[R2]): Command[R2]
    // def andThen[R2](next: => Command[R2]) = {}
    // TODO: have join_all - default and join_any functionality
    final def map[R2](f: R => R2): Command[R2] = {
      flatMap(r => Return(f(r)))
    }

    // flatMap is NOT stack safe
    final def flatMap[R2](f: R => Command[R2]): Command[R2] = {
      this match {
        case Return(retval) => f(retval)
        // a1: Any
        // a2: R
        // c.a: R1 (hidden)
        // c.f: R1 (hidden) => R
        case c: Cont[a1, a2] => Cont(c.a, (x: a1) => c.f(x) flatMap f)
        case c: Command[R] => Cont(c, f)
      }
    }
  }

  // Command sum type
  // Internal classes representing DUT interaction
  protected case class Poke[I <: Data](signal: I, value: I) extends Command[Unit]
  protected case class Peek[I <: Data](signal: I) extends Command[I]

  // Internal class representing simulator synchronization points
  protected case class Step(cycles: Int) extends Command[Unit]

  // Internal class representing the end of a command sequence
  protected case class Return[R](retval: R) extends Command[R]

  // Internal class representing a continuation
  protected case class Cont[R1, R2](a: Command[R1], f: R1 => Command[R2]) extends Command[R2]

  // Internal classes representing fork/join synchronization
  protected case class ThreadHandle[R](id: Int)
  // semantics of Fork:
  // the thread calling Fork continues execution to the next block until a step is seen
  // then the Fork'ed thread will execute until a step is seen and hand back control to the main thread, and so forth until the forked thread returns
  protected case class Fork[R](c: Command[R], name: String) extends Command[ThreadHandle[R]] {
    def makeThreadHandle(id: Int): ThreadHandle[R] = ThreadHandle[R](id)
  }
  // semantics of Join:
  // the thread calling Join on another thread will block (allow infinite time advancement) until the thread being joined is complete and returns a value
  protected case class Join[R](threadHandle: ThreadHandle[R]) extends Command[R]

  // Internal classes representing an inter-thread communication channel
  // protected case class ChannelHandle[T](id: Int)
  // protected case class MakeChannel[T]() extends Command[ChannelHandle[T]]
  // protected case class Put[T](chan: ChannelHandle[T], data: T) extends Command[Unit]
  // protected case class GetBlocking[T](chan: ChannelHandle[T]) extends Command[T]
  // protected case class NonEmpty[T](chan: ChannelHandle[T]) extends Command[Boolean]

  // Public API
  // tailRecM will continually call f until it returns Command[Right]
  // @tailrec - not tail recursive, but still stack safe
  final def tailRecM[R, R2](r: R)(f: R => Command[Either[R, R2]]): Command[R2] = {
    // see implementation for Free: https://github.com/typelevel/cats/pull/1041/files#diff-7349edfd077f9612f7181fe1f8caca63ac667c847ce83b53dceae4d08040fd55
    f(r).flatMap {
      case Left(value) => tailRecM(value)(f) // recursion here is lazy so the stack won't blow up
      case Right(value) => lift(value)
    }
    /*
    f(r) match {
      case Return(Left(value)) => tailRecM(value)(f)
      case Return(Right(value)) => lift(value)
      // a1: Any
      // a2: Either[R, R2]
      // c.a: Command[Any]
      // c.b: Any => Command[Either[R, R2]]
      //case c: Cont[a1, a2] => c //tailRecM(r)(c)c.a ???
      case c @ Cont(x1, x2) =>
        for {
          x1Ret <- x1
          either <- x2(x1Ret)
          x <- either match {
            case Left(r) => tailRecM(r)(f) // not in tail position
            case Right(r) => lift(r)
          }
        } yield x
      //case Poke(signal, value) => ???
      //case Peek(signal) => ???
      //case Step(cycles) => ???
      //case Fork(c, name) => ???
      //case Join(threadHandle) => ???
    }
     */
  }

  def poke[I <: Data](signal: I, value: I): Command[Unit] = {
    Poke(signal, value)
  }

  def peek[I <: Data](signal: I): Command[I] = {
    Peek(signal)
  }

  def step[I <: Data](cycles: Int): Command[Unit] = {
    Step(cycles)
  }

  def lift[R](value: R): Command[R] = {
    Return(value)
  }

  def noop(): Command[Unit] = {
    lift(())
  }

  def fork[R](cmd: Command[R], name: String): Command[ThreadHandle[R]] = {
    Fork(cmd, name)
  }

  def join[R](handle: ThreadHandle[R]): Command[R] = {
    Join(handle)
  }

  // Runtime
  case class Result[R](retval: R, cycles: Int, threadsSpawned: Int)

  private class ThreadIdGenerator {
    var count = 0
    def getNewThreadId: Int = {
      count = count + 1
      count
    }
  }
  private case class ThreadData(cmd: Command[_], name: String, id: Int)
  private case class InterpreterCfg(print: Boolean)
  private case class EC(clock: Clock, threads: Seq[ThreadData])

  def unsafeRun[R](cmd: Command[R], clock: Clock, print: Boolean): Result[R] = {
    runInner(cmd, clock, print, new ThreadIdGenerator())
  }

  private def completedThread(t: ThreadData): Boolean = {
    t match {
      case ThreadData(Return(_), _, _) => true
      case _ => false
    }
  }

  private def debug(cfg: InterpreterCfg, thread: ThreadData, time: Int, message: String): Unit = {
    if (cfg.print) println(s"[$time] [${thread.name} (${thread.id})]: $message")
  }

  private def runInner[R](cmd: Command[R], clock: Clock, print: Boolean, threadIdGen: ThreadIdGenerator): Result[R] = {
    val cfg = InterpreterCfg(print)
    var time = 0
    var ec = EC(clock, Seq(ThreadData(cmd, "MAIN", 0)))
    val retVals = mutable.Map.empty[Int, Any] // TODO: seems like a hack but I can't carry type information of inner Commands into this function
    while (true) { // Until the main thread returns
      // Go through all threads and run them until they hit a sync state
      // Do this recursively to handle new thread spawning on this timestep
      val newThreadHandles = runThreadsUntilSync(ec.threads, time, cfg, threadIdGen, retVals)

      // Remove all completed threads from the thread list
      val completedThreads = newThreadHandles.filter(completedThread)
      completedThreads.foreach {
        case t @ ThreadData(Return(retval), name, id) =>
          debug(cfg, t, time, s"Return to top-level with value $retval")
          retVals(id) = retval
        case _ => Predef.assert(false, "Interpreter error")
      }
      val nextThreadHandles = newThreadHandles.filter{t => !completedThread(t)}

      // See if the main thread has finished
      assert(newThreadHandles.head.name == "MAIN")
      val done = newThreadHandles.head match {
        case ThreadData(Return(retval), _, _) => Some(retval)
        case _ => None
      }

      // If the main thread returns, we are done
      if (done.isDefined) {
        debug(cfg, ec.threads.head, time, s"Main thread returned at time $time with value ${done.get}")
        if (ec.threads.length > 1) debug(cfg, ec.threads.head, time, s"[FISHY] Main thread returning while child threads ${ec.threads.tail} aren't finished!")
        return Result(done.get.asInstanceOf[R], time, retVals.keys.size)
      }

      // Are there any threads waiting on a join and we have data ready for it
      /*
      val nextThreadHandlesAfterJoin: Seq[ThreadData] = nextThreadHandles.map {
        case t @ ThreadData(Join(threadHandle), name, id) =>
          debug(cfg, t, time, s"Waiting on join from ${threadHandle.id}")
          if (retVals.contains(threadHandle.id)) { // the thread that's being waited on has returned
            val (newHandle, cycles, newThreads) = runUntilSync(t.copy(cmd = next(retVals(threadHandle.id))), time, cfg, Seq.empty, threadIdGen)
            assert(newThreads.isEmpty) // I'll support spawning from join return later
            newHandle
          } else { // the thread that's being waited on hasn't returned yet
            t
          }
        case t @ ThreadData(_, _, _) => t
      }
       */

      // Advance time by 1 cycle (TODO: do we need to know the cycle advancement for each thread?)
      ec.clock.step(1)
      debug(cfg, ec.threads.head, time, "Stepping 1 cycle")
      time = time + 1

      //ec = ec.copy(threads=nextThreadHandlesAfterJoin)
      ec = ec.copy(threads=nextThreadHandles)
    }
    ???
  }

  private def runThreadsUntilSync(threads: Seq[ThreadData], time: Int, cfg: InterpreterCfg, threadIdGen: ThreadIdGenerator, retvals: mutable.Map[Int, Any]): Seq[ThreadData] = {
    val iteration = threads.map { t =>
      val (newThreadHandle, cycles, newThreads) = runUntilSync(t, time, cfg, Seq.empty, threadIdGen, retvals)
      Predef.assert(cycles == 1 || cycles == 0)
      (newThreadHandle, cycles, newThreads)
    }
    if (iteration.map(_._3.length).sum == 0) { // no new threads spawned, we're done
      iteration.map(_._1)
    } else { // all the current threads are done, but they have spawned new threads which need to be run until a syncpoint is hit
      val existingThreads = iteration.map(_._1)
      val newThreads = iteration.flatMap(_._3)
      val newThreadsRun = runThreadsUntilSync(newThreads, time, cfg, threadIdGen, retvals)
      existingThreads ++ newThreadsRun
    }
  }

  private def runUntilSync(thread: ThreadData, time: Int, cfg: InterpreterCfg, newThreads: Seq[ThreadData], threadIdGen: ThreadIdGenerator, retvals: mutable.Map[Int, Any]): (ThreadData, Int, Seq[ThreadData]) = {
    thread.cmd match {
      case Cont(a, next) =>
        val retval = runUntilSync(thread.copy(cmd=a), time, cfg, newThreads, threadIdGen, retvals)
        // retval will contain a Return() or a Command[_] from a pending step which means 'a' is not yet complete
        //debug(cfg, thread, time, s"[Cont] Got retval $retval")
        retval match {
          case (ThreadData(Return(retval), _, _), 0, newTs) => // a is 'complete' so continue executing next
            //debug(cfg, thread, time, s"[Cont] a is complete, executing next")
            runUntilSync(thread.copy(cmd=next(retval)), time, cfg, newTs, threadIdGen, retvals) // TODO: recursive thread spawning won't work without appending newThreads to newTs
          case (ThreadData(c: Command[_], _, _), cycles, newTs) => // a has hit a step so we should save next in a new Concat
            //debug(cfg, thread, time, s"[Cont] a is not complete, saving pointer at $c")
            (thread.copy(cmd=Cont(c, next)), cycles, newTs)
        }
      case f @ Fork(c, name) =>
        val forkedThreadId = threadIdGen.getNewThreadId
        val forkedThreadHandle = f.makeThreadHandle(forkedThreadId)
        debug(cfg, thread, time, s"[Fork] Forking thread $name ($forkedThreadId)")
        runUntilSync(thread.copy(cmd=Return(forkedThreadHandle)), time, cfg, newThreads :+ ThreadData(c, name, forkedThreadId), threadIdGen, retvals)
        // runUntilSync(thread.copy(cmd=next(forkedThreadHandle)), time, cfg, newThreads :+ ThreadData(c, name, forkedThreadId), threadIdGen)
      case Step(cycles) =>
        if (cycles == 0) { // this Step is a nop
          debug(cfg, thread, time, "[Step] 0 cycles (NOP)")
          //runUntilSync(thread.copy(cmd=noop()), time, cfg, newThreads, threadIdGen, retvals)
          ???
        } else if (cycles == 1) { // this Step will complete in 1 more cycle
          debug(cfg, thread, time, "[Step] 1 cycle")
          (thread.copy(cmd=noop()), 1, newThreads)
        } else { // this Step requires 2 or more cycles to complete
          debug(cfg, thread, time, "[Step] 1 cycle")
          (thread.copy(cmd=Step(cycles - 1)), 1, newThreads)
        }
      case Poke(signal, value) =>
        debug(cfg, thread, time, s"[Poke] $signal <- $value")
        signal.poke(value)
        runUntilSync(thread.copy(cmd=noop()), time, cfg, newThreads, threadIdGen, retvals)
      case Peek(signal) =>
        val value = signal.peek()
        debug(cfg, thread, time, s"[Peek] $signal -> $value")
        runUntilSync(thread.copy(cmd=Return(value)), time, cfg, newThreads, threadIdGen, retvals)
      case Return(retval) =>
        debug(cfg, thread, time, s"[Return] $retval")
        (thread.copy(cmd=Return(retval)), 0, newThreads)
      case Join(threadHandle) =>
        debug(cfg, thread, time, s"[Join] Requesting join on thread ${threadHandle.id}")
        if (retvals.contains(threadHandle.id)) {
          // TODO: this isn't right - you don't necessarily need to step a cycle before a join can take place (it can happen on this same timestep)
          (thread.copy(cmd=Return(retvals(threadHandle.id))), 1, newThreads)
        } else { // We're stil waiting on the join
          (thread, 1, newThreads)
        }
    }
  }
}
