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

package org.apache.seatunnel.connectors.seatunnel.file.s3.source;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.connectors.seatunnel.common.schema.SeaTunnelSchema;
import org.apache.seatunnel.connectors.seatunnel.file.config.FileFormat;
import org.apache.seatunnel.connectors.seatunnel.file.config.FileSystemType;
import org.apache.seatunnel.connectors.seatunnel.file.exception.FilePluginException;
import org.apache.seatunnel.connectors.seatunnel.file.s3.config.S3Conf;
import org.apache.seatunnel.connectors.seatunnel.file.s3.config.S3Config;
import org.apache.seatunnel.connectors.seatunnel.file.source.BaseFileSource;
import org.apache.seatunnel.connectors.seatunnel.file.source.reader.ReadStrategyFactory;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;

import java.io.IOException;

@AutoService(SeaTunnelSource.class)
public class S3FileSource extends BaseFileSource {
    @Override
    public String getPluginName() {
        return FileSystemType.S3.getFileSystemPluginName();
    }

    @Override
    public void prepare(Config pluginConfig) throws PrepareFailException {
        CheckResult result = CheckConfigUtil.checkAllExists(pluginConfig,
                S3Config.FILE_PATH, S3Config.FILE_TYPE, S3Config.S3_BUCKET,
                S3Config.S3_ACCESS_KEY, S3Config.S3_SECRET_KEY);
        if (!result.isSuccess()) {
            throw new PrepareFailException(getPluginName(), PluginType.SOURCE, result.getMsg());
        }
        readStrategy = ReadStrategyFactory.of(pluginConfig.getString(S3Config.FILE_TYPE));
        readStrategy.setPluginConfig(pluginConfig);
        String path = pluginConfig.getString(S3Config.FILE_PATH);
        hadoopConf = S3Conf.buildWithConfig(pluginConfig);
        try {
            filePaths = readStrategy.getFileNamesByPath(hadoopConf, path);
        } catch (IOException e) {
            throw new PrepareFailException(getPluginName(), PluginType.SOURCE, "Check file path fail.");
        }
        // support user-defined schema
        FileFormat fileFormat = FileFormat.valueOf(pluginConfig.getString(S3Config.FILE_TYPE).toUpperCase());
        // only json text csv type support user-defined schema now
        if (pluginConfig.hasPath(SeaTunnelSchema.SCHEMA.key())) {
            switch (fileFormat) {
                case CSV:
                case TEXT:
                case JSON:
                    Config schemaConfig = pluginConfig.getConfig(SeaTunnelSchema.SCHEMA.key());
                    SeaTunnelRowType userDefinedSchema = SeaTunnelSchema
                            .buildWithConfig(schemaConfig)
                            .getSeaTunnelRowType();
                    readStrategy.setSeaTunnelRowTypeInfo(userDefinedSchema);
                    rowType = readStrategy.getActualSeaTunnelRowTypeInfo();
                    break;
                case ORC:
                case PARQUET:
                    throw new UnsupportedOperationException("SeaTunnel does not support user-defined schema for [parquet, orc] files");
                default:
                    // never got in there
                    throw new UnsupportedOperationException("SeaTunnel does not supported this file format");
            }
        } else {
            try {
                rowType = readStrategy.getSeaTunnelRowTypeInfo(hadoopConf, filePaths.get(0));
            } catch (FilePluginException e) {
                throw new PrepareFailException(getPluginName(), PluginType.SOURCE, "Read file schema error.", e);
            }
        }
    }
}
