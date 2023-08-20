package chipyard

import chipyard.clocking._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.Field

case class ChipyardPRCIControlParams(
                                      slaveWhere: TLBusWrapperLocation = CBUS,
                                      baseAddress: BigInt = 0x100000,
                                      enableTileResetSetting: Boolean = true
                                    )


case object ChipyardPRCIControlKey extends Field[ChipyardPRCIControlParams](ChipyardPRCIControlParams())

trait HasChipyardPRCI { this: BaseSubsystem with InstantiatesTiles =>
  require(p(SubsystemDriveAsyncClockGroupsKey).isEmpty, "Subsystem asyncClockGroups must be undriven")

  val prciParams = p(ChipyardPRCIControlKey)

  // Set up clock domain
  private val tlbus = locateTLBusWrapper(prciParams.slaveWhere)
  val prci_ctrl_domain = LazyModule(new ClockSinkDomain(name=Some("chipyard-prci-control")))
  prci_ctrl_domain.clockNode := tlbus.fixedClockNode

  val prci_ctrl_bus = prci_ctrl_domain { TLXbar() }
  tlbus.coupleTo("prci_ctrl") { (prci_ctrl_bus
    := TLFIFOFixer(TLFIFOFixer.all)
    := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes)
    := TLBuffer()
    := _)
  }

  // Aggregate all the clock groups into a single node
  val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
  val allClockGroupsNode = ClockGroupEphemeralNode()

  // There are two "sets" of clocks which must be dealt with

  // 1. The implicit clock from the subsystem. RC is moving away from depending on this
  //    clock, but some modules still use it. Since the implicit clock sink node
  //    is created in the ChipTop (the hierarchy wrapping the subsystem), this function
  //    is provided to allow connecting that clock to the clock aggregator. This function
  //    should be called in the ChipTop context
  def connectImplicitClockSinkNode(sink: ClockSinkNode) = {
    val implicitClockGrouper = this { ClockGroup() }
    (sink
      := implicitClockGrouper
      := aggregator)
  }

  // 2. The rest of the diplomatic clocks in the subsystem are routed to this asyncClockGroupsNode
  val clockNamePrefixer = ClockGroupNamePrefixer()
  (asyncClockGroupsNode
    :*= clockNamePrefixer
    :*= aggregator)


  // Once all the clocks are gathered in the aggregator node, several steps remain
  // 1. Assign frequencies to any clock groups which did not specify a frequency.
  // 2. Combine duplicated clock groups (clock groups which physically should be in the same clock domain)
  // 3. Synchronize reset to each clock group
  // 4. Clock gate the clock groups corresponding to Tiles (if desired).
  // 5. Add reset control registers to the tiles (if desired)
  // The final clock group here contains physically distinct clock domains, which some PRCI node in a
  // diplomatic IOBinder should drive
  val frequencySpecifier = ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey))
  val clockGroupCombiner = ClockGroupCombiner()
  val resetSynchronizer  = prci_ctrl_domain { ClockGroupResetSynchronizer() }
  val tileResetSetter    = Option.when(prciParams.enableTileResetSetting) { prci_ctrl_domain {
    val reset_setter = LazyModule(new TileResetSetter(prciParams.baseAddress + 0x10000, tlbus.beatBytes,
      tile_prci_domains.map(_.tile_reset_domain.clockNode.portParams(0).name.get), Nil))
    reset_setter.tlNode := prci_ctrl_bus
    reset_setter
  } }

  (aggregator
    := frequencySpecifier
    := clockGroupCombiner
    := resetSynchronizer
    := tileResetSetter.map(_.clockNode).getOrElse(ClockGroupEphemeralNode()(ValName("temp")))
    := allClockGroupsNode)
}