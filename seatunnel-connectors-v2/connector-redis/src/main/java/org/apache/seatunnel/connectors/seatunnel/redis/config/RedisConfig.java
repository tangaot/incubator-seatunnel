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

package org.apache.seatunnel.connectors.seatunnel.redis.config;

public class RedisConfig {
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String AUTH = "auth";
    public static final String USER = "user";
    public static final String KEY_PATTERN = "keys";
    public static final String KEY = "key";
    public static final String DATA_TYPE = "data_type";
    public static final String FORMAT = "format";
    public static final String MODE = "mode";
    public static final String NODES = "nodes";
    public static final String HASH_KEY_PARSE_MODE = "hash_key_parse_mode";

    public enum RedisMode {
        SINGLE,
        CLUSTER;
    }

    public enum HashKeyParseMode {
        ALL,
        KV;
    }
}
