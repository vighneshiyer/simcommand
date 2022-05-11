`timescale 1ps/1ps

module NeuromorphicProcessor_tb();
    reg clock = 0;
    always #(1) clock = ~clock;
    reg reset = 0;

    wire uartTx;
    reg uartRx = 1'b1;
    parameter FREQ = 'd80_000_000;
    parameter BAUDRATE = 'd115200;
    parameter bitDelay = $rtoi(FREQ / BAUDRATE) + 1;

    `include "../../src/test/scala/neuroproc/systemtests/image.sv"
    `include "../../src/test/scala/neuroproc/systemtests/results_round.sv"

    NeuromorphicProcessor dut (
        .clock(clock),
        .reset(reset),
        .io_uartTx(uartTx),
        .io_uartRx(uartRx)
    );

    task receiveByte(input [7:0] b);
        integer i;
        begin
            //$display("Sending byte %d\n", b);
            // Start bit
            uartRx = 1'b0;
            repeat (bitDelay) @(posedge clock);
            // Byte
            for (i = 0; i < 8; i = i + 1) begin
                uartRx = b[i];
                repeat (bitDelay) @(posedge clock);
            end
            // Stop bit
            uartRx = 1'b1;
            repeat (bitDelay) @(posedge clock);
        end
    endtask

    task transferByte(output [7:0] b);
        integer j;
        begin
            b = 0;
            // Start bit, assume start bit has already been seen
            repeat (bitDelay) @(posedge clock);
            // Byte
            for (j = 0; j < 8; j = j + 1) begin
                b = (uartTx << j) | b;
                repeat (bitDelay) @(posedge clock);
            end
            // Stop bit
            assert(uartTx == 1'b1); // stop bit
            // repeat (bitDelay) @(posedge clock);
            // $display("Received byte %d\n", b);
        end
    endtask

    reg [7:0] spikes [$];
    reg receive = 1'b1;
    reg [7:0] byteSeen;
    integer k;
    initial begin
        // $vcdpluson;
        $fsdbDumpfile("dump.fsdb");
        $fsdbDumpvars();
        $fsdbDumpon;

        @(posedge clock);
        uartRx = 1'b1;
        // assert(uartTx == 1'b1);
        reset = 1'b1;
        repeat (3) @(posedge clock); #1;
        reset = 1'b0;
        assert(uartTx == 1'b1);
        // @(posedge clock);

        fork
            begin
                while (receive) begin
                    if (!uartTx) begin
                        transferByte(byteSeen);
                        if (byteSeen < 200) begin
                            spikes.push_back(byteSeen);
                        end
                        $display("Received spike %d", byteSeen);
                    end
                    @(posedge clock);
                end
            end
            begin
                // Load an image into the accelerator ...
                $display("Loading image into accelerator");
                for (k = 0; k < $size(image); k = k + 1) begin
                    // Write top byte of index, bottom byte of index, top byte
                    // of rate, and bottom byte of rate
                    receiveByte((k >> 8));
                    receiveByte((k & 8'hff));
                    receiveByte((image[k] >> 8));
                    receiveByte((image[k] & 8'hff));
                end
                $display("Done loading image - ");

                // ... get its response
                $display("getting accelerator's response");
                repeat (FREQ/2) @(posedge clock);
                receive = 1'b0;
                // rec.join, receiver thread will finish on its own
            end
        join


        $display("Response received - comparing results");
        // println(spikes.deep.mkString(","))

        assert($size(spikes) == $size(results)) else $error("number of spikes does not match expected");
        for (k = 0; k < $size(results); k = k + 1) begin
            assert(results[k] == spikes[k]) else $error("spikes do not match, got %d expected %d", spikes[k], results[k]);
        end

        $display("%t", $time);
        $finish();
    end

endmodule
