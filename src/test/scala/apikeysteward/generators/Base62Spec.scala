package apikeysteward.generators

import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Base62Spec extends AnyWordSpec with Matchers with EitherValues {

  "Base62" should {

    "have character set (base) of size 62" in {
      Base62.CharacterSet.length shouldBe 62
    }

    "have characters set starting with numbers, continuing with upper and then lower case alphabet" in {
      val alphabetUpperCase: Array[Char] = ('A' to 'Z').toArray
      val alphabetLowerCase = alphabetUpperCase.map(_.toLower)

      Base62.CharacterSet.take(10).mkString shouldBe "0123456789"
      Base62.CharacterSet.slice(10, 36) shouldBe alphabetUpperCase
      Base62.CharacterSet.drop(36) shouldBe alphabetLowerCase
    }
  }

  "Base62 on encode" should {

    "return Left containing Base62Error" when {
      "provided with negative value" in {
        val input = -1
        Base62.encode(input) shouldBe Left(ProvidedWithNegativeNumberError(input))
      }
    }

    "return Right containing Array with encoded Chars" when {

      "provided with 0 (zero)" in {
        val input = 0
        Base62.encode(input).value.mkString shouldBe "0"
      }

      "provided with 1" in {
        val input = 1

        Base62.encode(input).value.mkString shouldBe "1"
      }

      "provided with 9" in {
        val input = 9
        Base62.encode(input).value.mkString shouldBe "9"
      }

      "provided with 10" in {
        val input = 10
        Base62.encode(input).value.mkString shouldBe "A"
      }

      "provided with 61" in {
        val input = 61
        Base62.encode(input).value.mkString shouldBe "z"
      }

      "provided with 62" in {
        val input = 62
        Base62.encode(input).value.mkString shouldBe "10"
      }

      "provided with Int.MaxValue" in {
        val input = Int.MaxValue
        Base62.encode(input).value.mkString shouldBe "2LKcb1"
      }

      "provided with Int.MaxValue + 1" in {
        val input = Int.MaxValue.toLong + 1
        Base62.encode(input).value.mkString shouldBe "2LKcb2"
      }

      "provided with Long.MaxValue" in {
        val input = Long.MaxValue
        Base62.encode(input).value.mkString shouldBe "AzL8n0Y58m7"
      }
    }
  }

  "Base62 on decode" should {

    "return the value from before encoding" when {

      "provided with '0'" in {
        val input = Array('0')
        Base62.decode(input) shouldBe 0
      }

      "provided with '1'" in {
        val input = Array('1')
        Base62.decode(input) shouldBe 1
      }

      "provided with '9'" in {
        val input = Array('9')
        Base62.decode(input) shouldBe 9
      }

      "provided with 'A'" in {
        val input = Array('A')
        Base62.decode(input) shouldBe 10
      }

      "provided with '2LKcb1'" in {
        val input = "2LKcb1".toCharArray
        Base62.decode(input) shouldBe Int.MaxValue
      }

      "provided with '2LKcb2'" in {
        val input = "2LKcb2".toCharArray
        Base62.decode(input) shouldBe (Int.MaxValue.toLong + 1)
      }

      "provided with 'AzL8n0Y58m7'" in {
        val input = "AzL8n0Y58m7".toCharArray
        Base62.decode(input) shouldBe Long.MaxValue
      }

      "provided with padding '0's (zeros)" when {

        "provided with '000000'" in {
          val input = "000000".toCharArray
          Base62.decode(input) shouldBe 0
        }

        "provided with '000001'" in {
          val input = "000001".toCharArray
          Base62.decode(input) shouldBe 1
        }

        "provided with '000009'" in {
          val input = "000009".toCharArray
          Base62.decode(input) shouldBe 9
        }

        "provided with '00000A'" in {
          val input = "00000A".toCharArray
          Base62.decode(input) shouldBe 10
        }

        "provided with '0002LKcb1'" in {
          val input = "0002LKcb1".toCharArray
          Base62.decode(input) shouldBe Int.MaxValue
        }

      }
    }
  }
}
