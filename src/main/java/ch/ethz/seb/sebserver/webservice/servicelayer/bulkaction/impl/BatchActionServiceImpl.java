/*
 * Copyright (c) 2021 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ch.ethz.seb.sebserver.gbl.api.API;
import ch.ethz.seb.sebserver.gbl.api.API.BatchActionType;
import ch.ethz.seb.sebserver.gbl.api.APIMessage;
import ch.ethz.seb.sebserver.gbl.api.APIMessage.APIMessageException;
import ch.ethz.seb.sebserver.gbl.api.EntityType;
import ch.ethz.seb.sebserver.gbl.model.BatchAction;
import ch.ethz.seb.sebserver.gbl.model.EntityKey;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BatchActionExec;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BatchActionService;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.BatchActionDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.FilterMap;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ResourceNotFoundException;

@Service
@WebServiceProfile
public class BatchActionServiceImpl implements BatchActionService {

    private static final Logger log = LoggerFactory.getLogger(BatchActionServiceImpl.class);

    private final BatchActionDAO batchActionDAO;
    private final TaskScheduler taskScheduler;
    private final EnumMap<BatchActionType, BatchActionExec> batchExecutions;

    private ScheduledFuture<?> runningBatchProcess = null;

    public BatchActionServiceImpl(
            final BatchActionDAO batchActionDAO,
            final Collection<BatchActionExec> batchExecutions,
            final TaskScheduler taskScheduler) {

        this.batchActionDAO = batchActionDAO;
        this.taskScheduler = taskScheduler;

        this.batchExecutions = new EnumMap<>(BatchActionType.class);
        batchExecutions
                .stream()
                .forEach(exec -> this.batchExecutions.putIfAbsent(exec.actionType(), exec));
    }

    @Override
    public Result<BatchAction> validate(final BatchAction batchAction) {

        return Result.tryCatch(() -> {

            final BatchActionExec batchActionExec = this.batchExecutions.get(batchAction.actionType);
            if (batchActionExec == null) {
                throw new IllegalArgumentException(
                        "Batch action execution not found for batch action type: " + batchAction.actionType);
            }

            final APIMessage consistencyError = batchActionExec.checkConsistency(batchAction.attributes);
            if (consistencyError != null) {
                throw new APIMessageException(consistencyError);
            }

            return batchAction;

        });
    }

    @Override
    public Result<BatchAction> notifyNewBatchAction(final BatchAction batchAction) {
        return Result.tryCatch(() -> {
            processNextBatchAction();
            return batchAction;
        });
    }

    @Override
    public Result<BatchAction> getRunningAction(final String actionId) {
        return this.batchActionDAO.byModelId(actionId);
    }

    @Override
    public Result<Collection<BatchAction>> getRunningActions(final Long institutionId) {
        return this.batchActionDAO.allMatching(new FilterMap().putIfAbsent(
                API.PARAM_INSTITUTION_ID,
                String.valueOf(institutionId)))
                .map(results -> results.stream()
                        .filter(action -> StringUtils.isNotBlank(action.processorId) &&
                                !action.processorId.endsWith(BatchAction.FINISHED_FLAG))
                        .collect(Collectors.toList()));
    }

    @Override
    public Result<Collection<BatchAction>> getRunningActions(final Long institutionId, final EntityType entityType) {
        return this.batchActionDAO.allMatching(new FilterMap().putIfAbsent(
                API.PARAM_INSTITUTION_ID,
                String.valueOf(institutionId)))
                .map(results -> results.stream()
                        .filter(action -> StringUtils.isNotBlank(action.processorId) &&
                                !action.processorId.endsWith(BatchAction.FINISHED_FLAG))
                        .filter(action -> action.actionType.entityType == entityType)
                        .collect(Collectors.toList()));
    }

    @Override
    public Result<Collection<BatchAction>> getFinishedActions(final Long institutionId) {
        return this.batchActionDAO.allMatching(new FilterMap().putIfAbsent(
                API.PARAM_INSTITUTION_ID,
                String.valueOf(institutionId)))
                .map(results -> results.stream()
                        .filter(action -> StringUtils.isNotBlank(action.processorId) &&
                                action.processorId.endsWith(BatchAction.FINISHED_FLAG))
                        .collect(Collectors.toList()));
    }

    @Scheduled(
            fixedDelayString = "${sebserver.webservice.batchaction.update-interval:60000}",
            initialDelay = 60000)
    private void processing() {
        processNextBatchAction();
    }

    private void processNextBatchAction() {

        if (this.runningBatchProcess != null && !this.runningBatchProcess.isDone()) {
            return;
        }

        try {

            final String processorId = UUID.randomUUID().toString();
            log.debug("Check for pending batch action with processorId: {}", processorId);

            this.batchActionDAO
                    .getAndReserveNext(processorId)
                    .onSuccess(action -> {
                        this.runningBatchProcess = this.taskScheduler.schedule(
                                new BatchActionProcess(
                                        new BatchActionHandlerImpl(action),
                                        this.batchExecutions.get(action.actionType),
                                        action),
                                Instant.now());
                    })
                    .onError(error -> {
                        if (error instanceof ResourceNotFoundException) {
                            log.debug("No pending batch action found...");
                        } else {
                            throw new RuntimeException(error);
                        }
                    });

        } catch (final Exception e) {
            log.error("Failed to schedule BatchActionProcess task: ", e);
        }
    }

    private final static class BatchActionProcess implements Runnable {

        private final BatchActionHandler batchActionHandler;
        private final BatchActionExec batchActionExec;
        private final BatchAction batchAction;

        private Set<String> processingIds;

        public BatchActionProcess(
                final BatchActionHandler batchActionHandler,
                final BatchActionExec batchActionExec,
                final BatchAction batchAction) {

            this.batchActionHandler = batchActionHandler;
            this.batchActionExec = batchActionExec;
            this.batchAction = batchAction;
        }

        @Override
        public void run() {
            try {

                log.info("Starting or continuing batch action - {}", this.batchAction);

                this.processingIds = new HashSet<>(this.batchAction.sourceIds);
                this.processingIds.removeAll(this.batchAction.successful);

                this.processingIds
                        .stream()
                        .forEach(modelId -> {

                            if (log.isDebugEnabled()) {
                                log.debug("Process batch action type: {}, id: {}",
                                        this.batchAction.actionType,
                                        modelId);
                            }

                            this.batchActionExec
                                    .doSingleAction(modelId, this.batchAction)
                                    .onError(error -> this.batchActionHandler.handleError(modelId, error))
                                    .onSuccess(entityKey -> this.batchActionHandler.handleSuccess(entityKey));
                        });

                this.batchActionHandler.finishUp();

            } catch (final Exception e) {
                log.error("Unexpected error while batch action processing. processorId: {} action: ",
                        this.batchAction.processorId,
                        this.batchAction);
                log.info("Skip this batch action... new batch action process will be started automatically");
            }
        }
    }

    private interface BatchActionHandler {

        void handleSuccess(final EntityKey entityKey);

        void handleError(final String modelId, final Exception error);

        void finishUp();
    }

    private final class BatchActionHandlerImpl implements BatchActionHandler {

        public final BatchAction batchAction;

        public BatchActionHandlerImpl(final BatchAction batchAction) {
            this.batchAction = batchAction;
        }

        @Override
        public void handleSuccess(final EntityKey entityKey) {
            BatchActionServiceImpl.this.batchActionDAO.setSuccessfull(
                    this.batchAction.id,
                    this.batchAction.processorId,
                    entityKey.modelId);
        }

        @Override
        public void handleError(final String modelId, final Exception error) {
            log.error(
                    "Failed to process single entity on batch action. ModelId: {}, action: ",
                    modelId,
                    this.batchAction,
                    error);

            BatchActionServiceImpl.this.batchActionDAO.setFailure(
                    this.batchAction.id,
                    this.batchAction.processorId,
                    modelId,
                    error);
        }

        @Override
        public void finishUp() {
            BatchActionServiceImpl.this.batchActionDAO
                    .finishUp(this.batchAction.id, this.batchAction.processorId, false)
                    .onSuccess(action -> log.info("Finished batch action - {}", action))
                    .onError(error -> log.error(
                            "Failed to mark batch action as finished: {}",
                            this.batchAction, error));
        }
    }

}
