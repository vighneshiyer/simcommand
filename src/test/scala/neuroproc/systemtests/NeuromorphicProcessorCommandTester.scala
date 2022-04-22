package neuroproc.systemtests

import neuroproc._
import simapi._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import simapi.UARTCommands

import java.io.{FileNotFoundException, IOException}
/*
class NeuromorphicProcessorCommandTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Neuromorphic Processor"

  val bitDelay = FREQ / BAUDRATE + 1

  def fetch(file: String) = {
    val src = scala.io.Source.fromFile(file)
    val lines = try {
      src.mkString
    } catch {
      case e: FileNotFoundException => {
        println("Incorrect path to image file")
        ""
      }
      case e: IOException => {
        println("Cannot open image file")
        ""
      }
    } finally {
      src.close
    }
    lines.split(",").map(_.toInt)
  }

  if (!RANKORDERENC) {
    it should "process an image" taggedAs(SlowTest) in {
      val annos = Seq(
        VerilatorBackendAnnotation,
        WriteVcdAnnotation,
        chiseltest.internal.NoThreadingAnnotation,
      )

      // Reference image and results
      val image = fetch("./src/test/scala/neuroproc/systemtests/image.txt")
      val results = if (USEROUNDEDWGHTS)
        fetch("./src/test/scala/neuroproc/systemtests/results_round.txt")
      else
        fetch("./src/test/scala/neuroproc/systemtests/results_toInt.txt")

      test(new NeuromorphicProcessor()).withAnnotations(annos) { dut =>
        dut.clock.setTimeout(FREQ)

        // Reset inputs
        dut.io.uartRx.poke(true.B)
        dut.io.uartTx.expect(true.B)
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)
        dut.io.uartTx.expect(true.B)

        // Load an image into the accelerator ...
        val bytes = image.indices.flatMap { i =>
          Seq((i >> 8) & 0xff, i & 0xff, (image(i) >> 8) & 0xff, image(i) & 0xff)
        }
        val commands = new UARTCommands(dut.io.uartRx, dut.io.uartTx)
        val receiver = commands.receiveBytes(bitDelay, 110)
        val sender = commands.sendBytes(bitDelay, bytes)

        val program: Command[Seq[Int]] =
          Fork(receiver, "receiver", (r: ThreadHandle[Seq[Int]]) =>
            Fork(sender, "sender", (s: ThreadHandle[Unit]) => {
              println("Loading image into accelerator")
              Join(s, (_: Unit) => {
                println("Done loading image")
                println("getting accelerator's response") // Step(FREQ/2) used in original testbench
                Join(r, (retval: Seq[Int]) => {
                  println("Response received - comparing results")
                  Return(retval)
                })
              })
            })
          )
        val retval = Command.run(program, dut.clock, print=false)
        val spikes = retval.filter(_ < 200)
        assert(spikes.length == results.length, "number of spikes does not match expected")
        assert(spikes.zip(results).map(x => x._1 == x._2).reduce(_ && _), "spikes do not match expected")
      }
    }
  }
}

 */