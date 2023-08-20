package chipyard

import freechips.rocketchip.diplomacy.{BindingScope, LazyModule, LazyModuleImpLike, LazyRawModuleImp}
import org.chipsalliance.cde.config.{Field, Parameters}

case object BuildSystem extends Field[Parameters => LazyModule]((p: Parameters) => new DigitalTop()(p))

/**
 * The base class used for building chips. This constructor instantiates a module specified by the BuildSystem parameter,
 * named "system", which is an instance of DigitalTop by default. The diplomatic clocks of System, as well as its implicit clock,
 * is aggregated into the clockGroupNode. The parameterized functions controlled by ClockingSchemeKey and GlobalResetSchemeKey
 * drive clock and reset generation
 */

class ChipTop(implicit p: Parameters) extends LazyModule with BindingScope
  with HasIOBinders {
  // The system module specified by BuildSystem
  lazy val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("system")

  // NOTE: Making this a LazyRawModule is moderately dangerous, as anonymous children
  // of ChipTop (ex: ClockGroup) do not receive clock or reset.
  // However. anonymous children of ChipTop should not need an implicit Clock or Reset
  // anyways, they probably need to be explicitly clocked.
  lazy val module: LazyModuleImpLike = new LazyRawModuleImp(this) {  }
}
