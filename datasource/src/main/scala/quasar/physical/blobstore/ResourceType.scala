/*
 * Copyright 2014–2018 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.physical.blobstore

import slamdata.Predef.{Product, Serializable, String}

import argonaut._, Argonaut._
import scalaz.{Equal, Show}
import scalaz.syntax.show._

sealed trait ResourceType extends Product with Serializable

object ResourceType {
  case object Json extends ResourceType
  case object LdJson extends ResourceType

  implicit val decodeJson: DecodeJson[ResourceType] =
    DecodeJson(c => c.as[String] flatMap {
      case "json" => DecodeResult.ok(Json)
      case "ldjson" => DecodeResult.ok(LdJson)
      case other => DecodeResult.fail("Unsupported type: " + other, c.history)
    })

  implicit val encodeJson: EncodeJson[ResourceType] =
    jencode1(_.shows.toLowerCase)

  implicit val equal: Equal[ResourceType] =
    Equal.equalA

  implicit val show: Show[ResourceType] =
    Show.showFromToString
}