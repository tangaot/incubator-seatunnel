/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.http.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;

import java.util.Map;

public class HttpConfig {
    public static final String METHOD_DEFAULT_VALUE = "GET";
    public static final String DEFAULT_FORMAT = "json";
    public static final int DEFAULT_RETRY_BACKOFF_MULTIPLIER_MS = 100;
    public static final int DEFAULT_RETRY_BACKOFF_MAX_MS = 10000;
    public static final Option<String> URL = Options.key("url")
            .stringType()
            .noDefaultValue()
            .withDescription("Http request url");
    public static final Option<String> METHOD = Options.key("method")
            .stringType()
            .defaultValue(METHOD_DEFAULT_VALUE)
            .withDescription("Http request method");
    public static final Option<Map<String, String>> HEADERS = Options.key("headers")
            .mapType()
            .noDefaultValue()
            .withDescription("Http request headers");
    public static final Option<Map<String, String>> PARAMS = Options.key("params")
            .mapType()
            .noDefaultValue()
            .withDescription("Http request params");
    public static final Option<String> BODY = Options.key("body")
            .stringType()
            .noDefaultValue()
            .withDescription("Http request body");
    public static final Option<String> FORMAT = Options.key("format")
            .stringType()
            .defaultValue(DEFAULT_FORMAT)
            .withDescription("Http response format");
    public static final Option<Integer> POLL_INTERVAL_MILLS = Options.key("poll_interval_millis")
            .intType()
            .noDefaultValue()
            .withDescription("Request http api interval(millis) in stream mode");
    public static final Option<Integer> RETRY = Options.key("retry")
            .intType()
            .noDefaultValue()
            .withDescription("The max retry times if request http return to IOException");
    public static final Option<Integer> RETRY_BACKOFF_MULTIPLIER_MS = Options.key("retry_backoff_multiplier_ms")
            .intType()
            .defaultValue(DEFAULT_RETRY_BACKOFF_MULTIPLIER_MS)
            .withDescription("The retry-backoff times(millis) multiplier if request http failed");
    public static final Option<Integer> RETRY_BACKOFF_MAX_MS = Options.key("retry_backoff_max_ms")
            .intType()
            .defaultValue(DEFAULT_RETRY_BACKOFF_MAX_MS)
            .withDescription("The maximum retry-backoff times(millis) if request http failed");
}
