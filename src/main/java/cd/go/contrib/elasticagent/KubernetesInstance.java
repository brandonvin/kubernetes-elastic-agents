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

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.NonNull;
import lombok.Builder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.Map;

/*
 * KubernetesInstance represents an agent pod in Kubernetes.
 */
@Value
@Builder(toBuilder = true)
public class KubernetesInstance {

    public static KubernetesInstance of(DateTime createdAt,
    String environment,
    String podName,
    Map<String, String> podAnnotations,
    long jobId,
    PodState podState) {
        return KubernetesInstance.builder()
                .createdAt(createdAt)
                .environment(environment)
                .podName(podName)
                .podAnnotations(podAnnotations)
                .jobId(jobId)
                .podState(podState)
                .build();
    }

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

    public static final String CLUSTER_PROFILE_ID = "go.cd/cluster-profile-id";
    public static final String ELASTIC_PROFILE_ID = "go.cd/elastic-profile-id";

    @NonNull @Builder.Default
    DateTime createdAt = DateTime.now();

    // populated from k8s pod metadata.labels.Elastic-Agent-Environment
    @NonNull
    String environment;

    @NonNull
    String podName;

    // populated from k8s pod metadata.annotations
    // go.cd/cluster-profile-id contains uuid of the profile
    // go.cd/elastic-profile-id contains hash of the profile
    @NonNull @Builder.Default
    Map<String, String> podAnnotations = Collections.emptyMap();

    // populated from k8s pod metadata.labels.Elastic-Agent-Job-Id
    @NonNull
    Long jobId;

    @NonNull @Builder.Default
    PodState podState = PodState.Pending;

    @NonNull @Builder.Default
    AgentState agentState = AgentState.Unknown;

    public static class KubernetesInstanceBuilder {
        public KubernetesInstanceBuilder podAnnotations(@NonNull Map<String, String> podAnnotations) {
            this.podAnnotations$value = Map.copyOf(podAnnotations);
            this.podAnnotations$set = true;
            return this;
        }
    }
}
