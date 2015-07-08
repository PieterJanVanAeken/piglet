/*
 * Copyright (c) 2015 The Piglet team,
 *                    All Rights Reserved.
 *
 * This file is part of the Piglet package.
 *
 * PipeFabric is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License (GPL) as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This package is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; see the file LICENSE.
 * If not you can find the GPL at http://www.gnu.org/copyleft/gpl.html
 */
package dbis.pig.op

import dbis.pig.plan.{InvalidPlanException, DataflowPlan}
import dbis.pig.schema._


/**
 * A trait for the GENERATE part of a FOREACH operator.
 */
trait ForeachGenerator {}

/**
 * GeneratorExpr represents a single expression of a Generator.
 *
 * @param expr
 * @param alias
 */
case class GeneratorExpr(expr: ArithmeticExpr, alias: Option[Field] = None)

/**
 * GeneratorList implements the ForeachGenerator trait and is used to represent
 * the FOREACH ... GENERATE operator.
 *
 * @param exprs
 */
case class GeneratorList(exprs: List[GeneratorExpr]) extends ForeachGenerator

/**
 * GeneratorPlan implements the ForeachGenerator trait and is used to represent
 * a nested FOREACH.
 *
 * @param subPlan
 */
case class GeneratorPlan(subPlan: List[PigOperator]) extends ForeachGenerator

/**
 * Foreach represents the FOREACH operator of Pig.
 *
 * @param initialOutPipeName the name of the output pipe (relation).
 * @param initialInPipeName the name of the input pipe
 * @param generator the generator (a list of expressions or a subplan)
 */
case class Foreach(out: Pipe, in: Pipe, generator: ForeachGenerator) extends PigOperator {
  _outputs = List(out)
  _inputs = List(in)

  var subPlan: Option[DataflowPlan] = None

  override def preparePlan: Unit = {
    generator match {
      case GeneratorPlan(opList) => {
        // nested foreach require special handling: we construct a subplan for the operator list
        subPlan = Some(new DataflowPlan(opList))
      }
      case _ => {}
    }
  }

  def constructFieldList(exprs: List[GeneratorExpr]): Array[Field] =
    exprs.map(e => {
      e.alias match {
        // if we have an explicit schema (i.e. a field) then we use it
        case Some(f) => {
          if (f.fType == Types.ByteArrayType) {
            // if the type was only bytearray, we should check the expression if we have a more
            // specific type
            val res = e.expr.resultType(inputSchema)
            Field(f.name, res._2)
          }
          else
            f
        }
        // otherwise we take the field name from the expression and
        // the input schema
        case None => val res = e.expr.resultType(inputSchema); Field(res._1, res._2)
      }
    }).toArray

  override def constructSchema: Option[Schema] = {
    generator match {
      case GeneratorList(expr) => {
        val fields = constructFieldList(expr)

        schema = Some(new Schema(new BagType(new TupleType(fields), outPipeName)))
      }
      case GeneratorPlan(plan) => {
        // if we have ConstructBag operators in our subplan, we should add schema information
        plan.filter(p => p.isInstanceOf[ConstructBag]).foreach(p => p.asInstanceOf[ConstructBag].parentSchema = inputSchema)

        // we invoke constructSchema for all operators of the subplan
        plan.foreach(op => op.constructSchema)

        /*
         * TODO: expressions in generate can refer to _all_ bags in the subplan
         */
        val genOp = plan.last
        if (genOp.isInstanceOf[Generate]) {
          schema = genOp.schema
          schema.get.setBagName(outPipeName)
        }
        else
          throw new InvalidPlanException("last statement in nested foreach must be a generate")
      }
    }
    schema
  }

  override def checkSchemaConformance: Boolean = {
    generator match {
      case GeneratorList(expr) => inputSchema match {
        case Some(s) => {
          // if we know the schema we check all named fields
          expr.map(_.expr.traverseAnd(s, Expr.checkExpressionConformance)).foldLeft(true)((b1: Boolean, b2: Boolean) => b1 && b2)
        }
        case None => {
          // if we don't have a schema all expressions should contain only positional fields
          expr.map(_.expr.traverseAnd(null, Expr.containsNoNamedFields)).foldLeft(true)((b1: Boolean, b2: Boolean) => b1 && b2)
        }
      }
      case GeneratorPlan(plan) => true // TODO: implement checkSchemaConformance for nested foreach
    }
  }

  /**
   * Returns the lineage string describing the sub-plan producing the input for this operator.
   *
   * @return a string representation of the sub-plan.
   */
  override def lineageString: String = {
    generator match {
      case GeneratorList(expr) => s"""FOREACH%${expr}%""" + super.lineageString
      case GeneratorPlan(plan) => s"""FOREACH""" + super.lineageString // TODO: implement lineageString for nested foreach
    }
  }

  def containsFlatten(onBag: Boolean = false): Boolean = {
    val theSchema = inputSchema.orNull
    generator match {
      case GeneratorList(exprs) =>
        if (onBag) {
          exprs.map(g => g.expr.traverseOr(theSchema, Expr.containsFlattenOnBag)).exists(b => b)
        }
        else
          exprs.map(g => g.expr.traverseOr(theSchema, Expr.containsFlatten)).exists(b => b)
      case GeneratorPlan(plan) =>
        false // TODO: what happens if GENERATE contains flatten?
    }
  }
}

