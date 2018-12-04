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

package quasar.blobstore

import cats.Monad
import cats.syntax.flatMap._

object ops {

  def service[F[_]: Monad, A, Req, Res, B, C]
      (reqEncoder: A => F[Req], request: Req => F[Res], resDecoder: Res => F[B], handler: F[B] => C)
      : A => C = { a: A =>
    handler(reqEncoder(a) >>= request >>= resDecoder)
  }

}
