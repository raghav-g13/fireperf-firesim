//See LICENSE for license details.
package firesim.fasedtests

import freechips.rocketchip.config.{Field, Config}
import freechips.rocketchip.subsystem.{ExtMem, WithoutTLMonitors, WithNMemoryChannels, MemoryPortParams, MasterPortParams}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy.{AddressSet, RegionType, TransferSizes}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import firesim.configs._

object AXI4SlavePort extends Field[AXI4SlavePortParameters]
object MaxTransferSize extends Field[Int](64)
object BeatBytes extends Field[Int](8)
object IDBits extends Field[Int](4)
object AddrBits extends Field[Int](18)
object NumTransactions extends Field[Int](10000)
object MaxFlight extends Field[Int](128)
object NumMemoryChannels extends Field[Int](1)

class WithSlavePortParams extends Config((site, here, up) => {
  case AXI4SlavePort => AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = Seq(AddressSet(BigInt(0), (BigInt(1) << site(AddrBits)) - 1)),
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, site(MaxTransferSize)),
      supportsRead  = TransferSizes(1, site(MaxTransferSize)),
      interleavedId = Some(0))),
    beatBytes = site(BeatBytes))
})

class WithDefaultMemPort extends Config((site, here, up) => {
  case ExtMem => Some(MemoryPortParams(MasterPortParams(
                      base = BigInt(0),
                      size = BigInt(1) << 16,
                      beatBytes = site(BeatBytes),
                      idBits = site(IDBits)), 1))
})

class WithNTransactions(num: Int) extends Config((site, here, up) => {
  case NumTransactions => num
})

class NT10e5 extends WithNTransactions(100000)
class NT10e6 extends WithNTransactions(1000000)
class NT10e7 extends WithNTransactions(10000000)


class QuadChannel extends WithNMemoryChannels(4)

class DefaultConfig extends Config(
  new WithoutTLMonitors ++
  new WithDefaultMemPort ++
  new WithDefaultMemModel ++
  new Config((site, here, up) => {
    case junctions.NastiKey => junctions.NastiParameters(site(BeatBytes) * 8, site(AddrBits), site(IDBits))
  })
)

class FCFSConfig extends Config(
  new FCFS16GBQuadRank ++
  new DefaultConfig)

class FRFCFSConfig extends Config(
  new FRFCFS16GBQuadRank ++
  new DefaultConfig)

class LLCDRAMConfig extends Config(
  new FRFCFS16GBQuadRankLLC4MB ++
  new DefaultConfig)


// Platform Configs
class DefaultF1Config extends Config(new midas.F1Config)
