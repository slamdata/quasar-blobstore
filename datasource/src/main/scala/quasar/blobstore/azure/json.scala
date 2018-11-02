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

package quasar.blobstore.azure

import argonaut._, Argonaut._

object json {
  implicit val decodeContainerName: DecodeJson[ContainerName] = jdecode1(ContainerName(_))
  implicit val decodeStorageUrl: DecodeJson[StorageUrl] = jdecode1(StorageUrl(_))
  implicit val decodeAccountName: DecodeJson[AccountName] = jdecode1(AccountName(_))
  implicit val decodeAccountKey: DecodeJson[AccountKey] = jdecode1(AccountKey(_))

  implicit val encodeContainerName: EncodeJson[ContainerName] = jencode1(_.value)
  implicit val encodeStorageUrl: EncodeJson[StorageUrl] = jencode1(_.value)
  implicit val encodeAccountName: EncodeJson[AccountName] = jencode1(_.value)
  implicit val encodeAccountKey: EncodeJson[AccountKey] = jencode1(_.value)

  implicit val codecCedentials: CodecJson[AzureCredentials] =
    casecodec2(AzureCredentials.apply, AzureCredentials.unapply)("accountName", "accountKey")

  implicit val codecConfig: CodecJson[AzureConfig] =
    casecodec3(AzureConfig.apply, AzureConfig.unapply)("container", "credentials", "storageUrl")
}