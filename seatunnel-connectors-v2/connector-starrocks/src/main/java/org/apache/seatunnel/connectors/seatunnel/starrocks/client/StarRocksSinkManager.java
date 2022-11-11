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

package org.apache.seatunnel.connectors.seatunnel.starrocks.client;

import org.apache.seatunnel.connectors.seatunnel.starrocks.config.SinkConfig;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StarRocksSinkManager {

    private final SinkConfig sinkConfig;
    private final List<byte[]> batchList;

    private StarRocksStreamLoadVisitor starrocksStreamLoadVisitor;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private volatile boolean initialize;
    private volatile Exception flushException;
    private int batchRowCount = 0;
    private long batchBytesSize = 0;

    private Integer batchIntervalMs;

    public StarRocksSinkManager(SinkConfig sinkConfig, List<String> fileNames) {
        this.sinkConfig = sinkConfig;
        this.batchList = new ArrayList<>();
        this.batchIntervalMs = sinkConfig.getBatchIntervalMs();
        starrocksStreamLoadVisitor = new StarRocksStreamLoadVisitor(sinkConfig, fileNames);
    }

    private void tryInit() throws IOException {
        if (initialize) {
            return;
        }
        initialize = true;

        if (batchIntervalMs != null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setNameFormat("StarRocks-sink-output-%s").build());
            scheduledFuture = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        flush();
                    } catch (IOException e) {
                        flushException = e;
                    }
                },
                    batchIntervalMs,
                    batchIntervalMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void write(String record) throws IOException {
        tryInit();
        checkFlushException();
        byte[] bts = record.getBytes(StandardCharsets.UTF_8);
        batchList.add(bts);
        batchRowCount++;
        batchBytesSize += bts.length;
        if (batchRowCount >= sinkConfig.getBatchMaxSize() || batchBytesSize >= sinkConfig.getBatchMaxBytes()) {
            flush();
        }
    }

    public synchronized void close() throws IOException {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduler.shutdown();
        }

        flush();
    }

    public synchronized void flush() throws IOException {
        checkFlushException();
        if (batchList.isEmpty()) {
            return;
        }
        StarRocksFlushTuple tuple = null;
        for (int i = 0; i <= sinkConfig.getMaxRetries(); i++) {
            try {
                String label = createBatchLabel();
                tuple = new StarRocksFlushTuple(label, batchBytesSize, new ArrayList<>(batchList));
                starrocksStreamLoadVisitor.doStreamLoad(tuple);
            } catch (Exception e) {

                log.error("Writing records to StarRocks failed, retry times = {}", i, e);
                if (i >= sinkConfig.getMaxRetries()) {
                    throw new IOException("Writing records to StarRocks failed.", e);
                }

                if (e instanceof StarRocksStreamLoadFailedException && ((StarRocksStreamLoadFailedException) e).needReCreateLabel()) {
                    String newLabel = createBatchLabel();
                    log.warn(String.format("Batch label changed from [%s] to [%s]", tuple.getLabel(), newLabel));
                    tuple.setLabel(newLabel);
                }

                try {
                    long backoff = Math.min(sinkConfig.getRetryBackoffMultiplierMs() * i,
                            sinkConfig.getMaxRetryBackoffMs());
                    Thread.sleep(backoff);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "Unable to flush; interrupted while doing another attempt.", e);
                }
            }
        }
        batchList.clear();
        batchRowCount = 0;
        batchBytesSize = 0;
    }

    private void checkFlushException() {
        if (flushException != null) {
            throw new RuntimeException("Writing records to StarRocks failed.", flushException);
        }
    }

    public String createBatchLabel() {
        StringBuilder sb = new StringBuilder();
        if (!Strings.isNullOrEmpty(sinkConfig.getLabelPrefix())) {
            sb.append(sinkConfig.getLabelPrefix());
        }
        return sb.append(UUID.randomUUID().toString())
                .toString();
    }
}
