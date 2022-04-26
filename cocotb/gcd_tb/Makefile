# Makefile

# defaults
SIM ?= verilator
TOPLEVEL_LANG ?= verilog

VERILOG_SOURCES += $(PWD)/../../test_run_dir/DecoupledGcd_should_work_with_chiseltest_single_threaded_and_verilator/DecoupledGcd.sv
# use VHDL_SOURCES for VHDL files
COMPILE_ARGS += -DRANDOMIZE_REG_INIT -DRANDOMIZE_MEM_INIT
EXTRA_ARGS += --trace --trace-structs

# TOPLEVEL is the name of the toplevel module in your Verilog or VHDL file
TOPLEVEL = DecoupledGcd

# MODULE is the basename of the Python test file
MODULE = gcd_tb

# include cocotb's make rules to take care of the simulator setup
include $(shell cocotb-config --makefiles)/Makefile.sim
