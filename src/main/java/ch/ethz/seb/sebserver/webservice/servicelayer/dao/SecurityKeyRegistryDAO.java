/*
 * Copyright (c) 2022 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.dao;

import java.util.Collection;

import org.springframework.context.event.EventListener;

import ch.ethz.seb.sebserver.gbl.model.EntityKey;
import ch.ethz.seb.sebserver.gbl.model.institution.SecurityKey;
import ch.ethz.seb.sebserver.gbl.model.institution.SecurityKey.KeyType;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.impl.ExamDeletionEvent;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.impl.ExamTemplateDeletionEvent;

/** Concrete EntityDAO interface of SecurityKeyRegistry entities */
public interface SecurityKeyRegistryDAO extends EntityDAO<SecurityKey, SecurityKey> {

    Result<SecurityKey> registerCopyForExam(Long keyId, Long examId);

    Result<SecurityKey> registerCopyForExamTemplate(Long keyId, Long examTemplateId);

    Result<Collection<SecurityKey>> getAll(Long institutionId, Long examId, KeyType type);

    Result<EntityKey> delete(Long keyId);

    @EventListener(ExamDeletionEvent.class)
    void notifyExamDeletion(ExamDeletionEvent event);

    @EventListener(ExamTemplateDeletionEvent.class)
    void notifyExamTemplateDeletion(ExamTemplateDeletionEvent event);

}
