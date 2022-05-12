default: NeuromorphicProcessor.ivl NeuromorphicProcessor.vcs

IVERILOG = iverilog -g2012
VCS = vcs -full64 -sverilog

DUT = ../../test_run_dir/NeuromorphicProcessorRawSimulatorTester/NeuromorphicProcessor.sv ../../test_run_dir/NeuromorphicProcessorRawSimulatorTester/ClockBufferBB.v
TB = NeuromorphicProcessor_tb.sv

NeuromorphicProcessor.ivl: $(TB) $(DUT)
	$(IVERILOG) -o $@ $^

NeuromorphicProcessor.vcs: $(TB) $(DUT)
	$(VCS) -debug_access -o $@ $^

clean:
	rm -f *.ivl
	rm -f *.vcs ucli.key
	rm -rf csrc *.daidir
	rm -f *.vcd

.PHONY: default clean
