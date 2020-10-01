/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.dao.impl;

import static org.mybatis.dynamic.sql.SqlBuilder.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.ethz.seb.sebserver.gbl.api.API.BulkActionType;
import ch.ethz.seb.sebserver.gbl.api.EntityType;
import ch.ethz.seb.sebserver.gbl.model.EntityDependency;
import ch.ethz.seb.sebserver.gbl.model.EntityKey;
import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection;
import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection.ConnectionStatus;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.gbl.util.Utils;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientConnectionRecordDynamicSqlSupport;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientConnectionRecordMapper;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientEventRecordDynamicSqlSupport;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientEventRecordMapper;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientInstructionRecordDynamicSqlSupport;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ClientInstructionRecordMapper;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.mapper.ExamRecordDynamicSqlSupport;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.model.ClientConnectionRecord;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.impl.BulkAction;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ClientConnectionDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.DAOLoggingSupport;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.FilterMap;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ResourceNotFoundException;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.TransactionHandler;
import io.micrometer.core.instrument.util.StringUtils;

@Lazy
@Component
@WebServiceProfile
public class ClientConnectionDAOImpl implements ClientConnectionDAO {

    private final ClientConnectionRecordMapper clientConnectionRecordMapper;
    private final ClientEventRecordMapper clientEventRecordMapper;
    private final ClientInstructionRecordMapper clientInstructionRecordMapper;

    protected ClientConnectionDAOImpl(
            final ClientConnectionRecordMapper clientConnectionRecordMapper,
            final ClientEventRecordMapper clientEventRecordMapper,
            final ClientInstructionRecordMapper clientInstructionRecordMapper) {

        this.clientConnectionRecordMapper = clientConnectionRecordMapper;
        this.clientEventRecordMapper = clientEventRecordMapper;
        this.clientInstructionRecordMapper = clientInstructionRecordMapper;
    }

    @Override
    public EntityType entityType() {
        return EntityType.CLIENT_CONNECTION;
    }

