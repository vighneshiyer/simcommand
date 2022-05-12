package neuroproc

import chisel3._
import chisel3.util._

class ClockBufferIO extends Bundle {
  val I  = Input(Clock())
  val CE = Input(Bool())
  val O  = Output(Clock())
}

abstract class ClockBuffer extends Module {
  val io = IO(new ClockBufferIO)
}

// For now, this Xilinx primitive is used. In practice, this should be
// implemented much more involved along the lines of `ClockBufferBB`, as
// seen in "How to Successfully Use Gated Clocking in an ASIC Design" by
// Darren Jones of MIPS Technologies.
class BUFGCE extends BlackBox(Map("SIM_DEVICE" -> "7SERIES")) {
  val io = IO(new ClockBufferIO)
}

class ClockBufferFPGA extends ClockBuffer {
  val bg = Module(new BUFGCE)
  io <> bg.io
}

// The Verilator comments instruct Verilator not to warn about the latch
// and to consider the latched signals a clock enable thus ensuring 
// correct multi-clock operation. Inspired by Rocket Chip.
class ClockBufferBB extends BlackBox with HasBlackBoxInline {
  val io = IO(new ClockBufferIO)
  setInline("ClockBufferBB.v",
  s"""
  |/* verilator lint_off UNOPTFLAT */
  |module ClockBufferBB(input I, input CE, output O);
  |  reg en_latched /*verilator clock_enable*/;
  |  always @(*) begin
  |    if (!I) begin
  |       en_latched = CE;
  |    end
  |  end
  |  assign O = en_latched & I;
  |endmodule
  """.stripMargin)
}
  //|  always_latch if (!I) en_latched = CE;

class ClockBufferVerilog extends ClockBuffer {
  val bb = Module(new ClockBufferBB)
  io <> bb.io
}

object ClockBuffer {
  def apply(synth: Boolean = false) = {
    if (synth)
      new ClockBufferFPGA
    else
      new ClockBufferVerilog
  }
}
