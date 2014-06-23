/*
 * Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics
package snowplow
package enrich
package hadoop

// Java
import java.net.URI
import java.util.NoSuchElementException

// Jackson
import com.fasterxml.jackson.databind.JsonNode

// Scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s.jackson.JsonMethods._

// Scalding
import com.twitter.scalding.Args

// Iglu
import iglu.client._
import iglu.client.validation.ProcessingMessageMethods._

// Snowplow Common Enrich
import common._
import common.utils.{
  ConversionUtils,
  JacksonJsonUtils
}

import common.enrichments.EventEnrichments
import common.config.EnrichmentConfigRegistry

// This project
import utils.ScalazArgs

/**
 * The configuration for the SnowPlowEtlJob.
 */
case class EtlJobConfig(
  inFolder: String,
  inFormat: String,
  maxmindFile: URI,
  outFolder: String,
  badFolder: String,
  etlTstamp: String,
  registry: EnrichmentConfigRegistry,
  exceptionsFolder: Option[String]
  )

/**
 * Module to handle configuration for
 * the SnowPlowEtlJob
 */
object EtlJobConfig {
  // TODO comment
  //private val EnrichmentsSchema = SchemaKey("com.snowplowanalytics.xx", "xx", "jsonschema", "1-0-0")

  /**
   * Loads the Config from the Scalding
   * job's supplied Args.
   *
   * @param args The arguments to parse
   * @return the EtLJobConfig, or one or
   *         more error messages, boxed
   *         in a Scalaz Validation Nel
   */
  def loadConfigFrom(args: Args, localMode: Boolean): ValidatedNelMessage[EtlJobConfig] = {

    import ScalazArgs._

    val inFolder  = args.requiredz("input_folder")
    val inFormat = args.requiredz("input_format") // TODO: check it's a valid format
    val maxmindFile = args.requiredz("maxmind_file").flatMap(f => getMaxmindUri(f))
    val outFolder = args.requiredz("output_folder")
    val badFolder = args.requiredz("bad_rows_folder")
    val etlTstamp = args.requiredz("etl_tstamp").flatMap(t => EventEnrichments.extractTimestamp("etl_tstamp", t).leftMap(_.toProcessingMessage))
    val exceptionsFolder = args.optionalz("exceptions_folder")
    
    val igluResolver: ValidatedNelMessage[Resolver] = args.requiredz("iglu_config") match {
      case Failure(e) => e.toString.toProcessingMessage.failNel[Resolver]
      case Success(s) => for {
        node <- base64ToJsonNode(s)
        reso <- Resolver.parse(node)
      } yield reso
    }

    val enrichments: ValidatedNelMessage[JsonNode] = for {
      str  <- (args.requiredz("enrichments").toValidationNel: ValidatedNelMessage[String])
      node <-  base64ToJsonNode(str)
      } yield node

    val registry: ValidatedNelMessage[EnrichmentConfigRegistry] = (enrichments |@| igluResolver) {
      buildEnrichmentRegistry(_, localMode)(_)
    }.flatMap(s => s)

    (inFolder.toValidationNel |@| inFormat.toValidationNel |@| maxmindFile.toValidationNel |@| outFolder.toValidationNel |@| badFolder.toValidationNel |@| etlTstamp.toValidationNel |@| registry |@| exceptionsFolder.toValidationNel) { EtlJobConfig(_,_,_,_,_,_,_,_) }
  }

  /**
   * TODO: desc
   */
  private def buildEnrichmentRegistry(enrichments:JsonNode, localMode: Boolean)(implicit resolver: Resolver): ValidatedNelMessage[EnrichmentConfigRegistry] = {
    EnrichmentConfigRegistry.parse(fromJsonNode(enrichments), localMode)
  }

  /**
   * Takes an incoming JsonNode and:
   * 1. Validates it against its own
   *    schema
   * 2. Confirms that that schema is
   *    a enrichments JSON Schema
   * 3. Breaks it into an array of
   *    child JsonNodes
   * 4. Validates each of those
   *    JsonNodes against their own
   *    internal schema
   * 5. Adds new entries to List[JsonNode]
   *    with enabled: false for any
   *    enrichments we support that are not
   *    found
   *
  // TODO: move this to Scala Common Enrich
  def validateEnrichments(node: JsonNode): ValidationNel[String, List[JsonNode]] = {

    // Check it passes validation
    config.validateAndIdentifySchema(dataOnly = true) match {
      case Success((key, node)) if key == EnrichmentsSchema => {

        val json = fromJsonNode(node) // => :JValue
        val enrichmentConfigs: ValidatedNel[String, JsonNode] = (field[List[JValue]]("repositories")(json)).fold(
          f => f.map(_.toString.toProcessingMessage).fail,
          s => getEnrichmentConfigs(s)
        )
        (cacheSize |@| repositoryRefs) {
          Resolver(_, _)
        }
      }
      case Success((key, node)) if key != EnrichmentsSchema =>
        s"Expected a ${ConfigurationSchema} as resolver configuration, got: ${key}".fail.toProcessingMessageNel
      case Failure(err) =>
        (err.<::("Resolver configuration failed JSON Schema validation".toProcessingMessage)).fail[Resolver]
    }
  } */

  /**
   * Converts a base64-encoded JSON
   * String into a JsonNode.
   *
   * @param str base64-encoded JSON
   * @return a JsonNode on Success,
   * a NonEmptyList of
   * ProcessingMessages on
   * Failure
   */
  private def base64ToJsonNode(str: String): ValidatedNelMessage[JsonNode] =
    (for {
      raw <-  ConversionUtils.decodeBase64Url("enrichments", str)
      node <- JacksonJsonUtils.extractJson("enrichments", raw)
    } yield node).leftMap(_.toProcessingMessage).toValidationNel
}
