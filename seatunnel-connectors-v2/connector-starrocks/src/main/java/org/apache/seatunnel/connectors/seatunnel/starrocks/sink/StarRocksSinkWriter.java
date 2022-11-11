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

package org.apache.seatunnel.connectors.seatunnel.starrocks.sink;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;
import org.apache.seatunnel.connectors.seatunnel.starrocks.client.StarRocksSinkManager;
import org.apache.seatunnel.connectors.seatunnel.starrocks.config.SinkConfig;
import org.apache.seatunnel.connectors.seatunnel.starrocks.serialize.StarRocksCsvSerializer;
import org.apache.seatunnel.connectors.seatunnel.starrocks.serialize.StarRocksISerializer;
import org.apache.seatunnel.connectors.seatunnel.starrocks.serialize.StarRocksJsonSerializer;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class StarRocksSinkWriter extends AbstractSinkWriter<SeaTunnelRow, Void> {

    private final StarRocksISerializer serializer;
    private final StarRocksSinkManager manager;

    public StarRocksSinkWriter(Config pluginConfig,
                               SeaTunnelRowType seaTunnelRowType) {
        SinkConfig sinkConfig = SinkConfig.loadConfig(pluginConfig);
        List<String> fieldNames = Arrays.stream(seaTunnelRowType.getFieldNames()).collect(Collectors.toList());
        this.serializer = createSerializer(sinkConfig, seaTunnelRowType);
        this.manager = new StarRocksSinkManager(sinkConfig, fieldNames);
    }

    @Override
    public void write(SeaTunnelRow element) throws IOException {
        String record = serializer.serialize(element);
        manager.write(record);
    }

    @SneakyThrows
    @Override
    public Optional<Void> prepareCommit() {
        // Flush to storage before snapshot state is performed
        manager.flush();
        return super.prepareCommit();
    }

    @Override
    public void close() throws IOException {
        try {
            if (manager != null) {
                manager.close();
            }
        } catch (IOException e) {
            log.error("Close starRocks manager failed.", e);
            throw new IOException("Close starRocks manager failed.", e);
        }
    }

    public static StarRocksISerializer createSerializer(SinkConfig sinkConfig, SeaTunnelRowType seaTunnelRowType) {
        if (SinkConfig.StreamLoadFormat.CSV.equals(sinkConfig.getLoadFormat())) {
            return new StarRocksCsvSerializer(sinkConfig.getColumnSeparator(), seaTunnelRowType);
        }
        if (SinkConfig.StreamLoadFormat.JSON.equals(sinkConfig.getLoadFormat())) {
            return new StarRocksJsonSerializer(seaTunnelRowType);
        }
        throw new RuntimeException("Failed to create row serializer, unsupported `format` from stream load properties.");
    }
}
