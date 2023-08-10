// See LICENSE.SiFive for license details.

package playground.harness

import chisel3._
import freechips.rocketchip.devices.debug.Debug
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.prci.{ClockParameters, ClockSinkParameters}
import freechips.rocketchip.system.{ExampleRocketSystem, SimAXIMem}
import freechips.rocketchip.util.AsyncResetReg
import midas.widgets.{PeekPokeBridge, RationalClock, RationalClockBridge, ResetPulseBridge, ResetPulseBridgeParameters}
import org.chipsalliance.cde.config.Parameters

import scala.collection.mutable.LinkedHashMap
import playground.clocking.SimplePllConfiguration

class FireSimClockBridgeInstantiator extends HarnessClockInstantiator {
  // connect all clock wires specified to the RationalClockBridge
  def instantiateHarnessClocks(refClock: Clock, refClockFreqMHz: Double): Unit = {
    val sinks = clockMap.map({ case (name, (freq, bundle)) =>
      ClockSinkParameters(take=Some(ClockParameters(freqMHz=freq / (1000 * 1000))), name=Some(name))
    }).toSeq

    val pllConfig = new SimplePllConfiguration("firesimRationalClockBridge", sinks)
    pllConfig.emitSummaries()

    var instantiatedClocks = LinkedHashMap[Int, (Clock, Seq[String])]()
    // connect wires to clock source
    def findOrInstantiate(freqMHz: Int, name: String): Clock = {
      if (!instantiatedClocks.contains(freqMHz)) {
        val clock = Wire(Clock())
        instantiatedClocks(freqMHz) = (clock, Seq(name))
      } else {
        instantiatedClocks(freqMHz) = (instantiatedClocks(freqMHz)._1, instantiatedClocks(freqMHz)._2 :+ name)
      }
      instantiatedClocks(freqMHz)._1
    }
    for ((name, (freq, clock)) <- clockMap) {
      val freqMHz = (freq / (1000 * 1000)).toInt
      clock := findOrInstantiate(freqMHz, name)
    }

    // The undivided reference clock as calculated by pllConfig must be instantiated
    findOrInstantiate(pllConfig.referenceFreqMHz.toInt, "reference")

    val ratClocks = instantiatedClocks.map { case (freqMHz, (clock, names)) =>
      (RationalClock(names.mkString(","), 1, pllConfig.referenceFreqMHz.toInt / freqMHz), clock)
    }.toSeq
    val clockBridge = Module(new RationalClockBridge(ratClocks.map(_._1)))
    (clockBridge.io.clocks zip ratClocks).foreach { case (clk, rat) =>
      rat._2 := clk
    }
  }
}

class FireSim(implicit val p: Parameters) extends RawModule with HasHarnessInstantiators {
  require(harnessClockInstantiator.isInstanceOf[FireSimClockBridgeInstantiator])
  freechips.rocketchip.util.property.cover.setPropLib(new midas.passes.FireSimPropertyLibrary())

  // The peek-poke bridge must still be instantiated even though it's
  // functionally unused. This will be removed in a future PR.
  val dummy = WireInit(false.B)
  val peekPokeBridge = PeekPokeBridge(harnessBinderClock, dummy)

  val resetBridge = Module(new ResetPulseBridge(ResetPulseBridgeParameters()))
  // In effect, the bridge counts the length of the reset in terms of this clock.
  resetBridge.io.clock := harnessBinderClock

  def referenceClockFreqMHz = 0.0
  def referenceClock = false.B.asClock // unused
  def referenceReset = resetBridge.io.reset
  def success = { require(false, "success should not be used in Firesim"); false.B }

  override val supportsMultiChip = true

  // TODO: ChipTop
  instantiateChipTops()

  // Ensures FireSim-synthesized assertions and instrumentation is disabled
  // while resetBridge.io.reset is asserted.  This ensures assertions do not fire at
  // time zero in the event their local reset is delayed (typically because it
  // has been pipelined)
  midas.targetutils.GlobalResetCondition(resetBridge.io.reset)
}

class FireSimHarness()(implicit p: Parameters) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Reset()))
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  withClockAndReset(clock, reset) {
    val ldut = LazyModule(new ExampleRocketSystem)
    val dut = Module(ldut.module)

    // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
    dut.reset := (reset.asBool | ldut.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)).asBool

    dut.dontTouchPorts()
    dut.tieOffInterrupts()
    SimAXIMem.connectMem(ldut)
    SimAXIMem.connectMMIO(ldut)
    ldut.l2_frontend_bus_axi4.foreach(a => {
      a.ar.valid := false.B
      a.ar.bits := DontCare
      a.aw.valid := false.B
      a.aw.bits := DontCare
      a.w.valid := false.B
      a.w.bits := DontCare
      a.r.ready := false.B
      a.b.ready := false.B
    })
    //ldut.l2_frontend_bus_axi4.foreach(_.tieoff)
    Debug.connectDebug(ldut.debug, ldut.resetctrl, ldut.psd, clock, reset.asBool, io.success)
  }
}
