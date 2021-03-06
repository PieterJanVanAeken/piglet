package dbis.pig.backends

import dbis.pig.tools.Conf
import dbis.pig.tools.logging.PigletLogging
import java.net.URLClassLoader


/**
 * Manages the available backends. 
 */
object BackendManager extends PigletLogging {
 
  /**
   * Loaded backend
   */
  private var _backend: BackendConf = _

  def backend = _backend

  def backend_= (conf: BackendConf) = _backend = conf

  /**
   * Get the runner class for the backend with the given name
   * 
   * @param backend The name of the backend. 
   * @return Returns a new instance of the runner class (whose FQN was specified in Piglet config file)
   */
  def backend(backend: String): BackendConf = {
    
    val className = Conf.backendConf(backend)
    
    logger.debug(s"""loading runner class for backend "$backend" with name: $className""")
    
    Class.forName(className).newInstance().asInstanceOf[BackendConf]
  }

}
