// See LICENSE for license details.

package midas.targetutils.xdc

import chisel3.Data
import chisel3.experimental.{ChiselAnnotation}
import firrtl.{RenameMap}
import firrtl.annotations.{Annotation, NoTargetAnnotation, ReferenceTarget, HasSerializationHints}
import firrtl.transforms.{DontTouchAllTargets}

import midas.targetutils.ReferenceTargetRenamer

private [midas] trait XDCAnnotationConstants {
  val specifierRegex = "\\{}".r
  val xdcHeader = "# This file was generated by WriteXDCFile from collected XDCAnnotations.\n"
}

/**
  * All Golden-Gate-known output XDC files extend this.
  */
sealed trait XDCDestinationFile {
  def fileSuffix: String
  def preLink: Boolean
}

/**
  * In hierarchal FPGA flows, like EC2 F1's, additional levels of path are
  * introduced once a partition has been linked into the larger design. These
  * indicate to the XDC emission pass whether an additional path should be
  * appended to references that appear in XDC snippets based on whether the
  * constraints will be applied before or after this linking phase.
  *
  */
trait IsPostLink { self: XDCDestinationFile => def preLink = false }
trait IsPreLink { self: XDCDestinationFile => def preLink = true }

object XDCFiles {
  case object Synthesis extends XDCDestinationFile with IsPreLink { def fileSuffix = ".synthesis.xdc" }
  case object Implementation extends XDCDestinationFile with IsPostLink { def fileSuffix = ".implementation.xdc" }
  val allFiles = Seq(Synthesis, Implementation)
}

/**
  * Encode a string to be emitted to an XDC file with specifiers derived from
  * ReferenceTargets (RTs). RTs are called out in the format string using "{}" a la
  * Python. This makes the emitted XDC more robust against the module hierarchy
  * manipulations (such as promotion/extraction and linking) that Golden Gate
  * performs.
  *
  * For example:
  *   XDCAnnotation("get_pins -of [get_clocks {}]", clockRT)
  *
  * Would eventually emit:
  *   get_pins -of [get_clocks absolute/path/to/clock]
  *
  * Here the emission pass by default creates a full instance path to the
  * reference to avoid multiple matches.
  *
  * Restrictions:
  * 1. Multiple specifiers in a string are permitted. However, to simplify
  *    emission under more complex duplication conditions, all RTs must share the
  *    same explicit root module (i.e,, their module parameter is the same).
  * .
  * 2. Specifiers currently cannot point at aggregates. This will be resolved in a future PR.
  *
  */

case class XDCAnnotation(
    destinationFile: XDCDestinationFile,
    formatString: String,
    argumentList: ReferenceTarget*)
    extends Annotation with XDCAnnotationConstants with HasSerializationHints
    // This is included until we figure out how to gracefully handle deletion.
    with DontTouchAllTargets {
  def update(renames: RenameMap): Seq[firrtl.annotations.Annotation] = {
    val renamer = new ReferenceTargetRenamer(renames)
    Seq(XDCAnnotation(destinationFile, formatString, argumentList.map(a => renamer.exactRename(a)):_*))
  }
  def typeHints: Seq[Class[_]] = Seq(classOf[XDCDestinationFile])
}

/**
  * Chisel-side sugar for emitting XDCAnnotations.
  *
  * For example, to tell Vivado not to optimize away a node:
  * {{{
  *   val myUsefulWire = Wire(<...>)
  *   // Note, (currently) calling dontTouch(myUsefulWire) does not get passed through to Vivado.
  *   XDC(XDCFiles.Synthesis, "set_property DONT_TOUCH [get_cells {}]", myUsefulWire)
  * }}}
  *
  */
object XDC extends XDCAnnotationConstants {
  def apply(
      destinationFile: XDCDestinationFile,
      formatString: String,
      argumentList: Data*): Unit = {
    val numArguments = specifierRegex.findAllIn(formatString).size
    require(numArguments == argumentList.size,
      s"Format string requires ${numArguments}, provided ${argumentList.size}")
    chisel3.experimental.annotate(new ChiselAnnotation {
      def toFirrtl = XDCAnnotation(destinationFile, formatString, argumentList.map(_.toTarget):_*)
    })
  }
}


/**
  *  Provides the absolute paths to firrtl-emitted module in the context of the
  *  FPGA project before and after linking. If the firrtl-emitted module is the
  *  top-level, set the path to None.
  */
case class XDCPathToCircuitAnnotation(
  preLinkPath: Option[String],
  postLinkPath: Option[String]) extends NoTargetAnnotation

object SpecifyXDCCircuitPaths {
  def apply(preLinkPath: Option[String], postLinkPath: Option[String]): Unit = {
    chisel3.experimental.annotate(new ChiselAnnotation {
      def toFirrtl = XDCPathToCircuitAnnotation(preLinkPath, postLinkPath)
    })
  }
}
