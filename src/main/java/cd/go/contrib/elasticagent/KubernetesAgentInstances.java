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

import cd.go.contrib.elasticagent.model.JobIdentifier;
import cd.go.contrib.elasticagent.requests.CreateAgentRequest;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static cd.go.contrib.elasticagent.KubernetesPlugin.LOG;
import static cd.go.contrib.elasticagent.utils.Util.getSimpleDateFormat;
import static java.text.MessageFormat.format;

public class KubernetesAgentInstances implements AgentInstances<KubernetesInstance> {
    private final ConcurrentHashMap<String, KubernetesInstance> instances = new ConcurrentHashMap<>();
    public Clock clock = Clock.DEFAULT;
    final Semaphore semaphore = new Semaphore(0, true);

    private KubernetesClientFactory factory;
    private KubernetesInstanceFactory kubernetesInstanceFactory;

    public KubernetesAgentInstances() {
        this(KubernetesClientFactory.instance(), new KubernetesInstanceFactory());
    }

    public KubernetesAgentInstances(KubernetesClientFactory factory) {
        this(factory, new KubernetesInstanceFactory());
    }

    public KubernetesAgentInstances(KubernetesClientFactory factory, KubernetesInstanceFactory kubernetesInstanceFactory) {
        this.factory = factory;
        this.kubernetesInstanceFactory = kubernetesInstanceFactory;
    }

    @Override
    public Optional<KubernetesInstance> createIfNecessary(CreateAgentRequest request, PluginSettings settings, PluginRequest pluginRequest, ConsoleLogAppender consoleLogAppender) {
        final Integer maxAllowedPods = settings.getMaxPendingPods();
        synchronized (instances) {
            refreshAll(settings);
            doWithLockOnSemaphore(new SetupSemaphore(maxAllowedPods, instances, semaphore));
            consoleLogAppender.accept("Waiting to create agent pod.");
            if (semaphore.tryAcquire()) {
                return createKubernetesInstance(request, settings, pluginRequest, consoleLogAppender);
            } else {
                String message = format("[Create Agent Request] The number of pending kubernetes pods is currently at the maximum permissible limit ({0}). Total kubernetes pods ({1}). Not creating any more pods.", maxAllowedPods, instances.size());
                LOG.warn(message);
                consoleLogAppender.accept(message);
                return null;
            }
        }
    }

    private void doWithLockOnSemaphore(Runnable runnable) {
        synchronized (semaphore) {
            runnable.run();
        }
    }

    private List<KubernetesInstance> findPodsEligibleForReuse(
        CreateAgentRequest request,
        PluginSettings settings,
        PluginRequest pluginRequest) {

        Long jobId = request.jobIdentifier().getJobId();
        String jobClusterProfileId = request.clusterProfileProperties().uuid();
        String jobElasticProfileId = Integer.toHexString(request.properties().hashCode());

        List<KubernetesInstance> eligiblePods = new ArrayList<>();

        for (KubernetesInstance instance : instances.values()) {
            if (instance.jobId().equals(jobId)) {
                eligiblePods.add(instance);
                continue;
            }

            // TODO: refactor
            String podClusterProfileId = instance.getInstanceProperties().getOrDefault("gocd/cluster-profile-id", "unknown");
            String podElasticProfileId = instance.getInstanceProperties().getOrDefault("gocd/elastic-profile-id", "unknown");

            if (podClusterProfileId.equals("unknown") || podElasticProfileId.equals("unknown")) {
                LOG.debug("[reuse] Pod {} is missing one of cluster profile ID or elastic profile ID ({}, {})",
                  instance.name(),
                  podClusterProfileId, podElasticProfileId);
            }

            boolean sameClusterProfile = podClusterProfileId.equals(podClusterProfileId);
            boolean sameElasticProfile = podElasticProfileId.equals(jobElasticProfileId);

            // TODO: guard against pod state as well - e.g. pending, terminating?
            // TODO: only reuse if pod is running?
            boolean instanceIsIdle = instance.getAgentState().equals(KubernetesInstance.AgentState.Idle);
            boolean isReusable = sameElasticProfile && sameClusterProfile && instanceIsIdle;

            LOG.info("[reuse] Pod eligible for reuse? {}. jobId={} has clusterProfileId={}, elasticProfileId={}; pod {} is in state {} with clusterProfileId={}, elasticProfileId={}",
                  isReusable ? "Yes" : "No",
                  jobId,
                  jobClusterProfileId,
                  jobElasticProfileId,
                  instance.name(),
                  instance.getAgentState(),
                  podClusterProfileId,
                  podElasticProfileId);

            if (isReusable) {
                eligiblePods.add(instance);
            }
        }

        return eligiblePods;
    }


