package izumi.fundamentals.platform.time

import izumi.fundamentals.platform.IzPlatformSyntax

import java.time.*
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import scala.language.implicitConversions

trait IzTimeConstants extends IzTimeConstantsSafe {
  final val TZ_UTC: ZoneId = ZoneId.of("UTC")

  final val EPOCH_OFFSET = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0), TZ_UTC)

  final val EPOCH = ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), TZ_UTC)

  final lazy val ISO_ZONED_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .append(ISO_OFFSET_DATE_TIME_3NANO)
      .optionalStart
      .appendLiteral('[')
      .parseCaseSensitive
      .appendZoneRegionId
      .appendLiteral(']')
      .toFormatter
  }

  final lazy val ISO_OFFSET_DATE_TIME_3NANO: DateTimeFormatter = {
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(ISO_LOCAL_DATE_TIME_3NANO)
      .parseLenient
      .appendOffsetId
      .parseStrict
      .toFormatter
  }
}

trait IzTime extends IzTimeSafe with IzTimeConstants with IzPlatformSyntax {

  // extended operators
  @inline implicit final def toRichZonedDateTime(timestamp: ZonedDateTime): IzZonedDateTime = new IzZonedDateTime(timestamp)

  // parsers
  @inline implicit final def toRichLong(value: Long): IzLongParsers = new IzLongParsers(value)
  @inline implicit final def stringToParseableTime(value: String): IzTimeParsers = new IzTimeParsers(value)

}

object IzTime extends IzTime with IzTimeOrdering with IzTimeConstants
