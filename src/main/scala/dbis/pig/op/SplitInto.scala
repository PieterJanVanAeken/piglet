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
package dbis.pig.op

import dbis.pig.plan._
import dbis.pig.schema._

import scala.collection.mutable.ArrayBuffer

case class SplitBranch(val output: Pipe, val expr: Expr)

/**
 * SplitInto represents the SPLIT INTO operator of Pig.
 *
 * @param initialInPipeName the names of the input pipe.
 * @param splits a list of split branches (output pipe + condition)
 */
case class SplitInto(in: Pipe, splits: List[SplitBranch]) extends PigOperator {
  _outputs = splits.map(s => s.output)
  _inputs = List(in)

  // override def initialOutPipeNames: List[String] = splits.map{ branch => branch.output.name }

  override def lineageString: String = {
    s"""SPLIT%""" + super.lineageString
  }
}
