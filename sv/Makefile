default: DecoupledGcd

DecoupledGcd: DecoupledGcd_tb.v ../test_run_dir/DecoupledGcd_should_work_with_Command_API_and_verilator/DecoupledGcd.sv
	iverilog -g2012 -o $@ $^

clean:
	rm -f DecoupledGcd
	rm -f *.vcd

.PHONY: default clean
