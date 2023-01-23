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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Map;

/*
 * KubernetesInstance represents an agent pod in Kubernetes.
 */
public class KubernetesInstance {
    private final DateTime createdAt;

    // populated from k8s pod metadata.labels.Elastic-Agent-Environment
    private final String environment;

    private AgentState agentState;

    public enum AgentState {
        Unknown, // agent hasn't yet registered with the plugin
        Idle, // agent has just finished work
        Building, // agent has been assigned work
    }

    private final String name;

    // populated from k8s pod metadata.annotations
    // gocd/cluster-profile-id contains uuid of the profile
    // gocd/elastic-profile-id contains hash of the profile
    private final Map<String, String> properties;

    // populated from k8s pod metadata.labels.Elastic-Agent-Job-Id
    private final Long jobId;
    private final PodState state;

    public KubernetesInstance(DateTime createdAt, String environment, String name, Map<String, String> properties, Long jobId, PodState state, AgentState agentState) {
        this.createdAt = createdAt.withZone(DateTimeZone.UTC);
        this.environment = environment;
        this.name = name;
        this.properties = properties;
        this.jobId = jobId;
        this.state = state;
        this.agentState = agentState;
    }

    public void terminate(KubernetesClient client) {
        client.pods().withName(name).delete();
    }

    public String name() {
        return name;
    }

    public DateTime createdAt() {
        return createdAt;
    }

    public String environment() {
        return environment;
    }

    public Map<String, String> getInstanceProperties() {
        return properties;
    }

    public Long jobId() {
        return jobId;
    }

    public boolean isPending() {
        return this.state.equals(PodState.Pending);
    }

    public AgentState getAgentState() {
        return agentState;
    }

    public void setAgentState(AgentState newState) {
        this.agentState = newState;
    }
}
