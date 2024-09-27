package apikeysteward.services

import cats.effect.IO

import java.util.UUID

class UuidGenerator {
  def generateUuid: IO[UUID] = IO(UUID.randomUUID())
}
