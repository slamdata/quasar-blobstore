# quasar-datasource-azure [![Build Status](https://travis-ci.org/slamdata/quasar-datasource-azure.svg?branch=master)](https://travis-ci.org/slamdata/quasar-datasource-azure) [![Bintray](https://img.shields.io/bintray/v/slamdata-inc/maven-public/quasar-datasource-azure.svg)](https://bintray.com/slamdata-inc/maven-public/quasar-datasource-azure) [![Discord](https://img.shields.io/discord/373302030460125185.svg?logo=discord)](https://discord.gg/QNjwCg6)

## Usage

```sbt
libraryDependencies += "com.slamdata" %% "quasar-datasource-azure" % <version>
```

## Configuration

Configuration for the Azure datasource has the following JSON format

```json
{
  "container": String,
  "storageUrl": String,
  "resourceType": "json" | "ldjson",
  ["credentials": Object,]
  ["maxQueueSize": Number]
}
```

* `container` the name of the Azure blobstore container to use.
* `storageUrl` the Azure storage URL to use. Typically this will be an URL of the form `https://<accountName>.blob.core.windows.net/`.
* `resourceType` the format of the resources that are assumed to be in the container. Currently array-wrapped (`"json"`) and line-delimited (`"ldjson"`) are supported.
* `credentials` (optional, default = empty) Azure credentials to use for access. Object has the following format: `{ "accountName": String, "accountKey": String }`.
* `maxQueueSize` (optional, default = 10) maximum amount of `ByteBuffer`s that can be kept in a queue when downloading a resource. 
  When the queue is full, downloading will halt. Downloading will continue again when a `ByteBuffer` is dequeued.
  Usually this value does not need to be overridden, but it can be increased in case downloading halts too often, or decreased to reduce memory use.