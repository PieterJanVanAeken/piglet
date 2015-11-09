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

package dbis.pig.backends.spark

import scala.Numeric.Implicits._
import scala.collection.mutable.ListBuffer

object PigFuncs {
  def average[T: Numeric](bag: Iterable[T]) : Double = sum(bag).toDouble / count(bag).toDouble

  def count(bag: Iterable[Any]): Int = bag.size

  def sum[T: Numeric](bag: Iterable[T]): T = bag.sum

  def min[T: Ordering](bag: Iterable[T]): T = bag.min

  def max[T: Ordering](bag: Iterable[T]): T = bag.max

  /*
   * String functions
   */
  def tokenize(s: String, delim: String = """[, "]""") = s.split(delim)
  
  def startswith(haystack: String, prefix: String) = haystack.startsWith(prefix)
  
  def strlen(s: String) = s.length()

  /*
   * Incremental versions of the aggregate functions - used for implementing ACCUMULATE.
   */
  def incrSUM(acc: Int, v: Int) = { println(s"SUM($acc, $v)"); acc + v }
  def incrCOUNT(acc: Int, v: Int) = { println(s"COUNT($acc, $v)"); acc + 1 }
  def incrMIN(acc: Int, v: Int) = math.min(acc, v)
  def incrMAX(acc: Int, v: Int) = math.max(acc, v)
}
