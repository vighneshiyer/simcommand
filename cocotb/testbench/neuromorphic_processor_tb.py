from typing import List
import cocotb
from cocotb.clock import Clock
from cocotb.triggers import Timer, ClockCycles, Event, RisingEdge
import datetime


FREQ             = 80000000                 # in Hz
BAUDRATE         = 115200
RANKORDERENC     = False
USEROUNDEDWGHTS  = True


def fetch(file: str) -> List[int]:
    with open(file, 'r') as f:
        lines = [x for x in f]
        assert len(lines) == 1
        return [int(x) for x in lines[0].split(',')]


async def receiveByte(dut, bitDelay: int, byte: int):
    print(f"Sending byte {byte}")
    # Start bit
    dut.io_uartRx.value = 0
    await ClockCycles(dut.clock, bitDelay)
    # Byte
    for i in range(8):
        dut.io_uartRx.value = (byte >> i) & 0x1
        await ClockCycles(dut.clock, bitDelay)
    # Stop bit
    dut.io_uartRx.value = 1
    await ClockCycles(dut.clock, bitDelay)
    print(f"Sent byte {byte}")


async def transferByte(dut, bitDelay: int) -> int:
    print("Receiving a byte")
    byte = 0
    # Assumes start bit has already been seen
    await ClockCycles(dut.clock, bitDelay)
    # Byte
    for i in range(8):
        byte = dut.io_uartTx.value << i | byte
        await ClockCycles(dut.clock, bitDelay)
    # Stop bit
    assert dut.io_uartTx.value == 1
    await ClockCycles(dut.clock, bitDelay)
    print(f"Received {byte}")
    return byte

spikes: List[int] = []


async def fetchSpikes(dut, receiveDone: Event, bitDelay: int) -> List[int]:
    while True:
        if not receiveDone.is_set():
            if dut.io_uartTx.value == 0:
                s = await transferByte(dut, bitDelay)
                if s < 200:
                    spikes.append(s)
                print(f"Received spike {s}")
            else:
                await ClockCycles(dut.clock, 1)
        else:
            break
    return spikes

@cocotb.test()
async def neuromorphic_processor_tb(dut):
    bitDelay = int(FREQ / BAUDRATE) + 1
    # Reference image and results
    image = fetch("../../src/test/scala/neuroproc/systemtests/image.txt")
    results = fetch("../../src/test/scala/neuroproc/systemtests/results_round.txt") if (USEROUNDEDWGHTS) else fetch("../..//src/test/scala/neuroproc/systemtests/results_toInt.txt")

    print(datetime.datetime.now())
    # Clock is generated inside Python
    cocotb.start_soon(Clock(dut.clock, 2, units="ps").start())
    await ClockCycles(dut.clock, 2)

    # Reset inputs
    dut.io_uartRx.value = 1
    # assert dut.io_uartTx.value == 1
    dut.reset.value = 1
    await ClockCycles(dut.clock, 1)
    dut.reset.value = 0
    await ClockCycles(dut.clock, 1)
    assert dut.io_uartTx.value == 1

    # Spawn a receiver thread
    receiveDone = Event("receiving done")
    cocotb.start_soon(fetchSpikes(dut, receiveDone, bitDelay))

    # Load an image into the accelerator ...
    print("Loading image into accelerator")
    for i in range(len(image)):
        # Write top byte of index, bottom byte of index, top byte of rate,
        # and bottom byte of rate
        await receiveByte(dut, bitDelay, (i >> 8) & 0xff)
        await receiveByte(dut, bitDelay, i & 0xff)
        await receiveByte(dut, bitDelay, (image[i] >> 8) & 0xff)
        await receiveByte(dut, bitDelay, image[i] & 0xff)
    print("Done loading image - ")

    # ... get its response
    print("getting accelerator's response")
    await ClockCycles(dut.clock, int(FREQ/2))
    receiveDone.set()

    print(datetime.datetime.now())
    print("Response received - comparing results")
    assert len(spikes) == len(results), "number of spikes does not match expected"
    for i in range(len(spikes)):
        assert spikes[i] == results[i], "spikes do not match expected: {spikes[i]} != {results[i]}"
    print(datetime.datetime.now())
