
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
import cd.go.contrib.elasticagent.utils.Util;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import static cd.go.contrib.elasticagent.KubernetesPlugin.LOG;

public class ShouldAssignWorkRequestExecutor implements RequestExecutor {
    private final AgentInstances<KubernetesInstance> agentInstances;
    private final ShouldAssignWorkRequest request;

    public ShouldAssignWorkRequestExecutor(ShouldAssignWorkRequest request, AgentInstances<KubernetesInstance> agentInstances) {
        this.request = request;
        this.agentInstances = agentInstances;
    }

    @Override
    public GoPluginApiResponse execute() {
        String agentId = request.agent().elasticAgentId();
        KubernetesInstance updated = agentInstances.updateAgent(agentId, instance -> {
            // No such agent is known to this plugin.
            if (instance == null) {
                return null;
            }

            Long jobId = request.jobIdentifier().getJobId();

            // Agent reuse disabled - only assign if the agent pod was created exactly for this job ID.
            if (!request.clusterProfileProperties().getEnableAgentReuse()) {
                // Job ID matches - assign work and mark the instance as building.
                if (jobId.equals(instance.getJobId())) {
                    LOG.debug("[should-assign-work] Job with identifier {} can be assigned to pod {}.",
                            request.jobIdentifier(),
                            instance.getPodName());
                    return instance.toBuilder().agentState(KubernetesInstance.AgentState.Building).build();
                }
                // Job ID doesn't match - don't assign work.
                return null;
            }

            // Agent reuse enabled - assign work if the job's cluster profile and elastic profile match this agent.
            String jobClusterProfileHash = Util.objectUUID(request.clusterProfileProperties());
            String podClusterProfileHash = instance.getPodAnnotations().get(KubernetesInstance.CLUSTER_PROFILE_HASH);
            boolean matchClusterProfile = jobClusterProfileHash.equals(podClusterProfileHash);

            String jobElasticProfileHash = Util.objectUUID(request.elasticProfileProperties());
            String podElasticProfileHash = instance.getPodAnnotations().get(KubernetesInstance.ELASTIC_PROFILE_HASH);
            boolean matchElasticProfile = jobElasticProfileHash.equals(podElasticProfileHash);

            LOG.info("[reuse] Should assign work? jobId={} has clusterProfileId={}, elasticProfileId={}; pod {} has clusterProfileId={}, elasticProfileId={}",
                    jobId,
                    jobClusterProfileHash,
                    jobElasticProfileHash,
                    instance.getPodName(),
                    podClusterProfileHash,
                    podElasticProfileHash);
            if (matchClusterProfile && matchElasticProfile) {
                LOG.info("[reuse] Reusing existing pod {} for job {}", instance.getPodName(), request);
                return instance.toBuilder().agentState(KubernetesInstance.AgentState.Building).build();
            }

            LOG.info("[should-assign-work] No KubernetesInstance can handle request {}", request);
            return null;
        });

        return DefaultGoPluginApiResponse.success(updated == null ? "false" : "true");
    }
}
