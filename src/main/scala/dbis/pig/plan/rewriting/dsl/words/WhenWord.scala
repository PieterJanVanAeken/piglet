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
package dbis.pig.plan.rewriting.dsl.words

import dbis.pig.op.PigOperator
import dbis.pig.plan.rewriting.dsl.builders.PigOperatorBuilder
import dbis.pig.plan.rewriting.dsl.traits.{BuilderT, EndWordT}

/** Class for chaining checks to a Builder.
  *
  * @param b
  * @param check
  * @tparam FROM
  * @tparam TO
  */
class WhenWord[FROM, TO](override val b: BuilderT[FROM, TO], val check: (FROM => Boolean))
  extends EndWordT[FROM, TO] {
  b.check = check

  def and(check: (FROM => Boolean)) = {
    val oldcheck = b.check.get
    val newcheck = { t: FROM =>
      (oldcheck(t) && check(t))
    }

    new WhenWord(b, newcheck)
  }

  def or(check: (FROM => Boolean)) = {
    val oldcheck = b.check.get
    val newcheck = { t: FROM =>
      (oldcheck(t) || check(t))
    }

    new WhenWord(b, newcheck)
  }

  /** Add a check in the form of a pattern match before the application of the function contained in the builder. If
    * the pattern matches, the function will be called.
    *
    * Use it like
    *
    * {{{
    *   andMatches { case _ : PigOperator => }
    * }}}
    */
  def andMatches(check: scala.PartialFunction[FROM, _]) = {
    val oldcheck = b.check.get
    val newcheck = { t: FROM =>
      (oldcheck(t) && check.isDefinedAt(t))
    }

    new WhenWord(b, newcheck)
  }

  /** Add a check in the form of a pattern match before the application of the function contained in the builder. If
    * the pattern matches, the function will not be called.
    *
    * Use it like
    *
    * {{{
    *   orMatches { case _ : PigOperator => }
    * }}}
    */
  def orMatches(check: scala.PartialFunction[FROM, _]) = {
    val oldcheck = b.check.get
    val newcheck = { t: FROM =>
      (oldcheck(t) || check.isDefinedAt(t))
    }

    new WhenWord(b, newcheck)
  }
}
