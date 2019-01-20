/*
 * Copyright (c) 2018 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.integration.api;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.test.context.jdbc.Sql;

import com.fasterxml.jackson.core.type.TypeReference;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.model.user.UserActivityLog;
import ch.ethz.seb.sebserver.webservice.weblayer.api.RestAPI;

@Sql(scripts = { "classpath:schema-test.sql", "classpath:data-test.sql" })
public class UserActivityLogAPITest extends AdministrationAPIIntegrationTest {

    @Test
    public void getAllAsSEBAdmin() throws Exception {
        final String token = getSebAdminAccess();
        final List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG)
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());
    }

    @Test
    public void getAllAsSEBAdminForUser() throws Exception {
        final String token = getSebAdminAccess();
        // for a user in another institution, the institution has to be defined
        List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "/user4?institution=2")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());

        // for a user in the same institution no institution is needed
        logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "/user2")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(1 == logs.size());
    }

    @Test
    public void getAllAsSEBAdminInTimeRange() throws Exception {
        final DateTime zeroDate = DateTime.parse("1970-01-01 00:00:00", Constants.DATE_TIME_PATTERN_UTC_NO_MILLIS);
        assertEquals("0", String.valueOf(zeroDate.getMillis()));
        final String sec2 = zeroDate.plus(1000).toString(Constants.DATE_TIME_PATTERN_UTC_NO_MILLIS);
        final String sec4 = zeroDate.plus(4000).toString(Constants.DATE_TIME_PATTERN_UTC_NO_MILLIS);
        final String sec5 = zeroDate.plus(5000).toString(Constants.DATE_TIME_PATTERN_UTC_NO_MILLIS);
        final String sec6 = zeroDate.plus(6000).toString(Constants.DATE_TIME_PATTERN_UTC_NO_MILLIS);

        final String token = getSebAdminAccess();
        List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(
                        get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?institution=2&from=" + sec2)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(3 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?institution=2&from="
                                + sec2 + "&to=" + sec4)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(1 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?institution=2&from=" + sec2
                                + "&to=" + sec5)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?institution=2&from=" + sec2
                                + "&to=" + sec6)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(3 == logs.size());
    }

    @Test
    public void getAllAsSEBAdminForActivityType() throws Exception {
        final String token = getSebAdminAccess();
        List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?activity_types=CREATE")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(1 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(
                                get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG
                                        + "?activity_types=CREATE,MODIFY")
                                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());

        // for other institution (2)
        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(
                                get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG
                                        + "?institution=2&activity_types=CREATE,MODIFY")
                                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(3 == logs.size());
    }

    @Test
    public void getAllAsSEBAdminForEntityType() throws Exception {
        final String token = getSebAdminAccess();
        List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "?entity_types=INSTITUTION")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(1 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(
                                get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG
                                        + "?entity_types=INSTITUTION,EXAM")
                                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());

        logs = this.jsonMapper.readValue(
                this.mockMvc
                        .perform(
                                get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG
                                        + "?entity_types=INSTITUTION,EXAM&institution=2")
                                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(3 == logs.size());
    }

    @Test
    public void getAllAsInstitutionalAdmin() throws Exception {
        final String token = getAdminInstitution1Access();
        final List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG)
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(2 == logs.size());
    }

    @Test
    public void getNoPermission() throws Exception {
        String token = getExamAdmin1();

        // no privilege at all
        this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        // no privilege at all
        this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "/user4")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // no privilege to query logs of users of other institution
        token = getAdminInstitution1Access();
        final List<UserActivityLog> logs = this.jsonMapper.readValue(
                this.mockMvc.perform(get(this.endpoint + RestAPI.ENDPOINT_USER_ACTIVITY_LOG + "/user4")
                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<UserActivityLog>>() {
                });

        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

}
