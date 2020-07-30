/*
 * Copyright (c) 2020 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.gbl.model.exam;

import org.apache.commons.lang3.BooleanUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ch.ethz.seb.sebserver.gbl.api.EntityType;
import ch.ethz.seb.sebserver.gbl.model.Domain;
import ch.ethz.seb.sebserver.gbl.model.Entity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExamProctoring implements Entity {

    public enum ServerType {
        JITSI_MEET
    }

    public static final String ATTR_ENABLE_PROCTORING = "enableProctoring";
    public static final String ATTR_SERVER_TYPE = "serverType";
    public static final String ATTR_SERVER_URL = "serverURL";
    public static final String ATTR_APP_KEY = "appKey";
    public static final String ATTR_APP_SECRET = "appSecret";

    @JsonProperty(Domain.EXAM.ATTR_ID)
    public final Long examId;
    @JsonProperty(ATTR_ENABLE_PROCTORING)
    public final Boolean enableProctoring;
    @JsonProperty(ATTR_SERVER_TYPE)
    public final ServerType serverType;
    @JsonProperty(ATTR_SERVER_URL)
    public final String serverURL;
    @JsonProperty(ATTR_APP_KEY)
    public final String appKey;
    @JsonProperty(ATTR_APP_SECRET)
    public final CharSequence appSecret;

    @JsonCreator
    public ExamProctoring(
            @JsonProperty(Domain.EXAM.ATTR_ID) final Long examId,
            @JsonProperty(ATTR_ENABLE_PROCTORING) final Boolean enableProctoring,
            @JsonProperty(ATTR_SERVER_TYPE) final ServerType serverType,
            @JsonProperty(ATTR_SERVER_URL) final String serverURL,
            @JsonProperty(ATTR_APP_KEY) final String appKey,
            @JsonProperty(ATTR_APP_SECRET) final CharSequence appSecret) {

        this.examId = examId;
        this.enableProctoring = BooleanUtils.isTrue(enableProctoring);
        this.serverType = (serverType != null) ? serverType : ServerType.JITSI_MEET;
        this.serverURL = serverURL;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String getModelId() {
        return (this.examId != null) ? String.valueOf(this.examId) : null;
    }

    @Override
    public EntityType entityType() {
        return EntityType.EXAM_PROCTOR_DATA;
    }

    @Override
    public String getName() {
        return this.serverType.name() + " " + this.serverURL;
    }

    public Long getExamId() {
        return this.examId;
    }

    public Boolean getEnableProctoring() {
        return this.enableProctoring;
    }

    public ServerType getServerType() {
        return this.serverType;
    }

    public String getServerURL() {
        return this.serverURL;
    }

    public String getAppKey() {
        return this.appKey;
    }

    public CharSequence getAppSecret() {
        return this.appSecret;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.examId == null) ? 0 : this.examId.hashCode());
        result = prime * result + ((this.serverType == null) ? 0 : this.serverType.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final ExamProctoring other = (ExamProctoring) obj;
        if (this.examId == null) {
            if (other.examId != null)
                return false;
        } else if (!this.examId.equals(other.examId))
            return false;
        if (this.serverType != other.serverType)
            return false;
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ExamProctoring [examId=");
        builder.append(this.examId);
        builder.append(", enableProctoring=");
        builder.append(this.enableProctoring);
        builder.append(", serverType=");
        builder.append(this.serverType);
        builder.append(", serverURL=");
        builder.append(this.serverURL);
        builder.append(", appKey=");
        builder.append(this.appKey);
        builder.append(", appSecret=");
        builder.append("--");
        builder.append("]");
        return builder.toString();
    }

}
