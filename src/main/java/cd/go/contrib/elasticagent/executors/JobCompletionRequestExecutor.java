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
import cd.go.contrib.elasticagent.requests.JobCompletionRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import static cd.go.contrib.elasticagent.KubernetesPlugin.LOG;

public class JobCompletionRequestExecutor implements RequestExecutor {
    private final JobCompletionRequest jobCompletionRequest;
    private final AgentInstances<KubernetesInstance> agentInstances;

    public JobCompletionRequestExecutor(JobCompletionRequest jobCompletionRequest,AgentInstances<KubernetesInstance> agentInstances) {
        this.jobCompletionRequest = jobCompletionRequest;
        this.agentInstances = agentInstances;
    }

    @Override
    public GoPluginApiResponse execute() throws Exception {
        String elasticAgentId = jobCompletionRequest.getElasticAgentId();
        KubernetesInstance updated = agentInstances.updateAgentState(elasticAgentId, KubernetesInstance.AgentState.Idle);
        if (updated != null) {
            LOG.debug("[reuse] Received job completion for agent ID {}. It is now marked Idle.", elasticAgentId);
        } else {
            // TODO: could register the instance here and put it in an idle state.
            // Otherwise it will be rediscovered later by refreshAll
            // and put in an Unknown state, and then terminated after a timeout.
            LOG.debug("[reuse] Received job completion for agent ID {}, which is not known to this plugin.", elasticAgentId);
        }
        return DefaultGoPluginApiResponse.success("");
    }
}
