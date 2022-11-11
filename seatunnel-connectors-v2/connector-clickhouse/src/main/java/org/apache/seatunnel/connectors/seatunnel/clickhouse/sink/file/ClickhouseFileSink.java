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

package org.apache.seatunnel.connectors.seatunnel.clickhouse.sink.file;

import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.CLICKHOUSE_LOCAL_PATH;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.COPY_METHOD;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.DATABASE;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.FIELDS;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.HOST;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.NODE_ADDRESS;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.NODE_PASS;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.PASSWORD;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.SHARDING_KEY;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.TABLE;
import static org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseConfig.USERNAME;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.sink.SeaTunnelSink;
import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.config.ClickhouseFileCopyMethod;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.config.FileReaderOption;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.shard.Shard;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.shard.ShardMetadata;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.sink.client.ClickhouseProxy;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.state.CKAggCommitInfo;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.state.CKCommitInfo;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.state.ClickhouseSinkState;
import org.apache.seatunnel.connectors.seatunnel.clickhouse.util.ClickhouseUtil;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import com.clickhouse.client.ClickHouseNode;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AutoService(SeaTunnelSink.class)
public class ClickhouseFileSink implements SeaTunnelSink<SeaTunnelRow, ClickhouseSinkState, CKCommitInfo, CKAggCommitInfo> {

    private FileReaderOption readerOption;

    @Override
    public String getPluginName() {
        return "ClickhouseFile";
    }

    @Override
    public void prepare(Config config) throws PrepareFailException {
        CheckResult checkResult = CheckConfigUtil.checkAllExists(config, HOST.key(), TABLE.key(), DATABASE.key(), USERNAME.key(), PASSWORD.key(), CLICKHOUSE_LOCAL_PATH.key());
        if (!checkResult.isSuccess()) {
            throw new PrepareFailException(getPluginName(), PluginType.SINK, checkResult.getMsg());
        }
        Map<String, Object> defaultConfigs = ImmutableMap.<String, Object>builder()
                .put(COPY_METHOD.key(), COPY_METHOD.defaultValue().getName())
                .build();

        config = config.withFallback(ConfigFactory.parseMap(defaultConfigs));
        List<ClickHouseNode> nodes = ClickhouseUtil.createNodes(config.getString(HOST.key()),
                config.getString(DATABASE.key()), config.getString(USERNAME.key()), config.getString(PASSWORD.key()));

        ClickhouseProxy proxy = new ClickhouseProxy(nodes.get(0));
        Map<String, String> tableSchema = proxy.getClickhouseTableSchema(config.getString(TABLE.key()));
        String shardKey = null;
        String shardKeyType = null;
        if (config.hasPath(SHARDING_KEY.key())) {
            shardKey = config.getString(SHARDING_KEY.key());
            shardKeyType = tableSchema.get(shardKey);
        }
        ShardMetadata shardMetadata = new ShardMetadata(
                shardKey,
                shardKeyType,
                config.getString(DATABASE.key()),
                config.getString(TABLE.key()),
                false, // we don't need to set splitMode in clickhouse file mode.
                new Shard(1, 1, nodes.get(0)), config.getString(USERNAME.key()), config.getString(PASSWORD.key()));
        List<String> fields;
        if (config.hasPath(FIELDS.key())) {
            fields = config.getStringList(FIELDS.key());
            // check if the fields exist in schema
            for (String field : fields) {
                if (!tableSchema.containsKey(field)) {
                    throw new RuntimeException("Field " + field + " does not exist in table " + config.getString(TABLE.key()));
                }
            }
        } else {
            fields = new ArrayList<>(tableSchema.keySet());
        }
        Map<String, String> nodeUser = config.getObjectList(NODE_PASS.key()).stream()
                .collect(Collectors.toMap(configObject -> configObject.toConfig().getString(NODE_ADDRESS),
                    configObject -> configObject.toConfig().hasPath(USERNAME.key()) ? configObject.toConfig().getString(USERNAME.key()) : "root"));
        Map<String, String> nodePassword = config.getObjectList(NODE_PASS.key()).stream()
                .collect(Collectors.toMap(configObject -> configObject.toConfig().getString(NODE_ADDRESS),
                    configObject -> configObject.toConfig().getString(PASSWORD.key())));

        proxy.close();
        this.readerOption = new FileReaderOption(shardMetadata, tableSchema, fields, config.getString(CLICKHOUSE_LOCAL_PATH.key()),
                ClickhouseFileCopyMethod.from(config.getString(COPY_METHOD.key())), nodeUser, nodePassword);
    }

    @Override
    public void setTypeInfo(SeaTunnelRowType seaTunnelRowType) {
        this.readerOption.setSeaTunnelRowType(seaTunnelRowType);
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getConsumedType() {
        return this.readerOption.getSeaTunnelRowType();
    }

    @Override
    public SinkWriter<SeaTunnelRow, CKCommitInfo, ClickhouseSinkState> createWriter(SinkWriter.Context context) throws IOException {
        return new ClickhouseFileSinkWriter(readerOption, context);
    }
}
