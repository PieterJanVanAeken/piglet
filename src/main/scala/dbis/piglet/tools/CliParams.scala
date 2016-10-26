package dbis.piglet.tools

import java.net.URI
import java.io.File
import java.nio.file.{Path,  Paths}

import scopt.OptionParser

import dbis.piglet.BuildInfo
import dbis.piglet.parser.LanguageFeature
import dbis.piglet.tools.logging.LogLevel
import dbis.piglet.tools.logging.LogLevel._

case class CliParams(
  master: String = "local",
  inputFiles: Seq[Path] = Seq.empty,
  compileOnly: Boolean = false,
  outDir: Path = Paths.get("."),
  params: Map[String, String] = Map.empty,
  backend: String = Conf.defaultBackend,
  backendPath: Path = Paths.get("."),
  languages: Seq[LanguageFeature.LanguageFeature] = List(LanguageFeature.PlainPig),
  updateConfig: Boolean = false,
  showPlan: Boolean = false,
  backendArgs: Map[String, String] = Map.empty,
  profiling: Option[URI] = None,
  logLevel: LogLevel = LogLevel.WARN,
  sequential: Boolean = false,
  keepFiles: Boolean = false,
  showStats: Boolean = false,
  paramFile: Option[Path] = None,
  interactive: Boolean = false
) {
  
}

object CliParams {
  
  private def parseLangFeature(strings: Seq[String]) = strings.map { _ match {
      case "sparql" => LanguageFeature.SparqlPig
      case "streaming" => LanguageFeature.StreamingPig
      case "pig" => LanguageFeature.PlainPig
      case "all" => LanguageFeature.CompletePiglet
    }
  }
  
  private lazy val optparser = new OptionParser[CliParams]("Piglet ") {
    head("Piglet", s"ver. ${BuildInfo.version} (built at ${BuildInfo.builtAtString})")
    opt[String]('m', "master") optional() action { (x, c) => c.copy(master = x) } text ("spark://host:port, mesos://host:port, yarn, or local.")
    opt[Unit]('c', "compile") action { (_, c) => c.copy(compileOnly = true) } text ("compile only (don't execute the script)")
    opt[URI]("profiling") optional() action { (x, c) => c.copy(profiling = Some(x)) } text ("Switch on profiling and write to DB. Provide the connection string as schema://host:port/dbname?user=username&pw=password")
    opt[File]('o', "outdir") optional() action { (x, c) => c.copy(outDir = x.toPath()) } text ("output directory for generated code")
    opt[String]('b', "backend") optional() action { (x, c) => c.copy(backend = x ) } text ("Target backend (spark, flink, sparks, ...)")
    opt[String]("backend_dir") optional() action { (x, c) => c.copy(backendPath = new File(x).toPath()) } text ("Path to the diretory containing the backend plugins")
    opt[Seq[String]]('l', "languages") optional() action { (x, c) => c.copy(languages = parseLangFeature(x)) } text ("Accepted language dialects (pig = default, sparql, streaming, cep, all)")
    opt[Map[String, String]]('p', "params") valueName ("name1=value1,name2=value2...") action { (x, c) => c.copy(params = x) } text ("parameter(s) to subsitute")
    opt[File]("param-file") optional() action { (x,c) => c.copy( paramFile = Some(x.toPath()) ) } text ("Path to a file containing parameter value pairs")
    opt[Unit]('u', "update-config") optional() action { (_, c) => c.copy(updateConfig = true) } text (s"update config file in program home (see config file)")
    opt[Unit]('s', "show-plan") optional() action { (_, c) => c.copy(showPlan = true) } text (s"show the execution plan")
    opt[Unit]("sequential") optional() action{ (_,c) => c.copy(sequential = true) } text ("sequential execution (do not merge plans)")
    opt[String]('g', "log-level") optional() action { (x, c) => c.copy(logLevel = LogLevel.withName(x.toUpperCase())) } text ("Set the log level: DEBUG, INFO, WARN, ERROR")
    opt[Unit]('k',"keep") optional() action { (x,c) => c.copy(keepFiles = true) } text ("keep generated files")
    opt[Unit]("show-stats") optional() action { (_,c) => c.copy(showStats = true) } text ("print detailed timing stats at the end")
    opt[Map[String, String]]("backend-args") valueName ("key1=value1,key2=value2...") action { (x, c) => c.copy(backendArgs = x) } text ("parameter(s) to substitute")
    opt[Unit]('q',"quiet") optional() action { (_,c) => c } text ("Don't print header output (does not affect logging and error output)")
    opt[Unit]('i', "interactive") action { (_, c) => c.copy(interactive = true) } text ("start an interactive REPL")
    help("help") text ("prints this usage text")
    version("version") text ("prints this version info")
    arg[File]("<file>...") unbounded() optional() action { (x, c) => c.copy(inputFiles = c.inputFiles :+ x.toPath()) } text ("Pig script files to execute")
  }
  
  def parse(args: Array[String]): CliParams = optparser.parse(args, CliParams()).getOrElse{
    System.exit(1)
    CliParams() // we won't get here. This is just to satisfy the return type.
  }
  
}