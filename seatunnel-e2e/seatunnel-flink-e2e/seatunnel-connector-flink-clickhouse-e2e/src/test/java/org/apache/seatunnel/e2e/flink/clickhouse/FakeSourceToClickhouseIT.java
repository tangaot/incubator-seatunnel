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

package org.apache.seatunnel.e2e.flink.clickhouse;

import static org.awaitility.Awaitility.given;

import org.apache.seatunnel.e2e.flink.FlinkContainer;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerLoggerFactory;
import ru.yandex.clickhouse.BalancedClickhouseDataSource;
import ru.yandex.clickhouse.ClickHouseConnection;
import ru.yandex.clickhouse.ClickHouseStatement;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class FakeSourceToClickhouseIT extends FlinkContainer {

    private GenericContainer<?> clickhouseServer;
    private BalancedClickhouseDataSource dataSource;
    private static final String CLICKHOUSE_DOCKER_IMAGE = "yandex/clickhouse-server:22.1.3.7";

    @BeforeEach
    public void startClickhouseContainer() throws InterruptedException {
        clickhouseServer = new GenericContainer<>(CLICKHOUSE_DOCKER_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("clickhouse")
            .withLogConsumer(new Slf4jLogConsumer(DockerLoggerFactory.getLogger(CLICKHOUSE_DOCKER_IMAGE)));
        clickhouseServer.setPortBindings(Lists.newArrayList("8123:8123"));
        Startables.deepStart(Stream.of(clickhouseServer)).join();
        log.info("Clickhouse container started");
        // wait for clickhouse fully start
        dataSource = createDatasource();
        given().ignoreExceptions()
            .await()
            .atLeast(100, TimeUnit.MILLISECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> initializeClickhouseTable());
    }

    /**
     * Test insert into clickhouse table from fake source by flink batch mode.
     *
     * @throws IOException          read from conf file error.
     * @throws InterruptedException execute flink job error.
     * @throws SQLException         execute clickhouse sql error.
     */
    @Test
    public void testFakeSourceToClickhouseSink() throws IOException, InterruptedException, SQLException {
        Container.ExecResult execResult = executeSeaTunnelFlinkJob("/clickhouse/fakesource_to_clickhouse.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
        // query result
        try (ClickHouseConnection connection = dataSource.getConnection()) {
            ClickHouseStatement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select * from default.seatunnel_console");
            List<String> result = Lists.newArrayList();
            while (resultSet.next()) {
                result.add(resultSet.getString("name"));
            }
            Assertions.assertFalse(result.isEmpty());
        }
    }

    private BalancedClickhouseDataSource createDatasource() {
        String jdbcUrl = "jdbc:clickhouse://localhost:8123/default";
        Properties properties = new Properties();
        properties.setProperty("user", "default");
        properties.setProperty("password", "");
        return new BalancedClickhouseDataSource(jdbcUrl, properties);
    }

    private void initializeClickhouseTable() {
        try (ClickHouseConnection connection = dataSource.getConnection()) {
            ClickHouseStatement statement = connection.createStatement();
            String initializeTableSql = "CREATE TABLE default.seatunnel_console" +
                "(" +
                "    `name` Nullable(String)" +
                ")ENGINE = Memory";
            statement.execute(initializeTableSql);
        } catch (SQLException e) {
            throw new RuntimeException("Initializing clickhouse table failed", e);
        }
    }

    @AfterEach
    public void closeClickhouseContainer() {
        if (clickhouseServer != null) {
            clickhouseServer.stop();
        }
    }
}
