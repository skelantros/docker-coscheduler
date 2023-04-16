package ru.skelantros.coscheduler.model

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import sttp.model.Uri
import sttp.tapir.Schema

case class Node(id: String, uri: Uri)

object Node {
    implicit val codec: Codec[Node] = deriveCodec

    implicit val uriEncoder: Encoder[Uri] =
        Encoder.encodeString.contramap(_.toString)

    implicit val uriDecoder: Decoder[Uri] =
        Decoder.decodeString.emap(Uri.parse)
}