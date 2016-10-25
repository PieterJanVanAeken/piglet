/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dbis.piglet

import scala.io.Source
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.JavaConverters._

import scopt.OptionParser

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.net.URI

import dbis.piglet.op.PigOperator
import dbis.piglet.parser.PigParser
import dbis.piglet.parser.LanguageFeature
import dbis.piglet.plan.DataflowPlan
import dbis.piglet.plan.rewriting.Rewriter._
import dbis.piglet.plan.rewriting.Rules
import dbis.piglet.schema.SchemaException
import dbis.piglet.tools.FileTools
import dbis.piglet.plan.MaterializationManager
import dbis.piglet.tools.Conf
import dbis.piglet.backends.BackendManager
import dbis.piglet.backends.BackendConf
import dbis.piglet.tools.{DepthFirstTopDownWalker, BreadthFirstTopDownWalker}
import dbis.piglet.mm.{DataflowProfiler, MaterializationPoint}
import dbis.piglet.codegen.PigletCompiler
import dbis.piglet.tools.logging.PigletLogging
import dbis.piglet.tools.logging.LogLevel
import dbis.piglet.tools.logging.LogLevel._
import dbis.piglet.plan.PlanMerger
import dbis.setm.SETM
import dbis.setm.SETM.timing
import dbis.piglet.plan.InvalidPlanException

object Piglet extends PigletLogging {

  case class CompilerConfig(master: String = "local",
                            inputs: Seq[File] = Seq.empty,
                            compile: Boolean = false,
                            outDir: String = ".",
                            params: Map[String, String] = Map(),
                            backend: Option[String] = None,//Conf.defaultBackend,
                            backendPath: Path = Paths.get("."),
                            languages: Seq[String] = Seq.empty,
                            updateConfig: Boolean = false,
                            showPlan: Boolean = false,
                            backendArgs: Map[String, String] = Map(),
                            profiling: Option[URI] = None,
                            loglevel: Option[String] = None,
                            sequential: Boolean = false,
                            keepFiles: Boolean = false,
                            stats: Boolean = false,
                            paramFile: Option[File] = None
                           )


  var master: String = "local"
  var backend: String = null
  var backendPath: Path = null
  var languageFeatures = List(LanguageFeature.PlainPig)
  var logLevel: Option[String] = None
  var keepFiles = false

