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
package common
package config

import utils.ScalazJson4sUtils
import enrichments.{
  AnonIpEnrichment,
  IpToGeoEnrichment
}

// Scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s.scalaz.JsonScalaz._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// Iglu
import iglu.client._
import iglu.client.validation.ValidatableJsonMethods._
import iglu.client.validation.ProcessingMessageMethods._
import iglu.client.validation.ValidatableJsonNode

/**
 * Companion which holds a constructor
 * for the EnrichmentConfigRegistry.
 */
object EnrichmentConfigRegistry {

  private val EnrichmentConfigSchemaKey = SchemaKey("com.snowplowanalytics.snowplow", "enrichments", "jsonschema", "1-0-0")

  /**
   * Constructs our EnrichmentConfigRegistry
   * from the supplied JSON JValue.
   *
   * TODO: rest of docstring 
   * @todo remove all the JsonNode round-tripping when
   *       we have ValidatableJValue
   */
  def parse(node: JValue, localMode: Boolean)(implicit resolver: Resolver): ValidatedNelMessage[EnrichmentConfigRegistry] =  {

    // Check schema, validate against schema, convert to List[JValue]
    val enrichments: ValidatedNelMessage[List[JValue]] = for {
        d <- asJsonNode(node).verifySchemaAndValidate(EnrichmentConfigSchemaKey, true)
      } yield (for {
        JArray(arr) <- fromJsonNode(d)
      } yield arr).flatten

    // Check each enrichment validates against its own schema
    val configs: ValidatedNelMessage[Map[String, EnrichmentConfig]] = (for {
        jsons <- enrichments
      } yield for {    
        json  <- jsons
      } yield for {
        pair  <- asJsonNode(json).validateAndIdentifySchema(dataOnly = true)
        conf  <- buildEnrichmentConfig(pair._1, fromJsonNode(pair._2), localMode)
      } yield conf)
      .flatMap(_.sequenceU) // Swap nested List[scalaz.Validation[...]
      .map(_.flatten.toMap) // Eliminate our Option boxing (drop Nones)

    // Build an EnrichmentConfigRegistry from the Map
    configs.bimap(
      e => NonEmptyList(e.toString.toProcessingMessage),
      s => EnrichmentConfigRegistry(s))
  }

  /**
   * Builds an EnrichmentConfig from a JValue if it has a 
   * recognized name field and matches a schema key 
   *
   * @param enrichmentConfig JValue with enrichment information
   * @param schemaKey SchemaKey for the JValue
   * @return ValidatedNelMessage boxing Option boxing Tuple2 containing
   *         the EnrichmentConfig object and the schemaKey
   */
  private def buildEnrichmentConfig(schemaKey: SchemaKey, enrichmentConfig: JValue, localMode: Boolean): ValidatedNelMessage[Option[Tuple2[String, EnrichmentConfig]]] = {

    val name: ValidatedNelMessage[String] = ScalazJson4sUtils.extractString(enrichmentConfig, NonEmptyList("name")).toValidationNel
    name.flatMap( nm => {

      if (nm == "ip_to_geo") {
        IpToGeoEnrichment.parse(enrichmentConfig, schemaKey, localMode).map((nm, _).some)
      } else if (nm == "anon_ip") {
        AnonIpEnrichment.parse(enrichmentConfig, schemaKey).map((nm, _).some)
      } else {
        None.success
      }
    })
  }

}

/**
 * A registry to hold all of our enrichment
 * configurations.
 *
 * In the future this may evolve to holding
 * all of our enrichments themselves.
 */
case class EnrichmentConfigRegistry(private val configs: Map[String, EnrichmentConfig]) {

  /**
   * Returns an Option boxing the AnonIpEnrichment
   * config value if present, or None if not
   *
   * @return Option boxing the AnonIpEnrichment instance
   */
  def getAnonIpEnrichment: Option[AnonIpEnrichment] =
    getEnrichment[AnonIpEnrichment]("anon_ip")

  /**
   * Returns an Option boxing the IpToGeoEnrichment
   * config value if present, or None if not
   *
   * @return Option boxing the IpToGeoEnrichment instance
   */
  def getIpToGeoEnrichment: Option[IpToGeoEnrichment] = 
    getEnrichment[IpToGeoEnrichment]("ip_to_geo")

  /**
   * Returns an Option boxing an Enrichment
   * config value if present, or None if not
   *
   * @tparam A Expected type of the enrichment to get
   * @param name The name of the enrichment to get
   * @return Option boxing the enrichment
   */
  private def getEnrichment[A <: EnrichmentConfig : Manifest](name: String): Option[A] =
    configs.get(name).map(cast[A](_))

  // Adapted from http://stackoverflow.com/questions/6686992/scala-asinstanceof-with-parameterized-types
  private def cast[A <: AnyRef : Manifest](a : Any) : A 
    = manifest.runtimeClass.cast(a).asInstanceOf[A]
}
