package io.kaitai.struct.exprlang

import fastparse._
import Lexical.kw

/**
  * Loosely based on /pythonparse/shared/src/main/scala/pythonparse/
  * from FastParse, Copyright (c) 2014 Li Haoyi (haoyi.sg@gmail.com)
  * http://www.lihaoyi.com/fastparse/
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
  * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
  * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
  * the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
  * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
  * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
  * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
  * IN THE SOFTWARE.
  */
object Expressions {
  implicit def whitespace(cfg: P[_]): P[Unit] = Lexical.wscomment(cfg)

  def NAME[_: P]: P[Ast.identifier] = Lexical.identifier
  def TYPE_NAME[_: P]: P[Ast.typeId] = P("::".!.? ~ NAME.rep(1, "::") ~ ("[" ~ "]").!.?).map {
    case (first, names: Seq[Ast.identifier], arrayStr) =>
      Ast.typeId(first.nonEmpty, names.map((el) => el.name), arrayStr.nonEmpty)
  }
  def INT_NUMBER[_: P] = Lexical.integer
  def FLOAT_NUMBER[_: P] = Lexical.floatnumber
  def STRING[_: P]: P[String] = Lexical.stringliteral

  def test[_: P]: P[Ast.expr] = P( or_test ~ ("?" ~ test ~ ":" ~ test).? ).map{
      case (x, None) => x
      case (condition, Some((ifTrue, ifFalse))) => Ast.expr.IfExp(condition, ifTrue, ifFalse)
    }
  def or_test[_: P] = P( and_test.rep(1, kw("or")) ).map{
    case Seq(x) => x
    case xs => Ast.expr.BoolOp(Ast.boolop.Or, xs)
  }
  def and_test[_: P] = P( not_test.rep(1, kw("and")) ).map{
    case Seq(x) => x
    case xs => Ast.expr.BoolOp(Ast.boolop.And, xs)
  }
  def not_test[_: P]: P[Ast.expr] = P( (kw("not") ~ not_test).map(Ast.expr.UnaryOp(Ast.unaryop.Not, _)) | comparison )
  def comparison[_: P]: P[Ast.expr] = P( expr ~ (comp_op ~ expr).? ).map{
    case (lhs, None) => lhs
    case (lhs, Some(chunks)) =>
      val (op, rhs) = chunks
      Ast.expr.Compare(lhs, op, rhs)
  }

  // Common operators, mapped from their
  // strings to their type-safe representations
  def op[T, _: P](s: => P[Unit], rhs: T) = s.!.map(_ => rhs)
  def LShift[_: P] = op("<<", Ast.operator.LShift)
  def RShift[_: P] = op(">>", Ast.operator.RShift)
  def Lt[_: P] = op("<", Ast.cmpop.Lt)
  def Gt[_: P] = op(">", Ast.cmpop.Gt)
  def Eq[_: P] = op("==", Ast.cmpop.Eq)
  def GtE[_: P] = op(">=", Ast.cmpop.GtE)
  def LtE[_: P] = op("<=", Ast.cmpop.LtE)
  def NotEq[_: P] = op("!=", Ast.cmpop.NotEq)
  def comp_op[_: P] = P( LtE|GtE|Eq|Gt|Lt|NotEq )
  def Add[_: P] = op("+", Ast.operator.Add)
  def Sub[_: P] = op("-", Ast.operator.Sub)
//  def Pow[_: P] = op("**", Ast.operator.Pow)
  def Mult[_: P] = op("*", Ast.operator.Mult)
  def Div[_: P] = op("/", Ast.operator.Div)
  def Mod[_: P] = op("%", Ast.operator.Mod)
  def BitOr[_: P] = op("|", Ast.operator.BitOr)
  def BitAnd[_: P] = op("&", Ast.operator.BitAnd)
  def BitXor[_: P] = op("^", Ast.operator.BitXor)

  def Chain[_: P](p: => P[Ast.expr], op: => P[Ast.operator]) = P( p ~ (op ~ p).rep ).map{
    case (lhs, chunks) =>
      chunks.foldLeft(lhs){case (lhs, (op, rhs)) =>
        Ast.expr.BinOp(lhs, op, rhs)
      }
  }
  def expr[_: P]: P[Ast.expr] = P( Chain(xor_expr, BitOr) )
  def xor_expr[_: P]: P[Ast.expr] = P( Chain(and_expr, BitXor) )
  def and_expr[_: P]: P[Ast.expr] = P( Chain(shift_expr, BitAnd) )
  def shift_expr[_: P]: P[Ast.expr] = P( Chain(arith_expr, LShift | RShift) )

