
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

import cd.go.contrib.elasticagent.AgentInstances;
import cd.go.contrib.elasticagent.KubernetesInstance;
import cd.go.contrib.elasticagent.RequestExecutor;
import cd.go.contrib.elasticagent.requests.ShouldAssignWorkRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import static cd.go.contrib.elasticagent.KubernetesPlugin.LOG;
import static java.text.MessageFormat.format;

public class ShouldAssignWorkRequestExecutor implements RequestExecutor {
    private final AgentInstances<KubernetesInstance> agentInstances;
    private final ShouldAssignWorkRequest request;

    public ShouldAssignWorkRequestExecutor(ShouldAssignWorkRequest request, AgentInstances<KubernetesInstance> agentInstances) {
        this.request = request;
        this.agentInstances = agentInstances;
    }

    @Override
    public GoPluginApiResponse execute() {
        KubernetesInstance pod = agentInstances.find(request.agent().elasticAgentId());

        // If pod is null, it means this plugin didn't create that agent!
        // Or, the plugin just started up and hasn't yet refreshed its view of the pods.
        if (pod == null) {
            return DefaultGoPluginApiResponse.success("false");
        }

        if (request.jobIdentifier().getJobId().equals(pod.jobId())) {
            LOG.debug(format("[should-assign-work] Job with identifier {0} can be assigned to an agent {1}.", request.jobIdentifier(), pod.name()));
            return DefaultGoPluginApiResponse.success("true");
        }

        String podClusterId = pod.getInstanceProperties().getOrDefault("gocd/cluster-profile-id", "unknown");
        boolean matchClusterProfile = request.clusterProfileProperties().uuid().equals(podClusterId);
        String podElasticProfileId = pod.getInstanceProperties().getOrDefault("gocd/elastic-profile-id", "unknown");
        boolean matchElasticProfile = ("" + request.properties().hashCode()).equals(podElasticProfileId);
        if (matchClusterProfile && matchElasticProfile) {
            LOG.info(String.format("[Reuse] Reusing existing KubernetesInstance (pod) {} for job {}", pod, request));
            return DefaultGoPluginApiResponse.success("true");
        }

        LOG.info(String.format("[should-assign-work] No KubernetesInstance (pod) can handle job {}", request));
        return DefaultGoPluginApiResponse.success("false");

        //LOG.debug(format("[should-assign-work] Job with identifier {0} can not be assigned to an agent {1}.", request.jobIdentifier(), pod.name()));
        //return DefaultGoPluginApiResponse.success("false");
    }
}
