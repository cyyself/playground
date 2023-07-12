// import Mill dependency
import mill._
import mill.scalalib.publish._
import mill.define.Sources
import mill.modules.Util
import scalalib._
// support BSP
import mill.bsp._
// input build.sc from each repositories.
import $file.dependencies.chisel.build
import $file.dependencies.firrtl.build
import $file.dependencies.treadle.build
import $file.dependencies.cde.build
import $file.dependencies.`berkeley-hardfloat`.build
import $file.dependencies.`rocket-chip`.common
import $file.dependencies.`chisel-testers2`.build

// Global Scala Version
object ivys {
  val sv = "2.13.10"
  val upickle = ivy"com.lihaoyi::upickle:1.3.15"
  val oslib = ivy"com.lihaoyi::os-lib:0.7.8"
  val pprint = ivy"com.lihaoyi::pprint:0.6.6"
  val utest = ivy"com.lihaoyi::utest:0.7.10"
  val jline = ivy"org.scala-lang.modules:scala-jline:2.12.1"
  val scalatest = ivy"org.scalatest::scalatest:3.2.2"
  val scalatestplus = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.3"
  val scopt = ivy"com.github.scopt::scopt:3.7.1"
  val playjson =ivy"com.typesafe.play::play-json:2.9.4"
  val breeze = ivy"org.scalanlp::breeze:1.1"
  val parallel = ivy"org.scala-lang.modules:scala-parallel-collections_3:1.0.4"
  val spire = ivy"org.typelevel::spire:0.17.0"
}

// For modules not support mill yet, need to have a ScalaModule depend on our own repositories.
trait CommonModule extends ScalaModule {
  override def scalaVersion = ivys.sv

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
  ) }

  override def scalacOptions = T {
    super.scalacOptions() ++ Agg(s"-Xplugin:${mychisel3.plugin.jar().path}", "-Ymacro-annotations")
  }

  override def moduleDeps: Seq[ScalaModule] = Seq(mychisel3)
}


// Chips Alliance

object myfirrtl extends dependencies.firrtl.build.firrtlCrossModule(ivys.sv) {
  override def millSourcePath = os.pwd / "dependencies" / "firrtl"
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivys.pprint
  )
  override val checkSystemAntlr4Version = false
  override val checkSystemProtocVersion = false
  override val protocVersion = os.proc("protoc", "--version").call().out.text.dropRight(1).split(' ').last
  override val antlr4Version = os.proc("antlr4").call().out.text.split('\n').head.split(' ').last
}

object mychisel3 extends dependencies.chisel.build.chisel3CrossModule(ivys.sv) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)

  def treadleModule: Option[PublishModule] = Some(mytreadle)

  def chiseltestModule: Option[PublishModule] = Some(mychiseltest)

  override def scalacOptions = T {
    super.scalacOptions() ++ Agg("-Ymacro-annotations")
  }
}

object mytreadle extends dependencies.treadle.build.treadleCrossModule(ivys.sv) {
  override def millSourcePath = os.pwd /  "dependencies" / "treadle"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}

object mycde extends dependencies.cde.build.cde(ivys.sv) with PublishModule {
  override def millSourcePath = os.pwd /  "dependencies" / "cde" / "cde"
}

object myrocketchip extends dependencies.`rocket-chip`.common.CommonRocketChip {

  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel3.plugin.jar().path}")
  }

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
  ) }

  override def millSourcePath = os.pwd /  "dependencies" / "rocket-chip"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def hardfloatModule: PublishModule = myhardfloat

  def cdeModule: PublishModule = mycde
}

object inclusivecache extends CommonModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-inclusive-cache" / 'design / 'craft / "inclusivecache"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object blocks extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-blocks"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

// UCB
object mychiseltest extends dependencies.`chisel-testers2`.build.chiseltestCrossModule(ivys.sv) {
  override def scalaVersion = ivys.sv
  def chisel3Module: Option[PublishModule] = Some(mychisel3)
  def treadleModule: Option[PublishModule] = Some(mytreadle)
}

