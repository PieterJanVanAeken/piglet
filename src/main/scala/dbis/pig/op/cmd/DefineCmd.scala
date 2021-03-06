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

package dbis.pig.op.cmd

import dbis.pig.op.PigOperator
import dbis.pig.expr.Value


/**
 * DefineCmd represents a pseudo operator for the DEFINE statement. This "operator" will
 * be eliminated during building the dataflow plan.
 *
 * @param alias the alias name of the UDF
 * @param scalaName the full classified Scala name of the function
 * @param paramList a list of values uses as the first standard parameters in the function call
 */
case class DefineCmd(alias: String, scalaName: String, paramList: List[Value]) extends PigOperator(List(), List())


