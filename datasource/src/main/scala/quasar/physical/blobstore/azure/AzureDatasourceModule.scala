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

package quasar.physical.blobstore.azure

import slamdata.Predef._
import quasar.Disposable
import quasar.api.datasource.DatasourceError.InitializationError
import quasar.api.datasource.{DatasourceError, DatasourceType}
import quasar.api.resource.ResourcePath
import quasar.blobstore.azure._
import json._
import quasar.connector.{Datasource, LightweightDatasourceModule, MonadResourceErr, QueryResult}

import scala.concurrent.ExecutionContext

import argonaut.Json
import cats.Applicative
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.syntax.applicative._
import cats.syntax.functor._
import fs2.Stream
import scalaz.{NonEmptyList, \/}
import scalaz.syntax.either._

object AzureDatasourceModule extends LightweightDatasourceModule {

  private val redactedCreds =
    AzureCredentials(
      AccountName("<REDACTED>"),
      AccountKey("<REDACTED>"))

  override def kind: DatasourceType = AzureDatasource.dsType

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  override def lightweightDatasource[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer](
      json: Json)(
      implicit ec: ExecutionContext)
      : F[InitializationError[Json] \/ Disposable[F, Datasource[F, Stream[F, ?], ResourcePath, QueryResult[F]]]] =
    json.as[AzureConfig].result match {
      case Right(cfg) =>
        AzureDatasource.mk(cfg).map(d => Disposable(d.asDsType, Applicative[F].unit).right)
      case Left((msg, _)) =>
        DatasourceError
          .invalidConfiguration[Json, InitializationError[Json]](kind, json, NonEmptyList(msg))
          .left.pure[F]

    }

  override def sanitizeConfig(config: Json): Json =
    config.as[AzureConfig].result.toOption.map(cfg =>
      cfg.copy(credentials = cfg.credentials.map(_ => redactedCreds))
    ).map(json.codecConfig.encode).getOrElse(Json.jEmptyObject)
}