  def main(args: Array[String]): Unit = {

    var inputFiles: Seq[Path] = null
		var backendCLI: Option[String] = None
    var compileOnly: Boolean = false
    var outDir: Path = null
    var params: Map[String, String] = null
    var updateConfig = false
    var showPlan = false
    var backendArgs: Map[String, String] = null
    var profiling: Option[URI] = None
    var sequential = false
    var showStats = false
    var paramFile: Option[Path] = None


    val parser = new OptionParser[CompilerConfig]("PigletCompiler") {
      head("PigletCompiler", s"ver. ${BuildInfo.version} (built at ${BuildInfo.builtAtString})")
      opt[String]('m', "master") optional() action { (x, c) => c.copy(master = x) } text ("spark://host:port, mesos://host:port, yarn, or local.")
      opt[Unit]('c', "compile") action { (_, c) => c.copy(compile = true) } text ("compile only (don't execute the script)")
      opt[URI]("profiling") optional() action { (x, c) => c.copy(profiling = Some(x)) } text ("Switch on profiling and write to DB. Provide the connection string as schema://host:port/dbname?user=username&pw=password")
      opt[String]('o', "outdir") optional() action { (x, c) => c.copy(outDir = x) } text ("output directory for generated code")
      opt[String]('b', "backend") optional() action { (x, c) => c.copy(backend = Some(x) ) } text ("Target backend (spark, flink, sparks, ...)")
      opt[String]("backend_dir") optional() action { (x, c) => c.copy(backendPath = new File(x).toPath()) } text ("Path to the diretory containing the backend plugins")
      opt[Seq[String]]('l', "languages") optional() action { (x, c) => c.copy(languages = x) } text ("Accepted language dialects (pig = default, sparql, streaming, cep, all)")
      opt[Map[String, String]]('p', "params") valueName ("name1=value1,name2=value2...") action { (x, c) => c.copy(params = x) } text ("parameter(s) to subsitute")
      opt[File]("param-file") optional() action { (x,c) => c.copy( paramFile = Some(x) ) } text ("Path to a file containing parameter value pairs")
      opt[Unit]('u', "update-config") optional() action { (_, c) => c.copy(updateConfig = true) } text (s"update config file in program home (see config file)")
      opt[Unit]('s', "show-plan") optional() action { (_, c) => c.copy(showPlan = true) } text (s"show the execution plan")
      opt[Unit]("sequential") optional() action{ (_,c) => c.copy(sequential = true) } text ("sequential execution (do not merge plans)")
      opt[String]('g', "log-level") optional() action { (x, c) => c.copy(loglevel = Some(x.toUpperCase())) } text ("Set the log level: DEBUG, INFO, WARN, ERROR")
      opt[Unit]('k',"keep") optional() action { (x,c) => c.copy(keepFiles = true) } text ("keep generated files")
      opt[Unit]("show-stats") optional() action { (_,c) => c.copy(stats = true) } text ("print detailed timing stats at the end")
      opt[Map[String, String]]("backend-args") valueName ("key1=value1,key2=value2...") action { (x, c) => c.copy(backendArgs = x) } text ("parameter(s) to substitute")
      help("help") text ("prints this usage text")
      version("version") text ("prints this version info")
      arg[File]("<file>...") unbounded() optional() action { (x, c) => c.copy(inputs = c.inputs :+ x) } text ("Pig script files to execute")
    }

    // parser.parse returns Option[C]
    parser.parse(args, CompilerConfig()) match {
      case Some(config) => {
        // do stuff
        master = config.master
        inputFiles = config.inputs.map { f => f.toPath() } //Paths.get(config.input)
        compileOnly = config.compile
        outDir = Paths.get(config.outDir)
        params = config.params
        backendCLI = config.backend
        backendPath = config.backendPath
        updateConfig = config.updateConfig
        showPlan = config.showPlan
        backendArgs = config.backendArgs
        languageFeatures = config.languages.toList.map { _ match {
          case "sparql" => LanguageFeature.SparqlPig
          case "streaming" => LanguageFeature.StreamingPig
          case "pig" => LanguageFeature.PlainPig
          case "all" => LanguageFeature.CompletePiglet
        }}
        // note: for some backends we could determine the language automatically
        profiling = config.profiling
        logLevel = config.loglevel
        sequential = config.sequential
        keepFiles = config.keepFiles
        showStats = config.stats
        paramFile = config.paramFile.map { f => f.toPath() }
      }
      case None =>
        // arguments are bad, error message will have been displayed
        return
    }

    // start statistics collector SETM if needed
    startCollectStats(showStats)

    // set the log level as defined in the parameters
    if (logLevel.isDefined) {
    	try {
    		logger.setLevel(LogLevel.withName(logLevel.get))
    	} catch {
    	case e: NoSuchElementException => println(s"ERROR: invalid log level ${logLevel} - continue with default")
    	}
    }


    /* Copy config file to the user's home directory
     * IMPORTANT: This must be the first call to Conf
     * Otherwise, the config file was already loaded before we could copy the new one
     */
    if (updateConfig) {
      // in case of --update we just copy the config file and exit
      Conf.copyConfigFile()
      println(s"Config file copied to ${Conf.programHome} - exitting now")
      sys.exit()
    }

    // set default backend if necessary now - we had to "wait" until after the update conf call
    backend = backendCLI.getOrElse(Conf.defaultBackend)

    // get the input files
    val files = inputFiles.takeWhile { p => !p.startsWith("-") }
    if (files.isEmpty) {
      // because the file argument was optional we have to check it here
      println("Error: Missing argument <file>...\nTry --help for more information.")
      sys.exit(-1)
    }

    val paramMap = MutableMap.empty[String, String]

    /*
     * If the parameter file is given, read each line, split it by = and add
     * the mapping to the parameters list
     */
    if(paramFile.isDefined) {
      val s = Files.readAllLines(paramFile.get).asScala
          .map { line => line.split("=", 2) } // 2 tells split to apply the regex 1 time (n-1) - the result array will be of size 2 (n)
          .map { arr => (arr(0) -> arr(1) )}

      paramMap ++= s
    }

    /* add the parameters supplied via CLI to the paramMap after we read the file
     * this way, we can override the values in the file via CLI
     */
    paramMap ++= params

    if(paramMap.nonEmpty)
    	logger.info(s"provided parameters: ${paramMap.map{ case (k,v) => s"$k -> $v"}.mkString("\n")}")


    /* if profiling is enabled, check the HTTP server
     * If it's unavailable print an error and stop
     */
    if(profiling.isDefined) {
      val reachable = FileTools.checkHttpServer(profiling.get)

      if(! reachable) {
        logger.error(s"Statistics management server is not reachable at ${profiling.get}. Aborting")
        return
      }
    }


    // start processing
    run(files, outDir, compileOnly, master, backend, languageFeatures, paramMap.toMap, backendPath, backendArgs, profiling, showPlan, sequential)

    // at the end, show the statistics
    if(showStats) {
      // collect and print runtime stats
      collectStats
    }

  } // main


