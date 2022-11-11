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

package org.apache.seatunnel.connectors.seatunnel.myhours.source;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.connectors.seatunnel.common.source.AbstractSingleSplitReader;
import org.apache.seatunnel.connectors.seatunnel.common.source.SingleSplitReaderContext;
import org.apache.seatunnel.connectors.seatunnel.http.client.HttpClientProvider;
import org.apache.seatunnel.connectors.seatunnel.http.client.HttpResponse;
import org.apache.seatunnel.connectors.seatunnel.http.source.HttpSource;
import org.apache.seatunnel.connectors.seatunnel.http.source.HttpSourceReader;
import org.apache.seatunnel.connectors.seatunnel.myhours.source.config.MyHoursSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.myhours.source.config.MyHoursSourceParameter;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
@AutoService(SeaTunnelSource.class)
public class MyHoursSource extends HttpSource {
    private final MyHoursSourceParameter myHoursSourceParameter = new MyHoursSourceParameter();

    @Override
    public String getPluginName() {
        return "MyHours";
    }

    @Override
    public void prepare(Config pluginConfig) throws PrepareFailException {
        CheckResult result = CheckConfigUtil.checkAllExists(pluginConfig, MyHoursSourceConfig.URL.key(),
                MyHoursSourceConfig.EMAIL.key(), MyHoursSourceConfig.PASSWORD.key());
        if (!result.isSuccess()) {
            throw new PrepareFailException(getPluginName(), PluginType.SOURCE, result.getMsg());
        }
        // Login to get accessToken
        String accessToken = getAccessToken(pluginConfig);
        this.myHoursSourceParameter.buildWithConfig(pluginConfig, accessToken);
        buildSchemaWithConfig(pluginConfig);
    }

    @Override
    public AbstractSingleSplitReader<SeaTunnelRow> createReader(SingleSplitReaderContext readerContext) throws Exception {
        return new HttpSourceReader(this.myHoursSourceParameter, readerContext, this.deserializationSchema);
    }

    private String getAccessToken(Config pluginConfig){
        MyHoursSourceParameter myHoursLoginParameter = new MyHoursSourceParameter();
        myHoursLoginParameter.buildWithLoginConfig(pluginConfig);
        HttpClientProvider loginHttpClient = new HttpClientProvider(myHoursLoginParameter);
        try {
            HttpResponse response = loginHttpClient.doPost(myHoursLoginParameter.getUrl(), myHoursLoginParameter.getBody());
            if (HttpResponse.STATUS_OK == response.getCode()) {
                String content = response.getContent();
                if (!Strings.isNullOrEmpty(content)) {
                    Map<String, String> contentMap = JsonUtils.toMap(content);
                    return contentMap.get(MyHoursSourceConfig.ACCESS_TOKEN);
                }
            }
            throw new RuntimeException(String.format("login http client execute exception, http response status code:[%d], content:[%s]",
                response.getCode(),
                response.getContent()));
        } catch (Exception e) {
            throw new RuntimeException("login http client execute exception");
        } finally {
            try {
                loginHttpClient.close();
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

}
