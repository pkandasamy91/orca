/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.snapshot;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.snapshot.DeleteSnapshotStage;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeleteSnapshotTask implements CloudProviderAware, RetryableTask {
  private final KatoService katoService;

  @Autowired
  public DeleteSnapshotTask(KatoService katoService) {
    this.katoService = katoService;
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    DeleteSnapshotStage.DeleteSnapshotRequest deleteSnapshotRequest =
        stage.mapTo(DeleteSnapshotStage.DeleteSnapshotRequest.class);

    List<Map<String, Map>> operations =
        deleteSnapshotRequest.getSnapshotIds().stream()
            .map(
                snapshotId -> {
                  Map<String, Object> operation = new HashMap<>();
                  operation.put("credentials", deleteSnapshotRequest.getCredentials());
                  operation.put("region", deleteSnapshotRequest.getRegion());
                  operation.put("snapshotId", snapshotId);
                  return Collections.<String, Map>singletonMap("deleteSnapshot", operation);
                })
            .collect(toList());

    TaskId taskId =
        katoService.requestOperations(deleteSnapshotRequest.getCloudProvider(), operations);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deleteSnapshot");
    outputs.put("kato.last.task.id", taskId);
    outputs.put("delete.region", deleteSnapshotRequest.getRegion());
    outputs.put("delete.account.name", deleteSnapshotRequest.getCredentials());

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(10);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(2);
  }
}
