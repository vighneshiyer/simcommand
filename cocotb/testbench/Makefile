# Makefile

# defaults
SIM ?= verilator
TOPLEVEL_LANG ?= verilog

VERILOG_SOURCES += $(PWD)/../../test_run_dir/Neuromorphic_Processor_should_process_an_image/NeuromorphicProcessor.sv \
	$(PWD)/ClockBufferBB.sv
# use VHDL_SOURCES for VHDL files
COMPILE_ARGS += -DRANDOMIZE_REG_INIT -DRANDOMIZE_MEM_INIT
#EXTRA_ARGS += --trace --trace-structs

# TOPLEVEL is the name of the toplevel module in your Verilog or VHDL file
TOPLEVEL = NeuromorphicProcessor

# MODULE is the basename of the Python test file
MODULE = neuromorphic_processor_tb

# include cocotb's make rules to take care of the simulator setup
include $(shell cocotb-config --makefiles)/Makefile.sim
