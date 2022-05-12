default: NeuromorphicProcessor.ivl NeuromorphicProcessor.vcs

IVERILOG = iverilog -g2012
VCS = vcs -full64 -sverilog

DUT = ../../test_run_dir/NeuromorphicProcessorRawSimulatorTester/NeuromorphicProcessor.sv ../../test_run_dir/NeuromorphicProcessorRawSimulatorTester/ClockBufferBB.v
TB = NeuromorphicProcessor_tb.sv

NeuromorphicProcessor.ivl: $(TB) $(DUT)
	$(IVERILOG) -o $@ $^

NeuromorphicProcessor.vcs: $(TB) $(DUT)
	#$(VCS) +define+RANDOMIZE_REG_INIT +define+RANDOMIZE_MEM_INIT -debug_access -o $@ $^
	#$(VCS) +vcs+initreg+random -debug_access -o $@ $^
	$(VCS) +vcs+initreg+random -o $@ $^

run_vcs: NeuromorphicProcessor.vcs
	./NeuromorphicProcessor.vcs +vcs+initreg+0

clean:
	rm -f *.ivl
	rm -f *.vcs ucli.key
	rm -rf csrc *.daidir
	rm -f *.vcd

.PHONY: default clean run_vcs
