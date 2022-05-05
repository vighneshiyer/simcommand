`timescale 1ps/1ps
module DecoupledGcd_tb();

    reg clock = 0;
    always #(1) clock <= ~clock;
    reg reset = 0;

    wire input_ready;
    reg input_valid = 1'b0;
    reg [59:0] input_bits_value1 = 'd0;
    reg [59:0] input_bits_value2 = 'd0;
    reg output_ready = 1'b0;
    wire output_valid;
    wire [59:0] output_bits_value1;
    wire [59:0] output_bits_value2;
    wire [59:0] output_bits_gcd;

    DecoupledGcd dut (
        .clock(clock),
        .reset(reset),
        .input_ready(input_ready),
        .input_valid(input_valid),
        .input_bits_value1(input_bits_value1),
        .input_bits_value2(input_bits_value2),
        .output_ready(output_ready),
        .output_valid(output_valid),
        .output_bits_value1(output_bits_value1),
        .output_bits_value2(output_bits_value2),
        .output_bits_gcd(output_bits_gcd)
    );

    task enqueue(input [59:0] value1, input [59:0] value2);
        begin
            input_valid = 'b1;
            input_bits_value1 = value1;
            input_bits_value2 = value2;
            while (!input_ready) begin
                @(posedge clock); #1;
            end
            @(posedge clock); #1;
            input_valid = 'b0;
        end
    endtask

    task dequeue(output [59:0] value1, output [59:0] value2, output [59:0] gcd);
        begin
            output_ready = 'b1;
            while (!output_valid) begin
                @(posedge clock); #1;
            end
            value1 = output_bits_value1;
            value2 = output_bits_value2;
            gcd = output_bits_gcd;
            @(posedge clock); #1;
            output_ready = 'b0;
        end
    endtask

    function [59:0] gcd(input [59:0] a, b);
        integer t;
        begin
            while (b != 0) begin
                t = b;
                b = a % b;
                a = t;
            end
            gcd = a;
        end
    endfunction

    reg [59:0] x, y;
    reg [(60*3)-1:0] stim [$];
    reg [(60*3)-1:0] stim_temp;
    reg [60-1:0] result_value1;
    reg [60-1:0] result_value2;
    reg [60-1:0] result_gcd;
    reg [(60*3)-1:0] results [$];
    integer maxX = 100;//100;
    integer maxY = 100;//100;
    integer i, j;
    initial begin
        //$dumpfile("DecoupledGcd_tb.vcd");
        //$dumpvars(0, DecoupledGcd_tb);
        for (x = 2; x <= maxX; x = x + 1) begin
            for (y = 2; y <= maxY; y = y + 1) begin
                stim.push_back({gcd(x, y), y, x});
            end
        end


        reset = 'b1;
        @(posedge clock); #1;
        reset = 'b0;
        @(posedge clock); #1;

        //$system("date +\"%s:%N\"");
        fork
            // Enqueuing thread
            begin
                for (i = 0; i < stim.size(); i = i + 1) begin
                    //$display(stim[i]);//], stim[i][1], stim[i][2]);
                    stim_temp = stim[i];
                    enqueue(stim_temp[60-1:0], stim_temp[60*2-1:60]);
                end
            end
            // Dequeueing thread
            begin
                for (j = 0; j < stim.size(); j = j + 1) begin
                    dequeue(result_value1, result_value2, result_gcd);
                    results.push_back({result_gcd, result_value2, result_value1});
                end
            end
        join

        for (i = 0; i < results.size(); i = i + 1) begin
            //$display(results[i]);
            assert(results[i] == stim[i]);
        end
        $display("%t", $time);

        $finish();
    end
endmodule
