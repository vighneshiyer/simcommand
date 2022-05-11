package gcd

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.stage.{ChiselCircuitAnnotation, ChiselGeneratorAnnotation, DesignAnnotation}
import chiseltest._
import chiseltest.internal.NoThreadingAnnotation
import chiseltest.simulator.{SimulatorAnnotation, SimulatorContext}
import firrtl.annotations.{Annotation, DeletedAnnotation}
import firrtl.options.TargetDirAnnotation
import firrtl.stage.FirrtlCircuitAnnotation
import firrtl.{AnnotationSeq, EmittedCircuitAnnotation}
import logger.LogLevelAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import simcommand.DecoupledCommands
import simcommand.Command
import simcommand.Command.Command

class DecoupledGcdChiseltestTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "DecoupledGcd"

  // play with these to adjust test execution time
  val (maxX, maxY) = (100, 100)
  val testValues = for {x <- 2 to maxX; y <- 2 to maxY} yield (BigInt(x), BigInt(y), BigInt(x).gcd(y))
  val bitWidth = 60
  val inputBundles = testValues.map { case (a, b, _) =>
    new GcdInputBundle(bitWidth).Lit(_.value1 -> a.U, _.value2 -> b.U)
  }
  val outputBundles = testValues.map { case (a, b, c) =>
    new GcdOutputBundle(bitWidth).Lit(_.value1 -> a.U, _.value2 -> b.U, _.gcd -> c.U)
  }

  def runWithChiseltestThreads(backend: SimulatorAnnotation): Unit = {
    val simName = backend.getSimulator.name

    val startElab = System.nanoTime()
    test(new DecoupledGcd(bitWidth)).withAnnotations(Seq(backend)) { dut =>
      println(s"Took ${(System.nanoTime() - startElab) / 1e9d}s to elaborate, compile and create simulation w/ $simName")
      val startTest = System.nanoTime()

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.input.initSource().setSourceClock(dut.clock)
      dut.output.initSink().setSinkClock(dut.clock)
      dut.clock.setTimeout(0)

      fork {
        dut.input.enqueueSeq(inputBundles)
      }.fork {
        dut.output.expectDequeueSeq(outputBundles)
      }.join()

      val deltaSeconds = (System.nanoTime() - startTest) / 1e9d
      println(s"Took ${deltaSeconds}s to run test with chiseltest threads w/ $simName")
    }
  }

  def runWithCommandAPI(backend: SimulatorAnnotation): Unit = {
    val simName = backend.getSimulator.name

    val startElab = System.nanoTime()
    test(new DecoupledGcd(bitWidth)).withAnnotations(Seq(backend, NoThreadingAnnotation, WriteVcdAnnotation)) { dut =>
      println(s"Took ${(System.nanoTime() - startElab) / 1e9d}s to elaborate, compile and create simulation w/ $simName")
      val startTest = System.nanoTime()
      val inputCmds = new DecoupledCommands(dut.input)
      val outputCmds = new DecoupledCommands(dut.output)

      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.setTimeout(1000)

      val program: Command[Seq[GcdOutputBundle]] = for {
        pushThread <- simcommand.Command.fork(inputCmds.enqueueSeq(inputBundles.toVector), "push")
        pullThread <- simcommand.Command.fork(outputCmds.dequeueN(outputBundles.length),"pull")
        _ <- simcommand.Command.join(pushThread)
        output <- simcommand.Command.join(pullThread)
      } yield output

      val result = Command.unsafeRun(program, dut.clock, false)
      Predef.assert(result.retval.length == outputBundles.length)
      result.retval.zip(outputBundles).foreach { case (actual, gold) =>
        Predef.assert(actual.gcd.litValue == gold.gcd.litValue)
        Predef.assert(actual.value1.litValue == gold.value1.litValue)
        Predef.assert(actual.value2.litValue == gold.value2.litValue)
      }

      val deltaSeconds = (System.nanoTime() - startTest) / 1e9d
      println(s"Took ${deltaSeconds}s to run test with command API w/ $simName")
      println(s"Executed ${result.cycles} cycles at an average frequency of ${result.cycles / deltaSeconds} Hz")
    }
  }

  private def runTestDut(dut: DecoupledGcd, testValues: Iterable[(BigInt, BigInt, BigInt)]): Long = {
    var cycles = 0L
    dut.reset.poke(true.B)
    dut.clock.step(2)
    cycles += 2
    dut.reset.poke(false.B)

    dut.output.ready.poke(true.B)
    for((i, j, expected) <- testValues) {
      dut.input.bits.value1.poke(i.U)
      dut.input.bits.value2.poke(j.U)
      dut.input.valid.poke(true.B)
      dut.clock.step(1)
      cycles += 1

      while(!dut.output.valid.peek().litToBoolean) {
        dut.clock.step(1)
        cycles += 1
      }
      dut.output.bits.gcd.expect(expected.U)
      dut.output.valid.expect(true.B)
    }

    cycles
  }

  def runWithChiseltestSingleThread(backend: SimulatorAnnotation): Unit = {
    val simName = backend.getSimulator.name
    val startElab = System.nanoTime()
    test(new DecoupledGcd(bitWidth)).withAnnotations(Seq(backend, NoThreadingAnnotation)) { dut =>
      println(s"Took ${(System.nanoTime() - startElab) / 1e9d}s to elaborate, compile and create simulation w/ $simName")
      val startTest = System.nanoTime()
      val cycles = runTestDut(dut, testValues)
      val deltaSeconds = (System.nanoTime() - startTest) / 1e9d
      println(s"Took ${deltaSeconds}s to run test with chiseltest but no threads w/ $simName")
      println(s"Executed $cycles cycles at an average frequency of ${cycles / deltaSeconds} Hz")
    }
  }


  private def runTestSim(dut: SimulatorContext, testValues: Iterable[(BigInt, BigInt, BigInt)]): Long = {
    var cycles = 0L
    dut.poke("reset", 1)
    dut.step(2)
    cycles += 2
    dut.poke("reset", 0)

    dut.poke("output_ready", 1)
    for((i, j, expected) <- testValues) {
      dut.poke("input_bits_value1", i)
      dut.poke("input_bits_value2", j)
      dut.poke("input_valid", 1)
      dut.step(1)
      cycles += 1

      while(dut.peek("output_valid") == 0) {
        dut.step(1)
        cycles += 1
      }
      assert(dut.peek("output_bits_gcd") == expected)
      assert(dut.peek("output_valid") == 1)
    }

    cycles
  }

  def runWithRawSimSingleThread(backend: SimulatorAnnotation): Unit = {
    val simName = backend.getSimulator.name

    val startElab = System.nanoTime()

    // elaborate and compile to low firrtl
    val targetDir = TargetDirAnnotation("test_run_dir/gcd_raw_sim_with_" + simName)
    val stage = new chisel3.stage.ChiselStage()
    val r = stage.run(Seq(
      ChiselGeneratorAnnotation(() => new DecoupledGcd(bitWidth)),
      targetDir,
    ))
    val state = annosToState(r)
    val dut = backend.getSimulator.createContext(state)

    println(s"Took ${(System.nanoTime() - startElab) / 1e9d}s to elaborate, compile and create simulation w/ $simName")

    val startTest = System.nanoTime()
    val cycles = runTestSim(dut, testValues)
    val deltaSeconds = (System.nanoTime() - startTest) / 1e9d
    println(s"Took ${deltaSeconds}s to run test with the raw simulator interface and no threads w/ $simName")
    println(s"Executed $cycles cycles at an average frequency of ${cycles / deltaSeconds} Hz")

  }

  // copied over from chiseltest
  private def annosToState(annos: AnnotationSeq): firrtl.CircuitState = {
    val circuit = annos.collectFirst { case FirrtlCircuitAnnotation(c) => c }.get
    val filteredAnnos = annos.filterNot(isInternalAnno)
    firrtl.CircuitState(circuit, filteredAnnos)
  }
  private def isInternalAnno(a: Annotation): Boolean = a match {
    case _: FirrtlCircuitAnnotation | _: DesignAnnotation[_] | _: ChiselCircuitAnnotation | _: DeletedAnnotation |
         _: EmittedCircuitAnnotation[_] | _: LogLevelAnnotation =>
      true
    case _ => false
  }

  it should  "work with chiseltest threads and treadle" in {
    runWithChiseltestThreads(TreadleBackendAnnotation)
  }

  it should  "work with chiseltest threads and verilator" in {
    runWithChiseltestThreads(VerilatorBackendAnnotation)
  }

  it should  "work with chiseltest single threaded and treadle" in {
    runWithChiseltestSingleThread(TreadleBackendAnnotation)
  }

  it should  "work with chiseltest single threaded and verilator" in {
    runWithChiseltestSingleThread(VerilatorBackendAnnotation)
  }

  it should "work with Command API and verilator" in {
    runWithCommandAPI(VerilatorBackendAnnotation)
  }

  it should "work with Command API and iverilog" in {
    runWithCommandAPI(IcarusBackendAnnotation)
  }

  it should "work with Command API and VCS" in {
    runWithCommandAPI(VcsBackendAnnotation)
  }

  it should  "work with raw simulator and single threaded and treadle" in {
    runWithRawSimSingleThread(TreadleBackendAnnotation)
  }

  it should  "work with raw simulator and single threaded and verilator" in {
    runWithRawSimSingleThread(VerilatorBackendAnnotation)
  }
}