object myhardfloat extends dependencies.`berkeley-hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd /  "dependencies" / "berkeley-hardfloat"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
    ) }
  
  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel3.plugin.jar().path}", "-Ymacro-annotations")
  }
}

object constellation extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "constellation"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object mempress extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "mempress"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, firesim)
}



object testchipip extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "testchipip"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

object icenet extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "icenet"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, testchipip)
}


object mdf extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "plsi-mdf"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)

  override def ivyDeps = Agg(
    ivys.playjson
  )
}

object firesim extends CommonModule with SbtModule { fs =>

  override def millSourcePath = os.pwd / "dependencies" / "firesim" / "sim"

  object midas extends CommonModule with SbtModule {

    override def millSourcePath = fs.millSourcePath / "midas"

    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, targetutils, mdf)

    object targetutils extends CommonModule with SbtModule
  }

  object lib extends CommonModule with SbtModule {
    override def millSourcePath = fs.millSourcePath / "firesim-lib"

    override def moduleDeps = super.moduleDeps ++ Seq(midas, testchipip, icenet)
  }

  override def moduleDeps = super.moduleDeps ++ Seq(lib, midas)
}

object boom extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "riscv-boom"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, testchipip)
}

object barstools extends CommonModule with SbtModule { bt =>
  override def millSourcePath = os.pwd / "dependencies" / "barstools"

  object macros extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "macros"
    override def moduleDeps = super.moduleDeps ++ Seq(mdf)
  }

  object iocell extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "iocell"
  }

  object tapeout extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "tapeout"
  }

  override def moduleDeps = super.moduleDeps ++ Seq(macros, iocell, tapeout)
}

object gemmini extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "gemmini"

  override def ivyDeps = Agg(
    ivys.breeze
  )
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, testchipip, firesim.lib)
}

object nvdla extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "nvdla-wrapper"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object cva6 extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "cva6-wrapper"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object hwacha extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "hwacha"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object sodor extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "riscv-sodor"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object sha3 extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "sha3"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, mychiseltest, firesim.midas)
}

object ibex extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "ibex-wrapper"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object dsptools extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "dsptools"

  override def ivyDeps = Agg(
    ivys.spire,
    ivys.breeze
  )
}

object dsputils extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-dsp-utils"

  override def ivyDeps = Agg(
    ivys.spire,
    ivys.breeze
  )

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, dsptools)
}

object fftgenerator extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "fft-generator"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, dsputils)
}

object barf extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "bar-fetchers"

  override def ivyDeps = Agg(
    ivys.spire,
    ivys.breeze
  )

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object shuttle extends CommonModule with SbtModule {
  override def millSourcePath = os.pwd / "dependencies" / "shuttle"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

// I know it's quite strange, however UCB messly managed their dependency...
object chipyard extends CommonModule with SbtModule { cy =>
  def basePath = os.pwd / "dependencies" / "chipyard"
  override def millSourcePath = basePath / "generators" / "chipyard"
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, barstools, testchipip, blocks, icenet, boom, gemmini, nvdla, hwacha, cva6, tracegen, sodor, sha3, ibex, constellation, mempress, fftgenerator, barf, shuttle)

  object tracegen extends CommonModule with SbtModule {
    override def millSourcePath = basePath / "generators" / "tracegen"
    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, boom)
  }

  object utilities extends CommonModule with SbtModule {
    override def millSourcePath = basePath / "generators" / "utilities"
    override def moduleDeps = super.moduleDeps ++ Seq(chipyard)
  }

  object firechip extends CommonModule with SbtModule {
    override def millSourcePath = basePath / "generators" / "firechip"
    override def moduleDeps = super.moduleDeps ++ Seq(chipyard)
  }
}

// Dummy

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, blocks, firesim, boom, chipyard, chipyard.utilities, mychiseltest)

  // add some scala ivy module you like here.
  override def ivyDeps = Agg(
    ivys.oslib,
    ivys.pprint
  )

  // use scalatest as your test framework
  object tests extends Tests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      ivys.scalatest
    )
    override def moduleDeps = super.moduleDeps ++ Seq(mychiseltest)
  }
}
