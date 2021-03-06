/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask.DeployServerGroup
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class AbstractWaitForClusterWideClouddriverTaskSpec extends Specification {
  private static final String application = 'bar'
  private static final String cluster = 'bar'
  private static final String credentials = 'test'
  private static final String cloudProvider = 'aws'
  private static final String region = 'us-east-1'

  static class TestTask extends AbstractWaitForClusterWideClouddriverTask {
    @Override
    boolean isServerGroupOperationInProgress(StageExecution stage,
                                             List<Map> interestingHealthProviderNames,
                                             Optional<ServerGroup> serverGroup) {
      return serverGroup.isPresent()
    }
  }

  CloudDriverService cloudDriverService = Mock()
  @Subject TestTask task = new TestTask(cloudDriverService: cloudDriverService)

  def 'uses deploy.server.groups to populate inital remainingDeployServerGroups list'() {
    when:
    def result = task.execute(stage(['deploy.server.groups': deployServerGroups, regions: regions]))

    then:
    1 * cloudDriverService.maybeCluster(application, credentials, cluster, cloudProvider) >> Optional.of(ModelUtils.cluster([serverGroups: serverGroups]))

    result.status == ExecutionStatus.RUNNING
    result.context.remainingDeployServerGroups == expected

    where:
    serverGroups = [sg('s1', 'r1'), sg('s2', 'r1'), sg('s3', 'r2'), sg('s4', 'r2')]
    deployServerGroups = serverGroups.groupBy { it.region }.collectEntries { k, v -> [(k): v.collect { it.name }]}
    expected = serverGroups.collect { new DeployServerGroup(it.region, it.name) }
    regions = ['r1', 'r2']
  }


  def 'succeeds immediately if nothing to wait for'() {
    expect:
    task.execute(stage([:])).status == ExecutionStatus.SUCCEEDED
  }

  def 'fails if no cluster present'() {
    when:
    def result = task.execute(stage([remainingDeployServerGroups: [dsg('c1')]]))

    then:
    1 * cloudDriverService.maybeCluster(application, credentials, cluster, cloudProvider) >> Optional.empty()

    thrown(IllegalStateException)
  }

  def 'fails if no server groups in cluster'() {
    when:
    def result = task.execute(stage([remainingDeployServerGroups: [dsg('c1')]]))

    then:
    1 * cloudDriverService.maybeCluster(application, credentials, cluster, cloudProvider) >> Optional.of(new Cluster(serverGroups: []))

    thrown(IllegalStateException)
  }

  def 'runs while there are still server groups'() {
    when:
    def result = task.execute(stage([remainingDeployServerGroups: [dsg('c1'), dsg('c2')]]))

    then:
    1 * cloudDriverService.maybeCluster(application, credentials, cluster, cloudProvider) >> Optional.of(ModelUtils.cluster([serverGroups: [sg('c1')]]))

    result.status == ExecutionStatus.RUNNING
    result.context.remainingDeployServerGroups == [dsg('c1')]
  }

  def 'finishes when last serverGroups disappear'() {
    when:
    def result = task.execute(stage([remainingDeployServerGroups: [dsg('c1')]]))

    then:
    1 * cloudDriverService.maybeCluster(application, credentials, cluster, cloudProvider) >> Optional.of(ModelUtils.cluster([serverGroups: [sg('c2'), sg('c3')]]))

    result.status == ExecutionStatus.SUCCEEDED
  }


  Map sg(String name, String r = region) {
    [name: name, region: r, type: cloudProvider]
  }

  DeployServerGroup dsg(String name, String r = region) {
    new DeployServerGroup(r, name)
  }

  StageExecutionImpl stage(Map context) {
    def base = [
      cluster: cluster,
      credentials: credentials,
      cloudProvider: cloudProvider,
      regions: [region]
    ]
    new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), 'shrinkCluster', base + context)
  }
}