  def run(inputFile: Path, outDir: Path, compileOnly: Boolean, master: String, backend: String,
          langFeatures: List[LanguageFeature.LanguageFeature], params: Map[String, String], backendPath: Path,
          backendArgs: Map[String, String], profiling: Option[URI], showPlan: Boolean, sequential: Boolean): Unit = timing("execution") {
    run(Seq(inputFile), outDir, compileOnly, master, backend, langFeatures, params, backendPath,
      backendArgs, profiling, showPlan, sequential)
  }

  /**
    * Start compiling the Pig script into a the desired program
    */
  def run(inputFiles: Seq[Path], outDir: Path, compileOnly: Boolean, master: String, backend: String,
          langFeatures: List[LanguageFeature.LanguageFeature], params: Map[String, String],
          backendPath: Path, backendArgs: Map[String, String], profiling: Option[URI],
          showPlan: Boolean, sequential: Boolean): Unit = {

    try {


      BackendManager.init(backend)

      if (BackendManager.backend.raw) {
        if (compileOnly) {
          logger.error("Raw backends do not support compile-only mode! Aborting")
          return
        }

        if (profiling.isDefined) {
          logger.error("Raw backends do not support profiling yet! Aborting")
          return
        }

        inputFiles.foreach { file => runRaw(file, master, backendArgs) }

      } else {
        runWithCodeGeneration(inputFiles, outDir, compileOnly, master, backend, langFeatures, params, backendPath, backendArgs, profiling, showPlan, sequential)
      }

    } catch {
      // don't print full stack trace to error
      case e: Exception =>
        logger.error(s"An error occured: ${e.getMessage}")
        logger.debug(e.getMessage, e)
    }
  }

  /**
    *
    * @param file
    * @param master
    * @param backendConf
    * @param backendArgs
    */
  def runRaw(file: Path, master: String, backendArgs: Map[String, String]) = timing("execute raw") {
    logger.debug(s"executing in raw mode: $file with master $master for backend ${BackendManager.backend.name} with arguments ${backendArgs.mkString(" ")}")
    val runner = BackendManager.backend.runnerClass
    runner.executeRaw(file, master, backendArgs)
  }


