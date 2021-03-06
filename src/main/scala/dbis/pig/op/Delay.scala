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

/**
 * Delay represents the DELAY operator of Pig.
 *
 * @param out the output pipe (relation).
 * @param in the input pipe.
 * @param size the percentage of input tuples that is passed to the output pipe
 * @param wtime the time for delaying the processing
 *
 */
case class Delay(
    private val out: Pipe, 
    private val in: Pipe, 
    size: Double, 
    wtime: Int
  ) extends PigOperator(out, in) {

  override def lineageString: String = {
    s"""DELAY%${size}%${wtime}%""" + super.lineageString
  }

}
