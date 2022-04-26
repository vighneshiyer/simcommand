from typing import List, Tuple
import cocotb
from cocotb.clock import Clock
from cocotb.triggers import Timer, ClockCycles, Event, RisingEdge, Join, RisingEdge, NextTimeStep, ReadOnly
import datetime
import math


async def enqueue(dut, value1: int, value2: int):
    dut.input_bits_value1.value = value1
    dut.input_bits_value2.value = value2
    dut.input_valid.value = 1
    #print(dut.input_bits_value1.value, dut.input_bits_value2.value)
    await ReadOnly()
    while dut.input_ready.value != 1:
        await RisingEdge(dut.clock)
        await ReadOnly()
    await RisingEdge(dut.clock)
    dut.input_valid.value = 0

async def enqueueSeq(dut, data: List[Tuple[int, int]]):
    for value1, value2 in data:
        await enqueue(dut, value1, value2)

async def dequeue(dut) -> (int, int, int):
    dut.output_ready.value = 1
    while dut.output_valid.value != 1:
        await ClockCycles(dut.clock, 1)
    value1, value2, gcd = (dut.output_bits_value1.value, dut.output_bits_value2.value, dut.output_bits_gcd.value)
    await ClockCycles(dut.clock, 1)
    dut.output_ready.value = 0
    return (value1, value2, gcd)

async def dequeueN(dut, n: int) -> List[Tuple[int, int, int]]:
    values = []
    for i in range(n):
        deq = await dequeue(dut)
        values.append(deq)
    return values

@cocotb.test()
async def gcd_tb(dut):
    (maxX, maxY) = (100, 100)
    #(maxX, maxY) = (10, 10)
    testValues = []
    for x in range(2, maxX + 1):
        for y in range(2, maxY + 1):
            testValues.append((x, y, math.gcd(x, y)))
    bitWidth = 60
    #print(testValues)

    print(datetime.datetime.now())
    # Clock is generated inside Python
    cocotb.start_soon(Clock(dut.clock, 2, units="ps").start())
    await ClockCycles(dut.clock, 2)

    # Reset inputs
    dut.reset.value = 1
    dut.input_valid.value = 0
    dut.output_ready.value = 0
    await ClockCycles(dut.clock, 1)
    dut.reset.value = 0
    await ClockCycles(dut.clock, 1)

    print("reset done")
    # Drive inputs and get outputs in 2 threads
    driver = cocotb.start_soon(enqueueSeq(dut, [(x[0], x[1]) for x in testValues]))
    reader = cocotb.start_soon(dequeueN(dut, len(testValues)))

    await Join(driver)
    results = await Join(reader)
    for r in zip(results, testValues):
        assert r[0][0] == r[1][0]
        assert r[0][1] == r[1][1]
        assert r[0][2] == r[1][2]
    print(datetime.datetime.now())
