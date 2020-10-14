package io.kaitai.struct.exprlang
import fastparse.NoWhitespace._
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
object Lexical {
  import fastparse._
  def kw[_: P](s: String) = s ~ !namePart

  def wscomment[_: P]  = P( (CharsWhileIn(" \n") | "\\\n").rep )

  def nameStart[_: P]  = P( letter | "_" )
  def namePart[_: P]   = P( letter | digit | "_" )
  def identifier[_: P]: P[Ast.identifier] =
    P( nameStart ~ namePart.rep ).!.map(Ast.identifier)
  def letter[_: P]      = P( lowercase | uppercase )
  def lowercase[_: P]   = P( CharIn("a-z") )
  def uppercase[_: P]   = P( CharIn("A-Z") )
  def digit[_: P]       = P( CharIn("0-9") )

  def stringliteral[_: P]: P[String] = P( singlestring | doublestring )
  def singlestring[_: P]  = P("'" ~/ singlestringchar.rep.! ~ "'")
  def singlestringchar[_: P]  = P( CharsWhile(!"'".contains(_)) )

  def doublestring[_: P]: P[String] = P("\"" ~/ doublestringitem.rep ~ "\"").map(_.mkString)
  def doublestringitem[_: P]  = P( doublestringchar.! | escapeseq )
  def doublestringchar[_: P]  = P( CharsWhile(!"\\\"".contains(_)) )
  def escapeseq[_: P]  = P( "\\" ~/ (quotedchar | quotedoctal | quotedhex) )

  val QUOTED_CC  = Map(
    "\\" -> "\\",  // backslash
    "'" -> "'",   // single quote
    "\"" -> "\"", // double quote
    "a" -> "\7",  // bell, ASCII code 7
    "b" -> "\b",  // backspace, ASCII code 8
    "t" -> "\t",  // horizontal tab, ASCII code 9
    "n" -> "\n",  // newline, ASCII code 10
    "v" -> "\13", // vertical tab, ASCII code 11 = 0o13
    "f" -> "\14", // form feed, ASCII code 12 = 0o14
    "r" -> "\r",  // carriage return, ASCII code 13
    "e" -> "\33" // escape, ASCII code 27 = 0o33
  )
  val VALID_QUOTED  = QUOTED_CC.keys.toList.sorted.mkString
  def quotedchar[_: P]  = P( CharIn("\\\'\"abtnvfre").! ).map(QUOTED_CC)
  def quotedoctal[_: P]: P[String] = P( octdigit.rep(1).! ).map { (digits) =>
    val code = Integer.parseInt(digits, 8).toChar
    Character.toString(code)
  }
  def quotedhex[_: P]: P[String] = P( "u" ~/ hexdigit.rep(exactly = 4).! ).map { (digits) =>
    val code = Integer.parseInt(digits, 16).toChar
    Character.toString(code)
  }
  // FIXME: fix this mess with octdigit / hexdigit allowing underscore
  // probably underscore shouldn't be inside them, but somehow added separately
  // plus there's a problem with "0x_" and "0o_" being legal now

  def integer[_: P]: P[BigInt] = P( octinteger | hexinteger | bininteger | decimalinteger)
  def decimalinteger[_: P]: P[BigInt] = P( nonzerodigit ~ (digit | "_").rep | "0" ).!.map(parseNum(_, 10))
  def octinteger[_: P]: P[BigInt] = P( "0" ~ ("o" | "O") ~ octdigit.rep(1).! ).map(parseNum(_, 8))
  def hexinteger[_: P]: P[BigInt] = P( "0" ~ ("x" | "X") ~ hexdigit.rep(1).! ).map(parseNum(_, 16))
  def bininteger[_: P]: P[BigInt] = P( "0" ~ ("b" | "B") ~ bindigit.rep(1).! ).map(parseNum(_, 2))
  def nonzerodigit[_: P]: P[Unit] = P( CharIn("1-9") )
  def octdigit[_: P]: P[Unit] = P( CharIn("0-7") | "_" )
  def bindigit[_: P]: P[Unit] = P( "0" | "1" | "_" )
  def hexdigit[_: P]: P[Unit] = P( digit | CharIn("a-f", "A-F") | "_" )

  def floatnumber[_: P]: P[BigDecimal] = P(
      digit.rep(1) ~ exponent // Ex.: 4E2, 4E+2, 4e-2
    | fixed ~ exponent.?      // Ex.: 4.E2, .4e+2, 4.2e-0
  ).!.map(BigDecimal(_))
  def fixed[_: P]  = P(
      digit.rep ~ "." ~ digit.rep(1)                // Ex.: 4.2, .42
    | digit.rep(1) ~ "." ~ !(wscomment ~ nameStart) // Ex.: 42., but not '42.abc' or '42.  def'
  )
  def exponent[_: P]: P[Unit] = P( ("e" | "E") ~ ("+" | "-").? ~ digit.rep(1) )

  /**
    * Converts number literal from string form into BigInt, ignoring underscores that might be
    * inside (usually for the sake of digit ranges separation).
    * @param literal number literal as a string (i.e. "1234", "12_34" or "caf_e")
    * @param base conversion base (10 = decimal, 8 = octal, 16 = hex, 2 = binary, etc)
    * @return number literal as BigInt
    */
  def parseNum(literal: String, base: Int): BigInt = {
    val cleanLiteral = literal.replace("_", "")
    scala.BigInt(cleanLiteral, base)
  }
}