  def arith_expr[_: P]: P[Ast.expr] = P( Chain(term, Add | Sub) )
  def term[_: P]: P[Ast.expr] = P( Chain(factor, Mult | Div | Mod) )
  def factor[_: P]: P[Ast.expr] = P(
    ("+" ~ factor) |
    ("-" ~ factor).map(Ast.expr.UnaryOp(Ast.unaryop.Minus, _)) |
    ("~" ~ factor).map(Ast.expr.UnaryOp(Ast.unaryop.Invert, _)) |
    power
  )
//  def power[_: P]: P[Ast.expr] = P( atom ~ trailer.rep ~ (Pow ~ factor).? ).map{
//    case (lhs, trailers, rhs) =>
//      def left = trailers.foldLeft(lhs)((l, t) => t(l))
//      rhs match{
//        case None => left
//        case Some((op, right)) => Ast.expr.BinOp(left, op, right)
//      }
//  }
  def power[_: P]: P[Ast.expr] = P( atom ~ trailer.rep ).map {
    case (lhs, trailers) =>
      trailers.foldLeft(lhs)((l, t) => t(l))
  }
  def atom[_: P]: P[Ast.expr] = {
    def empty_list = ("[" ~ "]").map(_ => Ast.expr.List(Nil))
//    def empty_dict = ("{" ~ "}").map(_ => Ast.expr.Dict(Nil, Nil))
    P(
      empty_list |
//      empty_dict |
      "(" ~ test ~ ")" |
      "[" ~ list ~ "]" |
//      "{" ~ dictorsetmaker ~ "}" |
      enumByName |
      byteSizeOfType |
      bitSizeOfType |
      STRING.rep(1).map(_.mkString).map(Ast.expr.Str) |
      NAME.map((x) => x.name match {
        case "true" => Ast.expr.Bool(true)
        case "false" => Ast.expr.Bool(false)
        case _ => Ast.expr.Name(x)
      }) |
      FLOAT_NUMBER.map(Ast.expr.FloatNum) |
      INT_NUMBER.map(Ast.expr.IntNum)
    )
  }
  def list_contents[_: P] = P( test.rep(1, ",") ~ ",".? )
  def list[_: P] = P( list_contents ).map(Ast.expr.List(_))

  def trailer[_: P]: P[Ast.expr => Ast.expr] = {
    def call = P("(" ~ arglist ~ ")").map{ case (args) => (lhs: Ast.expr) => Ast.expr.Call(lhs, args)}
    def slice = P("[" ~ test ~ "]").map{ case (args) => (lhs: Ast.expr) => Ast.expr.Subscript(lhs, args)}
    def cast = P( "." ~ "as" ~ "<" ~ TYPE_NAME ~ ">" ).map(
      typeName => (lhs: Ast.expr) => Ast.expr.CastToType(lhs, typeName)
    )
    def attr = P("." ~ NAME).map(id => (lhs: Ast.expr) => Ast.expr.Attribute(lhs, id))
    P( call | slice | cast | attr )
  }

  def exprlist[_: P]: P[Seq[Ast.expr]] = P( expr.rep(1, sep = ",") ~ ",".? )
  def testlist[_: P]: P[Seq[Ast.expr]] = P( test.rep(1, sep = ",") ~ ",".? )
//  def dictorsetmaker[_: P]: P[Ast.expr] = {
//    def dict_item = P( test ~ ":" ~ test )
//    def dict: P[Ast.expr.Dict] = P(
//      (dict_item.rep(1, ",") ~ ",".?).map{x =>
//        val (keys, values) = x.unzip
//        Ast.expr.Dict(keys, values)
//      }
//    )
//    P( /*dict_comp |*/ dict /*| set_comp | set*/)
//  }

  def arglist[_: P]: P[Seq[Ast.expr]] = P( (test).rep(0, ",") )

  def comp_if[_: P]: P[Ast.expr] = P( "if" ~ test )

  def testlist1[_: P]: P[Seq[Ast.expr]] = P( test.rep(1, sep = ",") )

  def enumByName[_: P]: P[Ast.expr.EnumByLabel] = P("::".!.? ~ NAME.rep(2, "::")).map {
    case (first, names: Seq[Ast.identifier]) =>
      def isAbsolute[_: P] = first.nonEmpty
      val (enumName, enumLabel) = names.takeRight(2) match {
        case Seq(a, b) => (a, b)
      }
      def typePath[_: P] = names.dropRight(2)
      if (typePath.isEmpty) {
        Ast.expr.EnumByLabel(enumName, enumLabel, Ast.EmptyTypeId)
      } else {
        Ast.expr.EnumByLabel(enumName, enumLabel, Ast.typeId(isAbsolute, typePath.map(_.name)))
      }
  }

  def byteSizeOfType[_: P]: P[Ast.expr.ByteSizeOfType] =
    P("sizeof" ~ "<" ~ TYPE_NAME ~ ">").map(typeName => Ast.expr.ByteSizeOfType(typeName))
  def bitSizeOfType[_: P]: P[Ast.expr.BitSizeOfType] =
    P("bitsizeof" ~ "<" ~ TYPE_NAME ~ ">").map(typeName => Ast.expr.BitSizeOfType(typeName))

  def topExpr[_: P]: P[Ast.expr] = P( test ~ End )

  def topExprList[_: P]: P[Seq[Ast.expr]] = P(testlist1 ~ End)

  class ParseException(val src: String, val failure: Parsed.Failure)
    extends RuntimeException(failure.msg)

  def parse(src: String): Ast.expr = realParse(src, topExpr(_))
  def parseList(src: String): Seq[Ast.expr] = realParse(src, topExprList(_))

  private def realParse[T](src: String, parser: P[_] => P[T]): T = {
    val r = fastparse.parse(src.trim, parser)
    r match {
      case Parsed.Success(value, _) => value
      case f: Parsed.Failure =>
        throw new ParseException(src, f)
    }
  }
}
