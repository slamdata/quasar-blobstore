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

import quasar.api.datasource.DatasourceType
import quasar.blobstore.azure.AzureBlobstore
import quasar.connector.MonadResourceErr
import quasar.physical.blobstore.BlobstoreDatasource

import cats.Applicative
import eu.timepit.refined.auto._

class AzureDatasource[F[_]: Applicative: MonadResourceErr](azureBlobstore: AzureBlobstore[F])
  extends BlobstoreDatasource[F](AzureDatasource.dsType, azureBlobstore)

object AzureDatasource {
  val dsType = DatasourceType("azure", 1L)
}
