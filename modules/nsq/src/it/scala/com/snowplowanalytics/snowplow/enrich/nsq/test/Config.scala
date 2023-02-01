/*
 * Copyright (c) 2023 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.enrich.nsq.test

import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

object Config {
  def base64EnrichConfig(
    lookupHost: String,
    lookupPort: Int,
    nsqdHost: String,
    nsqdPort: Int,
    inputTopic: String,
    goodOutputTopic: String,
    badOutputTopic: String
  ):String = {
    new String(
      Base64.getEncoder.encode(
        s"""
           |{
           |  "input": {
           |    "topic": $inputTopic
           |    "channel": "EnrichNsqChannel"
           |    "lookupHost": $lookupHost
           |    "lookupPort": $lookupPort
           |  }
           |
           |  "output": {
           |    "good": {
           |      "topic": $goodOutputTopic
           |      "nsqdHost": $nsqdHost
           |      "nsqdPort": $nsqdPort
           |    }
           |
           |    "bad": {
           |      "topic": $badOutputTopic
           |      "nsqdHost": $nsqdHost
           |      "nsqdPort": $nsqdPort
           |    }
           |  }
           |
           |  "monitoring": {
           |
           |    "sentry": {
           |      "dsn": ""
           |    }
           |
           |    # Optional, configure how metrics are reported
           |    "metrics": {
           |
           |      # Optional. Log to stdout using Slf4j
           |      "stdout": {
           |        "period": "10 seconds"
           |
           |        # Optional, override the default metric prefix
           |        # "prefix": "snowplow.enrich."
           |      }
           |
           |      # Optional. Send KCL and KPL metrics to Cloudwatch
           |      "cloudwatch": false
           |    }
           |  }
           |
           |  "telemetry": {
           |    "disable": true
           |  }
           |
           |  "featureFlags": {
           |    "acceptInvalid": true
           |  }
           |}
           |""".stripMargin.getBytes(UTF_8)
      )
    )
  }
}
