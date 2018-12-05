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

import cats.Applicative
import cats.syntax.applicative._

trait Converter[F[_], A, B] {
  def convert(a: A): F[B]
}

object Converter {
  def apply[F[_], A, B](f: A => F[B]): Converter[F, A, B] =
    new Converter[F, A, B] {
      override def convert(a: A): F[B] = f(a)
    }

  def pure[F[_]: Applicative, A, B](f: A => B): Converter[F, A, B] =
    Converter[F, A, B](f(_).pure[F])
}