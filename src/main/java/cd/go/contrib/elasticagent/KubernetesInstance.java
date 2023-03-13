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

import java.util.Collections;
import java.util.Map;
import java.time.Instant;

/**
 * KubernetesInstance represents an agent pod in Kubernetes.
 * Its fields are immutable.
 */
public class KubernetesInstance {

    /**
     * AgentState represents the possible agent states from the
     * GoCD server perspective - whether it is currently running a job,
     * ready to accept a new job, etc.
     */
    public enum AgentState {
        /**
         * Unknown means the agent hasn't yet been registered with the plugin.
         * For example, if the GoCD server restarted while a pod was building,
         * the state will be Unknown until the pod finishes its job.
         */
        Unknown,
        /**
         * Idle means the agent has just finished a job.
         */
        Idle,
        /**
         * Building means the agent has been assigned a job.
         */
        Building,
    }

    /**
     * CLUSTER_PROFILE_ID is a pod annotation that contains a hash of the cluster settings
     * that were used to create the pod.
     */
    public static final String CLUSTER_PROFILE_ID = "go.cd/cluster-profile-id";

    /**
     * ELASTIC_PROFILE_ID is a pod annotation that contains a hash of the elastic profile settings
     * that were used to create the pod.
     */
    public static final String ELASTIC_PROFILE_ID = "go.cd/elastic-profile-id";

    Instant createdAt;

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * environment is populated from k8s pod metadata.labels.Elastic-Agent-Environment
     */
    String environment;

    public String getEnvironment() {
        return this.environment;
    }

    String podName;

    public String getPodName() {
        return this.podName;
    }

    /**
     * podAnnotations is populated from k8s pod metadata.annotations
     */
    Map<String, String> podAnnotations;

    public Map<String, String> getPodAnnotations() {
        return Map.copyOf(this.podAnnotations);
    }

    /**
     * jobId is populated from k8s pod metadata.labels.Elastic-Agent-Job-Id
     */
    Long jobId;
    public Long getJobId() {
        return this.jobId;
    }

    PodState podState = PodState.Pending;
    public PodState getPodState() {
        return this.podState;
    }

    AgentState agentState = AgentState.Unknown;
    public AgentState getAgentState() {
        return this.agentState;
    }

    KubernetesInstance(
            Instant createdAt,
            String environment,
            String podName,
            Map<String, String> podAnnotations,
            Long jobId,
            PodState podState,
            AgentState agentState) {
        this.createdAt = createdAt;
        this.environment = environment;
        this.podName = podName;
        this.podAnnotations = Map.copyOf(podAnnotations);
        this.jobId = jobId;
        this.podState = podState;
        this.agentState = agentState;
    }

    public static class KubernetesInstanceBuilder {
        Instant createdAt = Instant.now();
        public KubernetesInstanceBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        String environment;
        public KubernetesInstanceBuilder environment(String environment) {
            this.environment = environment;
            return this;
        }

        String podName;
        public KubernetesInstanceBuilder podName(String podName) {
            this.podName = podName;
            return this;
        }

        Map<String, String> podAnnotations = Collections.emptyMap();
        public KubernetesInstanceBuilder podAnnotations(Map<String, String> podAnnotations) {
            if (podAnnotations == null) {
                this.podAnnotations = Collections.emptyMap();
            } else {
                this.podAnnotations = Map.copyOf(podAnnotations);
            }
            return this;
        }

        Long jobId;
        public KubernetesInstanceBuilder jobId(Long jobId) {
            this.jobId = jobId;
            return this;
        }
        PodState podState = PodState.Pending;
        public KubernetesInstanceBuilder podState(PodState podState) {
            this.podState = podState;
            return this;
        }

        AgentState agentState = AgentState.Unknown;
        public KubernetesInstanceBuilder agentState(AgentState agentState) {
            this.agentState = agentState;
            return this;
        }

        public KubernetesInstance build() {
            return new KubernetesInstance(createdAt, environment, podName, podAnnotations, jobId, podState, agentState);
        }
    }

    public static KubernetesInstanceBuilder builder() {
        return new KubernetesInstanceBuilder();
    }

    public KubernetesInstanceBuilder toBuilder() {
        return new KubernetesInstanceBuilder()
                .createdAt(createdAt)
                .environment(environment)
                .podName(podName)
                .podAnnotations(podAnnotations)
                .jobId(jobId)
                .podState(podState)
                .agentState(agentState);
    }

}
