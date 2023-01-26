
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
        KubernetesInstance updated = agentInstances.compute(agentId, (_agentId, instance) -> {
            // No such agent is known to this plugin.
            if (instance == null) {
                return null;
            }

            Long jobId = request.jobIdentifier().getJobId();

            // TODO: not sure if matching on job ID is still needed.
            // Try disabling and see what happens.
            if (jobId.equals(instance.jobId())) {
                LOG.debug("[should-assign-work] Job with identifier {} can be assigned to pod {}.",
                        request.jobIdentifier(),
                        instance.getPodName());
                return instance.withAgentState(KubernetesInstance.AgentState.Building);
            }

            String jobClusterProfileId = request.clusterProfileProperties().uuid();
            String podClusterProfileId = instance.getPodAnnotations().getOrDefault(KubernetesInstance.CLUSTER_PROFILE_ID, "unknown");
            boolean matchClusterProfile = jobClusterProfileId.equals(podClusterProfileId);

            String jobElasticProfileId = Integer.toHexString(request.elasticProfileProperties().hashCode());
            String podElasticProfileId = instance.getPodAnnotations().getOrDefault(KubernetesInstance.ELASTIC_PROFILE_ID, "unknown");
            boolean matchElasticProfile = jobElasticProfileId.equals(podElasticProfileId);

            LOG.info("[reuse] Should assign work? jobId={} has clusterProfileId={}, elasticProfileId={}; pod {} has clusterProfileId={}, elasticProfileId={}",
                    jobId,
                    jobClusterProfileId,
                    jobElasticProfileId,
                    instance.getPodName(),
                    podClusterProfileId,
                    podElasticProfileId);
            if (matchClusterProfile && matchElasticProfile) {
                LOG.info("[reuse] Reusing existing pod {} for job {}", instance.getPodName(), request);
                return instance.withAgentState(KubernetesInstance.AgentState.Building);
            }

            LOG.info(String.format("[should-assign-work] No KubernetesInstance can handle request %s", request));
            return null;
        });

        return DefaultGoPluginApiResponse.success(updated == null ? "false" : "true");
    }
}
