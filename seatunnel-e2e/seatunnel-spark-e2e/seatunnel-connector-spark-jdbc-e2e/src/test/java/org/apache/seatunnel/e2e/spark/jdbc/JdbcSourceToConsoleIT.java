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

package org.apache.seatunnel.e2e.spark.jdbc;

import static org.awaitility.Awaitility.given;

import org.apache.seatunnel.e2e.spark.SparkContainer;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class JdbcSourceToConsoleIT extends SparkContainer {
    private static final String POSTGRES_DOCKER_IMAGE = "postgres:alpine3.16";
    private PostgreSQLContainer<?> psl;

    @SuppressWarnings("checkstyle:MagicNumber")
    @BeforeEach
    public void startPostgreSqlContainer() throws InterruptedException, ClassNotFoundException, SQLException {
        psl = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_DOCKER_IMAGE))
                .withNetwork(NETWORK)
                .withNetworkAliases("postgresql")
                .withLogConsumer(new Slf4jLogConsumer(DockerLoggerFactory.getLogger(POSTGRES_DOCKER_IMAGE)));
        psl.setPortBindings(Lists.newArrayList("33306:3306"));
        Startables.deepStart(Stream.of(psl)).join();
        log.info("PostgreSql container started");
        Class.forName(psl.getDriverClassName());
        given().ignoreExceptions()
            .await()
            .atLeast(100, TimeUnit.MILLISECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> initializeJdbcTable());
        batchInsertData();
    }

    private void initializeJdbcTable() {
        try (Connection connection = DriverManager.getConnection(psl.getJdbcUrl(), psl.getUsername(), psl.getPassword())) {
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE test (\n" +
                    "  name varchar(255) NOT NULL,\n" +
                    "  age int NOT NULL\n" +
                    ")";
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Initializing PostgreSql table failed!", e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void batchInsertData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(psl.getJdbcUrl(), psl.getUsername(), psl.getPassword())) {
            String sql = "insert into test(name,age) values(?,?)";
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            for (int i = 0; i < 10; i++) {
                preparedStatement.setString(1, "Mike");
                preparedStatement.setInt(2, 20);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Batch insert data failed!", e);
        }
    }

    @Test
    public void testFakeSourceToJdbcSink() throws SQLException, IOException, InterruptedException {
        Container.ExecResult execResult = executeSeaTunnelSparkJob("/jdbc/jdbcsource_to_console.conf");
        Assertions.assertEquals(0, execResult.getExitCode());
    }

    @AfterEach
    public void closePostgreSqlContainer() {
        if (psl != null) {
            psl.stop();
        }
    }
}
