package playground

import freechips.rocketchip.subsystem.WithEdgeDataBits
import gemmini._
import org.chipsalliance.cde.config.Config

class TestConfig
    extends Config((site, here, up) => {
      case freechips.rocketchip.util.ClockGateModelFile => Some("./dependencies/rocket-chip/src/main/resources/vsrc/EICG_wrapper.v")
      case freechips.rocketchip.devices.tilelink.BootROMLocated(x) =>
        up(freechips.rocketchip.devices.tilelink.BootROMLocated(x), site)
          .map(_.copy(contentFileName = "./dependencies/rocket-chip/bootrom/bootrom.img"))
    })

class PlaygroundConfig
    extends Config(
      (new DefaultGemminiConfig ++ new WithEdgeDataBits(128) ++ new TestConfig)
        .orElse(new freechips.rocketchip.subsystem.WithInclusiveCache)
        .orElse(new freechips.rocketchip.system.DefaultConfig)
    )
