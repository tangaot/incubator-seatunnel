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

package org.apache.seatunnel.connectors.seatunnel.file.sink.writer;

import org.apache.seatunnel.api.serialization.SerializationSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.utils.DateTimeUtils;
import org.apache.seatunnel.common.utils.DateUtils;
import org.apache.seatunnel.common.utils.TimeUtils;
import org.apache.seatunnel.connectors.seatunnel.file.sink.config.TextFileSinkConfig;
import org.apache.seatunnel.connectors.seatunnel.file.sink.util.FileSystemUtils;
import org.apache.seatunnel.format.text.TextSerializationSchema;

import lombok.NonNull;
import org.apache.hadoop.fs.FSDataOutputStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TextWriteStrategy extends AbstractWriteStrategy {
    private final Map<String, FSDataOutputStream> beingWrittenOutputStream;
    private final Map<String, Boolean> isFirstWrite;
    private final String fieldDelimiter;
    private final String rowDelimiter;
    private final DateUtils.Formatter dateFormat;
    private final DateTimeUtils.Formatter dateTimeFormat;
    private final TimeUtils.Formatter timeFormat;
    private SerializationSchema serializationSchema;

    public TextWriteStrategy(TextFileSinkConfig textFileSinkConfig) {
        super(textFileSinkConfig);
        this.beingWrittenOutputStream = new HashMap<>();
        this.isFirstWrite = new HashMap<>();
        this.fieldDelimiter = textFileSinkConfig.getFieldDelimiter();
        this.rowDelimiter = textFileSinkConfig.getRowDelimiter();
        this.dateFormat = textFileSinkConfig.getDateFormat();
        this.dateTimeFormat = textFileSinkConfig.getDatetimeFormat();
        this.timeFormat = textFileSinkConfig.getTimeFormat();
    }

    @Override
    public void setSeaTunnelRowTypeInfo(SeaTunnelRowType seaTunnelRowType) {
        super.setSeaTunnelRowTypeInfo(seaTunnelRowType);
        this.serializationSchema = TextSerializationSchema.builder()
                .seaTunnelRowType(seaTunnelRowType)
                .delimiter(fieldDelimiter)
                .dateFormatter(dateFormat)
                .dateTimeFormatter(dateTimeFormat)
                .timeFormatter(timeFormat)
                .build();
    }

    @Override
    public void write(@NonNull SeaTunnelRow seaTunnelRow) {
        String filePath = getOrCreateFilePathBeingWritten(seaTunnelRow);
        FSDataOutputStream fsDataOutputStream = getOrCreateOutputStream(filePath);
        try {
            if (isFirstWrite.get(filePath)) {
                isFirstWrite.put(filePath, false);
            } else {
                fsDataOutputStream.write(rowDelimiter.getBytes());
            }
            fsDataOutputStream.write(serializationSchema.serialize(seaTunnelRow));
        } catch (IOException e) {
            log.error("write data to file {} error", filePath);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void finishAndCloseFile() {
        beingWrittenOutputStream.forEach((key, value) -> {
            try {
                value.flush();
            } catch (IOException e) {
                log.error("error when flush file {}", key);
                throw new RuntimeException(e);
            } finally {
                try {
                    value.close();
                } catch (IOException e) {
                    log.error("error when close output stream {}", key);
                }
            }
            needMoveFiles.put(key, getTargetLocation(key));
        });
        beingWrittenOutputStream.clear();
        isFirstWrite.clear();
    }

    private FSDataOutputStream getOrCreateOutputStream(@NonNull String filePath) {
        FSDataOutputStream fsDataOutputStream = beingWrittenOutputStream.get(filePath);
        if (fsDataOutputStream == null) {
            try {
                fsDataOutputStream = FileSystemUtils.getOutputStream(filePath);
                beingWrittenOutputStream.put(filePath, fsDataOutputStream);
                isFirstWrite.put(filePath, true);
            } catch (IOException e) {
                log.error("can not get output file stream");
                throw new RuntimeException(e);
            }
        }
        return fsDataOutputStream;
    }
}