/**
 * GENERATE represents the final generate statement inside a nested FOREACH.
 *
 * @param exprs list of generator expressions
 */
case class Generate(exprs: List[GeneratorExpr]) extends PigOperator {
  _outputs = List()
  _inputs = List()

  // TODO: what do we need here?
  override def constructSchema: Option[Schema] = {
    val fields = constructFieldList(exprs)
    schema = Some(new Schema(new BagType(new TupleType(fields))))
    schema
  }

  // TODO: eliminate replicated code
  def constructFieldList(exprs: List[GeneratorExpr]): Array[Field] =
    exprs.map(e => {
      e.alias match {
        // if we have an explicit schema (i.e. a field) then we use it
        case Some(f) => {
          if (f.fType == Types.ByteArrayType) {
            // if the type was only bytearray, we should check the expression if we have a more
            // specific type
            val res = e.expr.resultType(inputSchema)
            Field(f.name, res._2)
          }
          else
            f
        }
        // otherwise we take the field name from the expression and
        // the input schema
        case None => val res = e.expr.resultType(inputSchema); Field(res._1, res._2)
      }
    }).toArray
}

/**
 * This operator is a pseudo operator used inside a nested FOREACH to construct a new bag from an expression.
 *
 * @param initialOutPipeName the name of the initial output pipe (relation) which is needed to construct the plan, but
 *                           can be changed later.
 * @param refExpr a reference referring to an expression constructing a relation (bag).
 */
case class ConstructBag(out: Pipe, refExpr: Ref) extends PigOperator {
  _outputs = List(out)
  _inputs = List()

  // TODO: what do we need here?
  var parentSchema: Option[Schema] = None

  override def constructSchema: Option[Schema] = {
    parentSchema match {
      case Some(s) => {
        // first, we determine the field in the schema
        val field = refExpr match {
          case DerefTuple(t, r) => t match {
            case NamedField(n) => s.field(n)
            case PositionalField(p) => s.field(p)
          }
          case _ => throw InvalidPlanException("unexpected expression in ConstructBag")
        }
        // 2. we extract the type (which should be a BagType, MapType or TupleType)
        if (!field.fType.isInstanceOf[ComplexType])
          throw InvalidPlanException("invalid expression in ConstructBag")
        val fieldType = field.fType.asInstanceOf[ComplexType]

        val componentType = refExpr match {
          case DerefTuple(t, r) => r match {
            case NamedField(n) => fieldType.typeOfComponent(n)
            case PositionalField(p) => fieldType.typeOfComponent(p)
          }
          case _ => throw InvalidPlanException("unexpected expression in ConstructBag")
        }
        // construct a schema from the component type
        schema = Some(new Schema(new BagType(new TupleType(Array(Field("t", componentType))), outPipeName)))

      }
      case None => None
    }
    schema
  }
}