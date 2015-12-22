package com.pjanof.shapes

import spray.json._

case class Call(to: String, from: String)

trait CallProtocol extends DefaultJsonProtocol {
  implicit val callFormat = jsonFormat2(Call.apply)
}
