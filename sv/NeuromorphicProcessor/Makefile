default: NeuromorphicProcessor.ivl NeuromorphicProcessor.vcs

IVERILOG = iverilog -g2012
VCS = vcs -full64 -sverilog

DUT = ../../test_run_dir/Neuromorphic_Processor_should_process_an_image/NeuromorphicProcessor.sv ../../test_run_dir/Neuromorphic_Processor_should_process_an_image/ClockBufferBB.v
TB = NeuromorphicProcessor_tb.sv

NeuromorphicProcessor.ivl: $(TB) $(DUT)
	$(IVERILOG) -o $@ $^

NeuromorphicProcessor.vcs: $(TB) $(DUT)
	$(VCS) -o $@ $^

clean:
	rm -f *.ivl
	rm -f *.vcs ucli.key
	rm -rf csrc *.daidir
	rm -f *.vcd

.PHONY: default clean
