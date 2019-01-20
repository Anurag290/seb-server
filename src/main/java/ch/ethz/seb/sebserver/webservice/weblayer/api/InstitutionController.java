/*
 * Copyright (c) 2018 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.weblayer.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.ethz.seb.sebserver.gbl.model.EntityKey;
import ch.ethz.seb.sebserver.gbl.model.EntityKeyAndName;
import ch.ethz.seb.sebserver.gbl.model.EntityProcessingReport;
import ch.ethz.seb.sebserver.gbl.model.EntityType;
import ch.ethz.seb.sebserver.gbl.model.institution.Institution;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.AuthorizationGrantService;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.PrivilegeType;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.SEBServerUser;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BulkAction;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BulkAction.Type;
import ch.ethz.seb.sebserver.webservice.servicelayer.bulkaction.BulkActionService;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.InstitutionDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserActivityLogDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.UserActivityLogDAO.ActivityType;

@WebServiceProfile
@RestController
@RequestMapping("/${sebserver.webservice.api.admin.endpoint}" + RestAPI.ENDPOINT_INSTITUTION)
public class InstitutionController {

    private final InstitutionDAO institutionDAO;
    private final AuthorizationGrantService authorizationGrantService;
    private final UserActivityLogDAO userActivityLogDAO;
    private final BulkActionService bulkActionService;

    public InstitutionController(
            final InstitutionDAO institutionDAO,
            final AuthorizationGrantService authorizationGrantService,
            final UserActivityLogDAO userActivityLogDAO,
            final BulkActionService bulkActionService) {

        this.institutionDAO = institutionDAO;
        this.authorizationGrantService = authorizationGrantService;
        this.userActivityLogDAO = userActivityLogDAO;
        this.bulkActionService = bulkActionService;
    }

    @RequestMapping(path = "/self", method = RequestMethod.GET)
    public Institution getOwn() {
        final SEBServerUser currentUser = this.authorizationGrantService
                .getUserService()
                .getCurrentUser();

        final Long institutionId = currentUser.institutionId();
        return this.institutionDAO.byPK(institutionId).getOrThrow();
    }

    @RequestMapping(path = "/{id}", method = RequestMethod.GET)
    public Institution getById(@PathVariable final Long id) {
        return this.institutionDAO
                .byPK(id)
                .flatMap(inst -> this.authorizationGrantService.checkGrantOnEntity(
                        inst,
                        PrivilegeType.READ_ONLY))
                .getOrThrow();
    }

    @RequestMapping(method = RequestMethod.GET)
    public Collection<Institution> getAll(
            @RequestParam(name = Institution.FILTER_ATTR_ACTIVE, required = false) final Boolean active) {

        if (!this.authorizationGrantService.hasBasePrivilege(
                EntityType.INSTITUTION,
                PrivilegeType.READ_ONLY)) {

            // User has only institutional privilege, can see only the institution he/she belongs to
            return Arrays.asList(getOwn());
        } else {
            return this.institutionDAO.all(inst -> true, active).getOrThrow();
        }
    }

    @RequestMapping(path = "/names", method = RequestMethod.GET)
    public Collection<EntityKeyAndName> getNames(
            @RequestParam(name = Institution.FILTER_ATTR_ACTIVE, required = false) final Boolean active) {

        return getAll(active)
                .stream()
                .map(Institution::toName)
                .collect(Collectors.toList());
    }

    @RequestMapping(path = "/create", method = RequestMethod.PUT)
    public Institution create(@Valid @RequestBody final Institution institution) {
        return save(institution, PrivilegeType.WRITE)
                .getOrThrow();
    }

    @RequestMapping(path = "/save", method = RequestMethod.POST)
    public Institution save(@Valid @RequestBody final Institution institution) {
        return save(institution, PrivilegeType.MODIFY)
                .getOrThrow();
    }

    @RequestMapping(path = "/{id}/activate", method = RequestMethod.POST)
    public EntityProcessingReport activate(@PathVariable final Long id) {
        return setActive(id, true)
                .getOrThrow();
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public EntityProcessingReport deactivate(@PathVariable final Long id) {
        return setActive(id, false)
                .getOrThrow();
    }

    @RequestMapping(path = "/{id}/delete", method = RequestMethod.DELETE)
    public EntityProcessingReport deleteUser(@PathVariable final Long id) {
        checkPrivilegeForInstitution(id, PrivilegeType.WRITE);

        return this.bulkActionService.createReport(new BulkAction(
                Type.DEACTIVATE,
                EntityType.INSTITUTION,
                new EntityKey(id, EntityType.INSTITUTION)))
                .getOrThrow();
    }

    @RequestMapping(path = "/{id}/hard-delete", method = RequestMethod.DELETE)
    public EntityProcessingReport hardDeleteUser(@PathVariable final Long id) {
        checkPrivilegeForInstitution(id, PrivilegeType.WRITE);

        return this.bulkActionService.createReport(new BulkAction(
                Type.HARD_DELETE,
                EntityType.INSTITUTION,
                new EntityKey(id, EntityType.INSTITUTION)))
                .getOrThrow();
    }

    private void checkPrivilegeForInstitution(final Long id, final PrivilegeType type) {
        this.authorizationGrantService.checkHasAnyPrivilege(
                EntityType.INSTITUTION,
                type);

        this.institutionDAO.byPK(id)
                .flatMap(institution -> this.authorizationGrantService.checkGrantOnEntity(
                        institution,
                        type))
                .getOrThrow();
    }

    private Result<EntityProcessingReport> setActive(final Long id, final boolean active) {
        checkPrivilegeForInstitution(id, PrivilegeType.MODIFY);

        return this.bulkActionService.createReport(new BulkAction(
                (active) ? Type.ACTIVATE : Type.DEACTIVATE,
                EntityType.INSTITUTION,
                new EntityKey(id, EntityType.INSTITUTION)));

    }

    private Result<Institution> save(final Institution institution, final PrivilegeType privilegeType) {

        final ActivityType activityType = (institution.id == null)
                ? ActivityType.CREATE
                : ActivityType.MODIFY;

        return this.authorizationGrantService
                .checkGrantOnEntity(institution, privilegeType)
                .flatMap(this.institutionDAO::save)
                .flatMap(inst -> this.userActivityLogDAO.log(activityType, inst));
    }

}
