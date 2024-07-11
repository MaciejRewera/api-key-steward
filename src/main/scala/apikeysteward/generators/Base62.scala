package apikeysteward.generators

import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.model.CustomError
import cats.implicits.catsSyntaxEitherId

import scala.annotation.tailrec
import scala.math.pow

object Base62 {

  val CharacterSet: IndexedSeq[Char] = ('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')
  private val base: Int = CharacterSet.length

  def encode(num: Long): Either[Base62Error, Array[Char]] =
    if (num < 0) ProvidedWithNegativeNumberError(num).asLeft
    else encodePos(num).asRight

  private def encodePos(num: Long): Array[Char] = {
    @tailrec
    def loop(n: Long, acc: Array[Char] = Array.empty): Array[Char] =
      if (n > 0) {
        val idx = (n % base).toInt
        val newAcc = CharacterSet(idx) +: acc
        loop(n / base, newAcc)
      } else
        acc

    if (num == 0)
      Array[Char](CharacterSet(0))
    else
      loop(num)
  }

  def decode(chars: Array[Char]): Long =
    chars.reverseIterator.zipWithIndex.map { case (char, idx) =>
      val positionInBase62 = CharacterSet.indexOf(char)

      positionInBase62 * pow(base, idx).toLong
    }.sum

  sealed abstract class Base62Error(override val message: String) extends CustomError
  object Base62Error {

    case class ProvidedWithNegativeNumberError(number: Long)
        extends Base62Error(message = s"Base62 encoder can only encode non-negative numbers, but received: $number")
  }

}