    @Override
    @Transactional(readOnly = true)
    public Result<ClientConnection> byPK(final Long id) {
        return recordById(id)
                .flatMap(ClientConnectionDAOImpl::toDomainModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<Collection<ClientConnection>> allMatching(
            final FilterMap filterMap,
            final Predicate<ClientConnection> predicate) {

        return Result.tryCatch(() -> this.clientConnectionRecordMapper
                .selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.institutionId,
                        isEqualToWhenPresent(filterMap.getInstitutionId()))
                .and(
                        ClientConnectionRecordDynamicSqlSupport.examId,
                        isEqualToWhenPresent(filterMap.getClientConnectionExamId()))
                .and(
                        ClientConnectionRecordDynamicSqlSupport.status,
                        isEqualToWhenPresent(filterMap.getClientConnectionStatus()))
                .build()
                .execute()
                .stream()
                .map(ClientConnectionDAOImpl::toDomainModel)
                .flatMap(DAOLoggingSupport::logAndSkipOnError)
                .filter(predicate)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Result<Collection<ClientConnection>> allOf(final Set<Long> pks) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper.selectByExample()
                .where(ClientConnectionRecordDynamicSqlSupport.id, isIn(new ArrayList<>(pks)))
                .build()
                .execute()
                .stream()
                .map(ClientConnectionDAOImpl::toDomainModel)
                .flatMap(DAOLoggingSupport::logAndSkipOnError)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public Result<Collection<String>> getConnectionTokens(final Long examId) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper
                .selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.examId,
                        SqlBuilder.isEqualTo(examId))
                .build()
                .execute()
                .stream()
                .map(ClientConnectionRecord::getConnectionToken)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional
    public Result<ClientConnection> createNew(final ClientConnection data) {
        return Result.tryCatch(() -> {

            final ClientConnectionRecord newRecord = new ClientConnectionRecord(
                    null,
                    data.institutionId,
                    data.examId,
                    ConnectionStatus.CONNECTION_REQUESTED.name(),
                    data.connectionToken,
                    null,
                    data.clientAddress,
                    data.virtualClientAddress,
                    Utils.getMillisecondsNow(),
                    data.proctorRoomId);

            this.clientConnectionRecordMapper.insert(newRecord);
            return newRecord;
        })
                .flatMap(ClientConnectionDAOImpl::toDomainModel)
                .onError(TransactionHandler::rollback);
    }

    @Override
    @Transactional
    public Result<ClientConnection> save(final ClientConnection data) {
        return Result.tryCatch(() -> {

            final ClientConnectionRecord updateRecord = new ClientConnectionRecord(
                    data.id,
                    null,
                    data.examId,
                    data.status != null ? data.status.name() : null,
                    null,
                    data.userSessionId,
                    data.clientAddress,
                    data.virtualClientAddress,
                    null,
                    data.proctorRoomId);

            this.clientConnectionRecordMapper.updateByPrimaryKeySelective(updateRecord);
            return this.clientConnectionRecordMapper.selectByPrimaryKey(data.id);
        })
                .flatMap(ClientConnectionDAOImpl::toDomainModel)
                .onError(TransactionHandler::rollback);
    }

    @Override
    public Set<EntityDependency> getDependencies(final BulkAction bulkAction) {
        // only for deletion
        if (bulkAction.type == BulkActionType.ACTIVATE || bulkAction.type == BulkActionType.DEACTIVATE) {
            return Collections.emptySet();
        }
        // only if included
        if (!bulkAction.includesDependencyType(EntityType.CLIENT_CONNECTION)) {
            return Collections.emptySet();
        }

        // define the select function in case of source type
        Function<EntityKey, Result<Collection<EntityDependency>>> selectionFunction;
        switch (bulkAction.sourceType) {
            case INSTITUTION:
                selectionFunction = this::allIdsOfInstitution;
                break;
            case LMS_SETUP:
                selectionFunction = this::allIdsOfLmsSetup;
            case USER:
                selectionFunction = this::allIdsOfUser;
                break;
            case EXAM:
                selectionFunction = this::allIdsOfExam;
                break;
            default:
                selectionFunction = key -> Result.of(Collections.emptyList()); //empty select function
                break;
        }

        return getDependencies(bulkAction, selectionFunction);
    }

    @Override
    @Transactional
    public Result<Collection<EntityKey>> delete(final Set<EntityKey> all) {
        return Result.tryCatch(() -> {

            final List<Long> ids = extractListOfPKs(all);

            // first delete all related client events
            this.clientEventRecordMapper.deleteByExample()
                    .where(
                            ClientEventRecordDynamicSqlSupport.clientConnectionId,
                            SqlBuilder.isIn(ids))
                    .build()
                    .execute();

            // then delete all related client instructions
            final List<String> connectionTokens = this.clientConnectionRecordMapper.selectByExample()
                    .where(
                            ClientConnectionRecordDynamicSqlSupport.id,
                            SqlBuilder.isIn(ids))
                    .build()
                    .execute()
                    .stream()
                    .map(r -> r.getConnectionToken())
                    .collect(Collectors.toList());
            this.clientInstructionRecordMapper.deleteByExample()
                    .where(
                            ClientInstructionRecordDynamicSqlSupport.connectionToken,
                            SqlBuilder.isIn(connectionTokens))
                    .build()
                    .execute();

            // then delete all requested client-connections
            this.clientConnectionRecordMapper.deleteByExample()
                    .where(
                            ClientConnectionRecordDynamicSqlSupport.id,
                            SqlBuilder.isIn(ids))
                    .build()
                    .execute();

            return ids.stream()
                    .map(id -> new EntityKey(id, EntityType.CLIENT_CONNECTION))
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Result<ClientConnection> byConnectionToken(final String connectionToken) {
        return Result.tryCatch(() -> {
            final List<ClientConnectionRecord> list = this.clientConnectionRecordMapper
                    .selectByExample()
                    .where(
                            ClientConnectionRecordDynamicSqlSupport.connectionToken,
                            SqlBuilder.isEqualTo(connectionToken))

                    .build()
                    .execute();

            if (list.isEmpty()) {
                throw new ResourceNotFoundException(EntityType.CLIENT_CONNECTION, "connectionToken");
            }

            if (list.size() > 1) {
                throw new IllegalStateException("Only one ClientConnection expected but there are: " + list.size());
            }

            return list.get(0);
        })
                .flatMap(ClientConnectionDAOImpl::toDomainModel);
    }

    @Override
    public Result<Boolean> isActiveConnection(final Long examId, final String connectionToken) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper
                .selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.connectionToken,
                        SqlBuilder.isEqualTo(connectionToken))
                .and(
                        ClientConnectionRecordDynamicSqlSupport.examId,
                        SqlBuilder.isEqualTo(examId))
                .build()
                .execute()
                .stream()
                .findFirst()
                .isPresent());
    }

    @Override
    public Result<Set<String>> filterActive(final Long examId, final Set<String> connectionToken) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper
                .selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.connectionToken,
                        SqlBuilder.isIn(new ArrayList<>(connectionToken)))
                .and(
                        ClientConnectionRecordDynamicSqlSupport.examId,
                        SqlBuilder.isEqualTo(examId))
                .build()
                .execute()
                .stream()
                .filter(cc -> ConnectionStatus.ACTIVE.name().equals(cc.getStatus()))
                .map(ClientConnectionRecord::getConnectionToken)
                .collect(Collectors.toSet()));
    }

    private Result<ClientConnectionRecord> recordById(final Long id) {
        return Result.tryCatch(() -> {

            final ClientConnectionRecord record = this.clientConnectionRecordMapper.selectByPrimaryKey(id);
            if (record == null) {
                throw new ResourceNotFoundException(
                        entityType(),
                        String.valueOf(id));
            }

            return record;
        });
    }

    private static Result<ClientConnection> toDomainModel(final ClientConnectionRecord record) {
        return Result.tryCatch(() -> {

            final String status = record.getStatus();
            return new ClientConnection(
                    record.getId(),
                    record.getInstitutionId(),
                    record.getExamId(),
                    (StringUtils.isNotBlank(status))
                            ? ConnectionStatus.valueOf(status)
                            : ConnectionStatus.UNDEFINED,
                    record.getConnectionToken(),
                    record.getExamUserSessionId(),
                    record.getClientAddress(),
                    record.getVirtualClientAddress(),
                    record.getCreationTime(),
                    record.getProctorRoomId());
        });
    }

    private Result<Collection<EntityDependency>> allIdsOfInstitution(final EntityKey institutionKey) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper.selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.institutionId,
                        isEqualTo(Long.parseLong(institutionKey.modelId)))
                .build()
                .execute()
                .stream()
                .map(rec -> new EntityDependency(
                        institutionKey,
                        new EntityKey(rec.getId(), EntityType.CLIENT_CONNECTION),
                        rec.getExamUserSessionId(),
                        rec.getClientAddress()))
                .collect(Collectors.toList()));
    }

    private Result<Collection<EntityDependency>> allIdsOfLmsSetup(final EntityKey lmsSetupKey) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper.selectByExample()
                .leftJoin(ExamRecordDynamicSqlSupport.examRecord)
                .on(
                        ExamRecordDynamicSqlSupport.id,
                        equalTo(ClientConnectionRecordDynamicSqlSupport.examId))
                .where(
                        ExamRecordDynamicSqlSupport.lmsSetupId,
                        isEqualTo(Long.parseLong(lmsSetupKey.modelId)))
                .build()
                .execute()
                .stream()
                .map(rec -> new EntityDependency(
                        lmsSetupKey,
                        new EntityKey(rec.getId(), EntityType.CLIENT_CONNECTION),
                        rec.getExamUserSessionId(),
                        rec.getClientAddress()))
                .collect(Collectors.toList()));
    }

    private Result<Collection<EntityDependency>> allIdsOfUser(final EntityKey userKey) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper.selectByExample()
                .leftJoin(ExamRecordDynamicSqlSupport.examRecord)
                .on(
                        ExamRecordDynamicSqlSupport.id,
                        equalTo(ClientConnectionRecordDynamicSqlSupport.examId))
                .where(
                        ExamRecordDynamicSqlSupport.owner,
                        isEqualTo(userKey.modelId))
                .build()
                .execute()
                .stream()
                .map(rec -> new EntityDependency(
                        userKey,
                        new EntityKey(rec.getId(), EntityType.CLIENT_CONNECTION),
                        rec.getExamUserSessionId(),
                        rec.getClientAddress()))
                .collect(Collectors.toList()));
    }

    private Result<Collection<EntityDependency>> allIdsOfExam(final EntityKey examKey) {
        return Result.tryCatch(() -> this.clientConnectionRecordMapper.selectByExample()
                .where(
                        ClientConnectionRecordDynamicSqlSupport.examId,
                        isEqualTo(Long.parseLong(examKey.modelId)))
                .build()
                .execute()
                .stream()
                .map(rec -> new EntityDependency(
                        examKey,
                        new EntityKey(rec.getId(), EntityType.CLIENT_CONNECTION),
                        rec.getExamUserSessionId(),
                        rec.getClientAddress()))
                .collect(Collectors.toList()));
    }

}
