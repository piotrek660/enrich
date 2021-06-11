/*
 * Copyright (c) 2020-2022 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.pubsub

import cats.effect.{ExitCode, IO, IOApp, Resource, SyncIO}

import java.util.concurrent.{Executors, TimeUnit}

import scala.concurrent.ExecutionContext

import com.permutive.pubsub.consumer.ConsumerRecord

import fs2.Pipe

import com.snowplowanalytics.snowplow.enrich.common.fs2.Run

import com.snowplowanalytics.snowplow.enrich.pubsub.generated.BuildInfo

object Main extends IOApp.WithContext {

  /**
   * The maximum size of a serialized payload that can be written to pubsub.
   *
   *  Equal to 6.9 MB. The message will be base64 encoded by the underlying library, which brings the
   *  encoded message size to near 10 MB, which is the maximum allowed for PubSub.
   */
  private val MaxRecordSize = 6900000

  /**
   * An execution context matching the cats effect IOApp default. We create it explicitly so we can
   * also use it for our Blaze client.
   */
  override protected val executionContextResource: Resource[SyncIO, ExecutionContext] = {
    val poolSize = math.max(2, Runtime.getRuntime().availableProcessors())
    Resource
      .make(SyncIO(Executors.newFixedThreadPool(poolSize)))(pool =>
        SyncIO {
          pool.shutdown()
          pool.awaitTermination(10, TimeUnit.SECONDS)
          ()
        }
      )
      .map(ExecutionContext.fromExecutorService)
  }

  def run(args: List[String]): IO[ExitCode] =
    Run.run[IO, ConsumerRecord[IO, Array[Byte]]](
      args,
      BuildInfo.name,
      BuildInfo.version,
      BuildInfo.description,
      executionContext,
      Source.init,
      (_, auth, out) => Sink.initAttributed(auth, out),
      (_, auth, out) => Sink.initAttributed(auth, out),
      (_, auth, out) => Sink.init(auth, out),
      List(GcsClient.mk[IO]),
      checkpointer,
      _.value,
      false,
      MaxRecordSize
    )

  private def checkpointer[F[_]]: Pipe[F, ConsumerRecord[F, Array[Byte]], Unit] =
    _.evalMap(_.ack)

}
