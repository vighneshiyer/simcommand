default: DecoupledGcd.ivl DecoupledGcd.vcs

IVERILOG = iverilog -g2012
VCS = vcs -full64 -sverilog

DUT = ../../test_run_dir/DecoupledGcd_should_work_with_Command_API_and_verilator/DecoupledGcd.sv

DecoupledGcd.ivl: DecoupledGcd_tb.sv $(DUT)
	$(IVERILOG) -o $@ $^

DecoupledGcd.vcs: DecoupledGcd_tb.sv $(DUT)
	$(VCS) -o $@ $^

clean:
	rm -f DecoupledGcd.ivl
	rm -f DecoupledGcd.vcs ucli.key
	rm -rf csrc *.daidir
	rm -f *.vcd

.PHONY: default clean
