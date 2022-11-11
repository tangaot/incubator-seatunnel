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

package org.apache.seatunnel.e2e.common.container.seatunnel;

import static org.apache.seatunnel.e2e.common.util.ContainerUtil.PROJECT_ROOT_PATH;

import org.apache.seatunnel.e2e.common.container.AbstractTestContainer;
import org.apache.seatunnel.e2e.common.container.ContainerExtendedFactory;
import org.apache.seatunnel.e2e.common.container.TestContainer;
import org.apache.seatunnel.e2e.common.container.TestContainerId;

import com.google.auto.service.AutoService;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
@Slf4j
@AutoService(TestContainer.class)
public class SeaTunnelContainer extends AbstractTestContainer {
    private static final String JDK_DOCKER_IMAGE = "openjdk:8";
    private static final String CLIENT_SHELL = "seatunnel.sh";
    private static final String SERVER_SHELL = "seatunnel-cluster.sh";
    private GenericContainer<?> server;

    @Override
    public void startUp() throws Exception {
        server = new GenericContainer<>(getDockerImage())
            .withNetwork(NETWORK)
            .withCommand(Paths.get(SEATUNNEL_HOME, "bin", SERVER_SHELL).toString())
            .withNetworkAliases("server")
            .withExposedPorts()
            .withLogConsumer(new Slf4jLogConsumer(DockerLoggerFactory.getLogger("seatunnel-engine:" + JDK_DOCKER_IMAGE)))
            .waitingFor(Wait.forLogMessage(".*received new worker register.*\\n", 1));
        copySeaTunnelStarterToContainer(server);
        server.withCopyFileToContainer(MountableFile.forHostPath(PROJECT_ROOT_PATH + "/seatunnel-engine/seatunnel-engine-common/src/main/resources/"),
            Paths.get(SEATUNNEL_HOME, "config").toString());
        server.start();
        // execute extra commands
        executeExtraCommands(server);
    }

    @Override
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Override
    protected String getDockerImage() {
        return JDK_DOCKER_IMAGE;
    }

    @Override
    protected String getStartModuleName() {
        return "seatunnel-starter";
    }

    @Override
    protected String getStartShellName() {
        return CLIENT_SHELL;
    }

    @Override
    protected String getConnectorModulePath() {
        return "seatunnel-connectors-v2";
    }

    @Override
    protected String getConnectorType() {
        return "seatunnel";
    }

    @Override
    protected String getConnectorNamePrefix() {
        return "connector-";
    }

    @Override
    protected List<String> getExtraStartShellCommands() {
        return Collections.emptyList();
    }

    @Override
    public TestContainerId identifier() {
        return TestContainerId.SEATUNNEL;
    }

    @Override
    public void executeExtraCommands(ContainerExtendedFactory extendedFactory) throws IOException, InterruptedException {
        extendedFactory.extend(server);
    }

    @Override
    public Container.ExecResult executeJob(String confFile) throws IOException, InterruptedException {
        return executeJob(server, confFile);
    }
}