    private Optional<KubernetesInstance> createKubernetesInstance(CreateAgentRequest request, PluginSettings settings, PluginRequest pluginRequest, ConsoleLogAppender consoleLogAppender) {
        JobIdentifier jobIdentifier = request.jobIdentifier();

        List<KubernetesInstance> reusablePods = findPodsEligibleForReuse(request, settings, pluginRequest);
        LOG.info("[reuse] Found {} pods eligible for reuse for CreateAgentRequest for job {}: {}",
              reusablePods.size(),
              jobIdentifier.getJobId(),
              reusablePods.stream().map(pod -> pod.name()).collect(Collectors.toList()));

        if (isAgentCreatedForJob(jobIdentifier.getJobId())) {
            String message = format("[Create Agent Request] Request for creating an agent for Job Identifier [{0}] has already been scheduled. Skipping current request.", jobIdentifier);
            LOG.warn(message);
            consoleLogAppender.accept(message);
            return Optional.empty();
        }

        if (reusablePods.isEmpty()) {
            KubernetesClient client = factory.client(settings);
            KubernetesInstance instance = kubernetesInstanceFactory.create(request, settings, client, pluginRequest);
            consoleLogAppender.accept(String.format("Created pod: %s", instance.name()));
            instance.setAgentState(KubernetesInstance.AgentState.Building);
            register(instance);
            consoleLogAppender.accept(String.format("Agent pod %s created. Waiting for it to register to the GoCD server.", instance.name()));
            return Optional.of(instance);
        } else {
            String message = String.format(
                "[reuse] Not creating a new pod - found %s eligible for reuse.",
                reusablePods.size()
            );
            consoleLogAppender.accept(message);
            LOG.info(message);
            return Optional.empty();
        }
    }

