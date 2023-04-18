package ru.skelantros.coscheduler.model

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import sttp.model.Uri

case class Node(id: String, uri: Uri, cores: Int) {
    val port: Option[Int] = uri.port
    val host: Option[String] = uri.host
}

object Node {
    implicit val codec: Codec[Node] = deriveCodec

    implicit val uriEncoder: Encoder[Uri] =
        Encoder.encodeString.contramap(_.toString)

    implicit val uriDecoder: Decoder[Uri] =
        Decoder.decodeString.emap(Uri.parse)
}