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

package cd.go.contrib.elasticagent;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Map;

/*
 * KubernetesInstance represents an agent pod in Kubernetes.
 */
public class KubernetesInstance {

    public static final String CLUSTER_PROFILE_ID = "go.cd/cluster-profile-id";
    public static final String ELASTIC_PROFILE_ID = "go.cd/elastic-profile-id";

    private final DateTime createdAt;

    // populated from k8s pod metadata.labels.Elastic-Agent-Environment
    private final String environment;

    private AgentState agentState;

    public enum AgentState {
        // Unknown means the agent hasn't yet been registered with the plugin.
        // For example, if the GoCD server restarted while a pod was building,
        // the state will be Unknown until the pod finishes its job.
        Unknown,
        // Idle means the agent has just finished a job.
        Idle,
        // Building means the agent has been assigned a job.
        Building,
    }

    private final String podName;

    // populated from k8s pod metadata.annotations
    // gocd/cluster-profile-id contains uuid of the profile
    // gocd/elastic-profile-id contains hash of the profile
    private final Map<String, String> podAnnotations;

    // populated from k8s pod metadata.labels.Elastic-Agent-Job-Id
    private final Long jobId;
    private final PodState podState;

    public KubernetesInstance(DateTime createdAt,
                              String environment,
                              String podName,
                              Map<String, String> podAnnotations,
                              Long jobId,
                              PodState podState,
                              AgentState agentState) {
        this.createdAt = createdAt.withZone(DateTimeZone.UTC);
        this.environment = environment;
        this.podName = podName;
        this.podAnnotations = Map.copyOf(podAnnotations);
        this.jobId = jobId;
        this.podState = podState;
        this.agentState = agentState;
    }

    public String getPodName() {
        return podName;
    }

    public DateTime createdAt() {
        return createdAt;
    }

    public String environment() {
        return environment;
    }

    public Map<String, String> getPodAnnotations() {
        return podAnnotations;
    }

    public Long jobId() {
        return jobId;
    }

    public boolean isPending() {
        return this.podState.equals(PodState.Pending);
    }

    public PodState getPodState() {
        return this.podState;
    }

    public AgentState getAgentState() {
        return agentState;
    }

    public KubernetesInstance withAgentState(AgentState newAgentState) {
        return new KubernetesInstance(
            createdAt,
            environment,
            podName,
            podAnnotations,
            jobId,
            podState,
            newAgentState
        );
    }

    @Override
    public String toString() {
      return "KubernetesInstance{"
        + "createdAt=" + createdAt
        + ", environment=" + environment
        + ", podName=" + podName
        + ", podAnnotations=" + podAnnotations
        + ", jobId=" + jobId
        + ", podState=" + podState
        + ", agentState=" + agentState
        + "}";
    }
}