    private boolean isAgentCreatedForJob(Long jobId) {
        for (KubernetesInstance instance : instances.values()) {
            if (instance.jobId().equals(jobId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void terminate(String agentId, PluginSettings settings) {
        KubernetesInstance instance = instances.get(agentId);
        if (instance != null) {
            KubernetesClient client = factory.client(settings);
            instance.terminate(client);
        } else {
            LOG.warn(format("Requested to terminate an instance that does not exist {0}.", agentId));
        }
        instances.remove(agentId);
    }

    @Override
    public void terminateUnregisteredInstances(PluginSettings settings, Agents agents) throws Exception {
        KubernetesAgentInstances toTerminate = unregisteredAfterTimeout(settings, agents);
        if (toTerminate.instances.isEmpty()) {
            return;
        }

        LOG.warn(format("Terminating instances that did not register {0}.", toTerminate.instances.keySet()));
        for (String podName : toTerminate.instances.keySet()) {
            terminate(podName, settings);
        }
    }

    @Override
    public Agents instancesCreatedAfterTimeout(PluginSettings settings, Agents agents) {
        ArrayList<Agent> oldAgents = new ArrayList<>();
        for (Agent agent : agents.agents()) {
            KubernetesInstance instance = instances.get(agent.elasticAgentId());
            if (instance == null) {
                continue;
            }

            if (clock.now().isAfter(instance.createdAt().plus(settings.getAutoRegisterPeriod()))) {
                oldAgents.add(agent);
            }
        }
        return new Agents(oldAgents);
    }

    @Override
    public void refreshAll(PluginSettings properties) {
        LOG.debug("[Refresh Instances] Syncing k8s elastic agent pod information for cluster {}.", properties);
        PodList list = null;
        try {
            KubernetesClient client = factory.client(properties);
            list = client.pods().withLabel(Constants.KUBERNETES_POD_KIND_LABEL_KEY, Constants.KUBERNETES_POD_KIND_LABEL_VALUE).list();
        } catch (Exception e) {
            LOG.error("Error occurred while trying to list kubernetes pods:", e);

            if (e.getCause() instanceof SocketTimeoutException) {
                LOG.error("Error caused due to SocketTimeoutException. This generally happens due to stale kubernetes client. Clearing out existing kubernetes client and creating a new one!");
                factory.clearOutExistingClient();
                KubernetesClient client = factory.client(properties);
                list = client.pods().withLabel(Constants.KUBERNETES_POD_KIND_LABEL_KEY, Constants.KUBERNETES_POD_KIND_LABEL_VALUE).list();
            }
        }

        if (list == null) {
            LOG.info("Did not find any running kubernetes pods.");
            return;
        }

        // TODO: needs to persist Idle state written by JobCompletionRequest
        Map<String, KubernetesInstance> oldInstances = new HashMap<>(instances);
        instances.clear();

        for (Pod pod : list.getItems()) {
            String podName = pod.getMetadata().getName();
            // preserve pod's agent state
            KubernetesInstance newInstance = kubernetesInstanceFactory.fromKubernetesPod(pod);
            KubernetesInstance oldInstance = oldInstances.get(podName);
            if (oldInstance != null) {
                KubernetesInstance.AgentState oldAgentState = oldInstances.get(podName).getAgentState();
                newInstance.setAgentState(oldAgentState);
                LOG.debug("[reuse] Preserved AgentState {} upon refresh of pod {}", oldAgentState, podName);
            }
            register(newInstance);
        }

        LOG.info(String.format("[refresh-pod-state] Pod information successfully synced. All(Running/Pending) pod count is %d.", instances.size()));
    }

    @Override
    public KubernetesInstance find(String agentId) {
        return instances.get(agentId);
    }

    public void register(KubernetesInstance instance) {
        instances.put(instance.name(), instance);
    }

    private KubernetesAgentInstances unregisteredAfterTimeout(PluginSettings settings, Agents knownAgents) throws Exception {
        Period period = settings.getAutoRegisterPeriod();
        KubernetesAgentInstances unregisteredInstances = new KubernetesAgentInstances();
        KubernetesClient client = factory.client(settings);

        for (String instanceName : instances.keySet()) {
            if (knownAgents.containsAgentWithId(instanceName)) {
                continue;
            }

            Pod pod = getPod(client, instanceName);
            if (pod == null) {
                LOG.debug(String.format("[server-ping] Pod with name %s is already deleted.", instanceName));
                continue;
            }

            Date createdAt = getSimpleDateFormat().parse(pod.getMetadata().getCreationTimestamp());
            DateTime dateTimeCreated = new DateTime(createdAt);

            if (clock.now().isAfter(dateTimeCreated.plus(period))) {
                unregisteredInstances.register(kubernetesInstanceFactory.fromKubernetesPod(pod));
            }
        }

        return unregisteredInstances;
    }

    private Pod getPod(KubernetesClient client, String instanceName) {
        try {
            return client.pods().withName(instanceName).get();
        } catch (Exception e) {
            LOG.warn(String.format("[server-ping] Failed to fetch pod[%s] information:", instanceName), e);
            return null;
        }
    }

    public boolean instanceExists(KubernetesInstance instance) {
        return instances.contains(instance);
    }

    public boolean hasInstance(String elasticAgentId) {
        return find(elasticAgentId) != null;
    }
}
