package neuroproc.systemtests

import chiseltest._
import chisel3._
import neuroproc._

class NeuromorphicProcessorChiseltestTester extends NeuromorphicProcessorTester {

  it should "process an image" taggedAs (SlowTest) in {
    val startElab = System.nanoTime()
    test(new NeuromorphicProcessor())
      .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        println(s"Took ${(System.nanoTime() - startElab) / 1e9d}s to elaborate, compile and create simulation")
        val startTest = System.nanoTime()
        var cycles = 1L // don't want to spawn a new thread to count cycles
        dut.clock.setTimeout(FREQ)

        def receiveByte(byte: UInt): Unit = {
          // Start bit
          dut.io.uartRx.poke(false.B)
          dut.clock.step(bitDelay)
          // Byte
          for (i <- 0 until 8) {
            dut.io.uartRx.poke(byte(i))
            dut.clock.step(bitDelay)
          }
          // Stop bit
          dut.io.uartRx.poke(true.B)
          dut.clock.step(bitDelay)
        }

        def transferByte(): Int = {
          var byte = 0
          // Assumes start bit has already been seen
          dut.clock.step(bitDelay)
          // Byte
          for (i <- 0 until 8) {
            byte = (dut.io.uartTx.peek.litToBoolean << i) | byte
            dut.clock.step(bitDelay)
          }
          // Stop bit
          dut.io.uartTx.expect(true.B)
          //dut.clock.step(bitDelay)
          byte
        }

        // Reset inputs
        dut.io.uartRx.poke(true.B)
        dut.io.uartTx.expect(true.B)
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)
        dut.io.uartTx.expect(true.B)

        // Spawn a receiver thread
        var spikes = Array[Int]()
        var receive = true
        val rec = fork {
          while (receive) {
            if (!dut.io.uartTx.peek.litToBoolean) {
              val s = transferByte()
              if (s < 200)
                spikes = spikes :+ s
              println(s"Received spike ${s}")
            }
            dut.clock.step()
          }
        }
        rec.fork

        // Load an image into the accelerator ...
        println("Loading image into accelerator")
        for (i <- image.indices) {
          // Write top byte of index, bottom byte of index, top byte
          // of rate, and bottom byte of rate
          receiveByte((i >> 8).U(8.W))
          receiveByte((i & 0xff).U(8.W))
          receiveByte((image(i) >> 8).U(8.W))
          receiveByte((image(i) & 0xff).U(8.W))
        }
        print("Done loading image - ")

        // ... get its response
        println("getting accelerator's response")
        dut.clock.step(FREQ/2)
        receive = false
        rec.join

        println("Response received - comparing results")

        println(spikes.deep.mkString(","))

        assert(spikes.length == results.length, "number of spikes does not match expected")
        assert(spikes.zip(results).map(x => x._1 == x._2).reduce(_ && _), "spikes do not match expected")

        val deltaSeconds = (System.nanoTime() - startTest) / 1e9d
        println(s"Took ${deltaSeconds}s to run test with manual threading and using the chiseltest interface with the NoThreadingAnnotation")
        // println(s"Executed $cycles cycles at an average frequency of ${cycles / deltaSeconds} Hz")
      }
  }
}