  /**
   * Run with the provided set of files using the specified backend, that is _not_ a raw backend.
   *
   * @param inputFiles The list of files to execute
   * @param outDir The target directory to write generated files to
   * @param compileOnly Code generation and compilation only, but do not submit job
   * @param master Master specification (local, yarn, mesos, ...)
   * @param langFeature activated language features
   * @param params Parameter substitutions
   * @param backendPath Base directory for backends
   * @param backendArgs Parameters to pass to the backend runner (e.g., number of executors, ...)
   * @param profiling Optional URL of the statistics listener, if NONE, statistics will not be collected
   * @param showPlan Print the generated final plan to stdout
   * @param sequential If multiple scripts are given, execute them sequentially and do _not_ try to merge them
   *
   */
  def runWithCodeGeneration(inputFiles: Seq[Path], outDir: Path, compileOnly: Boolean, master: String, backend: String,
                            langFeatures: List[LanguageFeature.LanguageFeature], params: Map[String, String],
                            backendPath: Path, backendArgs: Map[String, String],
                            profiling: Option[URI], showPlan: Boolean, sequential: Boolean): Unit = timing("run with generation") {

    logger.debug("start parsing input files")

    var schedule = ListBuffer.empty[(DataflowPlan,Path)]

    for(file <- inputFiles) {
      // foreach file, generate the data flow plan and store it in our schedule
      PigletCompiler.createDataflowPlan(file, params, backend, langFeatures) match {
        case Some(v) => schedule += ((v, file))
        case None => // in case of an error (no plan genrated for file) abort current execution
          throw InvalidPlanException(s"failed to create dataflow plan for $file - aborting")
      }
    }


    /*
     * if we have got more than one plan and we should not execute them
     * sequentially, then try to merge them into one plan
     */
    if(schedule.size > 1 && !sequential) {
      logger.debug("Start merging plans")

      // merge plans into one plan
      val mergedPlan = PlanMerger.mergePlans( schedule.map{case (plan, _) => plan } )

      // adjust the new schedule. It now contains only the merged plan, with a new generated file name
      schedule = ListBuffer((mergedPlan, Paths.get(s"merged_${System.currentTimeMillis()}.pig")))
    }

    val templateFile = BackendManager.backend.templateFile

		val profiler = profiling.map { u => new DataflowProfiler(Some(u)) }


		// begin global analysis phase

		// count occurrences of each operator in schedule
    profiler.foreach { p => p.createOpCounter(schedule) }

    logger.debug("start processing created dataflow plans")

    for ((plan, path) <- schedule) timing("execute plan") {

      // 3. now, we should apply optimizations
      var newPlan = plan

      // process explicit MATERIALIZE operators
      if (profiling.isDefined) {
        val mm = new MaterializationManager(Conf.materializationBaseDir, profiling.get)
        newPlan = processMaterializations(newPlan, mm)
      }


      // rewrite WINDOW operators for Flink streaming
      if (langFeatures.contains(LanguageFeature.StreamingPig) && backend == "flinks")
        newPlan = processWindows(newPlan)

      Rules.registerBackendRules(backend)
      newPlan = processPlan(newPlan)

      // find materialization points
      profiler.foreach { p => p.addMaterializationPoints(newPlan) }

      logger.debug("finished optimizations")

      if (showPlan) {
        println("final plan = {")
        newPlan.printPlan()
        println("}")
      }

      try {
        // if this does _not_ throw an exception, the schema is ok
        // TODO: we should do this AFTER rewriting!
        newPlan.checkSchemaConformance
      } catch {
        case e: SchemaException => {
          logger.error(s"schema conformance error in ${e.getMessage} for plan")
          return
        }
      }

      val scriptName = path.getFileName.toString().replace(".pig", "")
      logger.debug(s"using script name: $scriptName")


      PigletCompiler.compilePlan(newPlan, scriptName, outDir, backendPath, templateFile, backend, profiling, keepFiles) match {
        // the file was created --> execute it
        case Some(jarFile) =>
          if (!compileOnly) {
            // 4. and finally deploy/submit
            val runner = BackendManager.backend.runnerClass
            logger.debug(s"using runner class ${runner.getClass.toString()}")

            logger.info( s"""starting job at "$jarFile" using backend "$backend" """)
            timing("job execution") {
              runner.execute(master, scriptName, jarFile, backendArgs)
            }
          } else
            logger.info("successfully compiled program - exiting.")

        case None => logger.error(s"creating jar file failed for ${path}")
      }
    }
  }


//  /**
//    * Sets the various configuration parameters to the given string values.
//    *
//    * @param master the master for Spark/Flink
//    * @param backend the backend used for execution (spark, flink, sparks, flinks, ...)
//    * @param language the Piglet dialect used for processing the script
//    * @param backendDir the directory where the backend-specific jars are located
//    */
//  def setConfig(master: String = "local", backend: String = "spark", language: String = "pig",
//                backendDir: Path = Paths.get(".")): Unit = {
//    Piglet.master = master
//    Piglet.backend = backend
//    Piglet.backendPath = backendDir
//    Piglet.languageFeatures = List(language match {
//      case "sparql" => LanguageFeature.SparqlPig
//      case "streaming" => LanguageFeature.StreamingPig
//      case "pig" => LanguageFeature.PlainPig
//      case _ => LanguageFeature.PlainPig
//    })
//    val backendConf = BackendManager.backend(backend)
//    BackendManager.backend = backendConf
//  }

//  /**
//    * Compiles and executes the given Piglet script.
//    *
//    * @param fileName the file name of the Piglet script
//    */
//  def compileFile(fileName: String): Unit = {
//    val path = Paths.get(fileName)
//    PigletCompiler.createDataflowPlan(path, Map[String, String](), backend, languageFeatures) match {
//      case Some(p) => compileAndExecute(p)
//      case None => {}
//    }
//  }

//  /**
//    * Compiles and executes the given Piglet script represented as string.
//    *
//    * @param source a string containing the Piglet code
//    */
//  def compile(source: String): List[Any] = {
//    PigletCompiler.createDataflowPlan(Paths.get(source), Map[String, String](), backend, languageFeatures) match {
//      case Some(p) => {
//        val res = compileAndExecute(p)
//        res.split("\n").toList.map(_.split(","))
//      }
//      case None => List()
//    }
//  }

//  private def compileAndExecute(p: DataflowPlan): String = {
//
//    val plan = processPlan(p)
//    plan.checkSchemaConformance
//
//    val backendConf = BackendManager.backend
//    val outDir = Paths.get(".")
//    val scriptName = "__r_piglet"
//    val templateFile = backendConf.templateFile
//    val jarFile = Conf.backendJar(backend)
//    val profiling: Option[URI] = None
//
//    val res: String = PigletCompiler.compilePlan(plan, scriptName, outDir,
//        backendPath, templateFile, backend, profiling, false) match {
//
//      case Some(jarFile) => {
//        val runner = backendConf.runnerClass
//        logger.debug(s"using runner class ${runner.getClass.toString()}")
//
//        logger.info( s"""starting job at "$jarFile" using backend "$backend" """)
//        val resStream = new java.io.ByteArrayOutputStream
//        Console.withOut(resStream)(runner.execute(master, scriptName, jarFile, Map[String, String]()))
//        resStream.toString
//      }
//      case None => ""
//    }
//    FileTools.recursiveDelete(scriptName)
//    res
//  }

  def startCollectStats(enable: Boolean) = if(enable) SETM.enable else SETM.disable
  def collectStats = SETM.collect()

}