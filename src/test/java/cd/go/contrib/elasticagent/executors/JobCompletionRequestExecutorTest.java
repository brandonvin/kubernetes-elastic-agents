/*
 * Copyright 2022 Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cd.go.contrib.elasticagent.executors;

import cd.go.contrib.elasticagent.*;
import cd.go.contrib.elasticagent.model.JobIdentifier;
import cd.go.contrib.elasticagent.requests.JobCompletionRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class JobCompletionRequestExecutorTest {

    // TODO: test agent reuse enabled/disabled cases
    @Test
    public void shouldMarkInstanceIdleOnJobCompletion() throws Exception {
        JobIdentifier jobIdentifier = new JobIdentifier(100L);
        ClusterProfileProperties clusterProfileProperties = new ClusterProfileProperties();
        String elasticAgentId = "agent-1";
        JobCompletionRequest request = new JobCompletionRequest(elasticAgentId, jobIdentifier, new HashMap<>(), clusterProfileProperties);
        KubernetesInstance instance = KubernetesInstance.builder().podName(elasticAgentId).agentState(KubernetesInstance.AgentState.Building).build();
        Map<String, KubernetesInstance> instances = Map.of(elasticAgentId, instance);
        KubernetesAgentInstances agentInstances = new KubernetesAgentInstances(null, null, instances);
        assertThat(agentInstances.find(elasticAgentId).getAgentState()).isEqualTo(KubernetesInstance.AgentState.Building);

        PluginRequest pluginRequest = mock(PluginRequest.class);
        JobCompletionRequestExecutor executor = new JobCompletionRequestExecutor(request, agentInstances, pluginRequest);
        GoPluginApiResponse response = executor.execute();

        assertThat(agentInstances.find(elasticAgentId).getAgentState()).isEqualTo(KubernetesInstance.AgentState.Idle);
        assertEquals(200, response.responseCode());
        assertTrue(response.responseBody().isEmpty());
    }

}
