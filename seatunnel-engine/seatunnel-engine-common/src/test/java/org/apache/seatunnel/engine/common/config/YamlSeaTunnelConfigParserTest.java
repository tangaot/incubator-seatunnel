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

package org.apache.seatunnel.engine.common.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class YamlSeaTunnelConfigParserTest {

    @Test
    public void testSeaTunnelConfig() {
        YamlSeaTunnelConfigLocator yamlConfigLocator = new YamlSeaTunnelConfigLocator();
        SeaTunnelConfig config;
        if (yamlConfigLocator.locateInWorkDirOrOnClasspath()) {
            // 2. Try loading YAML config from the working directory or from the classpath
            config = new YamlSeaTunnelConfigBuilder(yamlConfigLocator).setProperties(null).build();
        } else {
            throw new RuntimeException("can't find yaml in resources");
        }
        Assertions.assertNotNull(config);

        Assertions.assertEquals(config.getEngineConfig().getBackupCount(), 1);

        Assertions.assertEquals(config.getEngineConfig().getPrintExecutionInfoInterval(), 2);

        Assertions.assertFalse(config.getEngineConfig().getSlotServiceConfig().isDynamicSlot());

        Assertions.assertEquals(config.getEngineConfig().getSlotServiceConfig().getSlotNum(), 5);

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getCheckpointInterval(), 6000);

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getCheckpointTimeout(), 7000);

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getMaxConcurrentCheckpoints(), 5);

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getTolerableFailureCheckpoints(), 2);

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getStorage().getStorage(), "test");

        Assertions.assertEquals(config.getEngineConfig().getCheckpointConfig().getStorage().getMaxRetainedCheckpoints(), 3);

    }

}
