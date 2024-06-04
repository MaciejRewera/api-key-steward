package apikeysteward.generators

import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.generators.ChecksumCodec.ChecksumDecodingError.ProvidedEncodedChecksumTooLongError
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ChecksumCodecSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val checksumCodec = new ChecksumCodec

  "ChecksumCodec" should {

    "have EncodedChecksumLength equal to 6 (six)" in {
      ChecksumCodec.EncodedChecksumLength shouldBe 6
    }
  }

  "ChecksumCodec on encode" should {

    "return Left containing ChecksumCodecError" when {
      "provided with negative value" in {
        val input = -1
        checksumCodec.encode(input) shouldBe Left(ProvidedWithNegativeNumberError(input))
      }
    }

    "return Right containing encoded String, padded with '0's (zeros)" when {

      "provided with 0 (zero)" in {
        val input = 0
        checksumCodec.encode(input) shouldBe Right("000000")
      }

      "provided with 1" in {
        val input = 1
        checksumCodec.encode(input) shouldBe Right("000001")
      }

      "provided with 10" in {
        val input = 10
        checksumCodec.encode(input) shouldBe Right("00000A")
      }

      "provided with 62" in {
        val input = 62
        checksumCodec.encode(input) shouldBe Right("000010")
      }

      "provided with Int.MaxValue" in {
        val input = Int.MaxValue
        checksumCodec.encode(input) shouldBe Right("2LKcb1")
      }
    }
  }

  "ChecksumCodec on decode" should {

    "return Left containing error" when {

      "provided with a String longer than 6 characters" in {
        val input = "qwertyu"
        checksumCodec.decode(input) shouldBe Left(ProvidedEncodedChecksumTooLongError(input))
      }

      "provided with a String longer than 6 characters, containing padding '0's (zeros)" in {
        val input = "0000001"
        checksumCodec.decode(input) shouldBe Left(ProvidedEncodedChecksumTooLongError(input))
      }

      "provided with a String longer than 6 characters, containing only '0's (zeros)" in {
        val input = "0000000"
        checksumCodec.decode(input) shouldBe Left(ProvidedEncodedChecksumTooLongError(input))
      }
    }

    "return Right containing the decoded value" when {

      "provided with '000000'" in {
        val input = "000000"
        checksumCodec.decode(input) shouldBe Right(0)
      }

      "provided with '000001'" in {
        val input = "000001"
        checksumCodec.decode(input) shouldBe Right(1)
      }

      "provided with '00000A'" in {
        val input = "00000A"
        checksumCodec.decode(input) shouldBe Right(10)
      }

      "provided with '000010'" in {
        val input = "000010"
        checksumCodec.decode(input) shouldBe Right(62)
      }

      "provided with '2LKcb1'" in {
        val input = "2LKcb1"
        checksumCodec.decode(input) shouldBe Right(Int.MaxValue)
      }

      "provided with String without padding '0's (zeros)" when {

        "provided with '0'" in {
          val input = "0"
          checksumCodec.decode(input) shouldBe Right(0)
        }

        "provided with '1'" in {
          val input = "1"
          checksumCodec.decode(input) shouldBe Right(1)
        }

        "provided with 'A'" in {
          val input = "A"
          checksumCodec.decode(input) shouldBe Right(10)
        }

        "provided with '10'" in {
          val input = "10"
          checksumCodec.decode(input) shouldBe Right(62)
        }
      }
    }
  }

}
