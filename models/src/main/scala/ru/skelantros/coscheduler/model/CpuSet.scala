package ru.skelantros.coscheduler.model

import io.circe.{Decoder, Encoder}
import sttp.tapir
import sttp.tapir.CodecFormat.TextPlain

case class CpuSet(from: Int, count: Int) {
    def asString: String = s"$from-${from + count - 1}"
}

object CpuSet {
    private val regex = """(\d+)-(\d+)""".r
    def apply(str: String): Option[CpuSet] = str match {
        case regex(start, end) =>
            (start.toIntOption zip end.toIntOption).flatMap { case (start, end) =>
                val count = end - start + 1
                if(count > 0 && start > 0) Some(CpuSet(start, count))
                else None
            }
        case _ => None
    }

    implicit val encoder: Encoder[CpuSet] =
        Encoder.encodeString.contramap(_.asString)

    implicit val decoder: Decoder[CpuSet] =
        Decoder.decodeString.emap(CpuSet(_).toRight("wrong value"))

    implicit val optQueryCodec: tapir.Codec[List[String], Option[CpuSet], TextPlain] =
        tapir.Codec.list[String, String, TextPlain].map(_.headOption.flatMap(CpuSet(_)))(_.map(_.asString).toList)
}
