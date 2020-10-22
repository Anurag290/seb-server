/*
 * Copyright (c) 2018 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.session.impl;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.model.exam.Indicator;
import ch.ethz.seb.sebserver.webservice.servicelayer.session.ClientIndicator;

public abstract class AbstractClientIndicator implements ClientIndicator {

    protected Long examId;
    protected Long connectionId;
    protected boolean cachingEnabled;
    protected String[] tags;

    protected double currentValue = Double.NaN;

    @Override
    public void init(
            final Indicator indicatorDefinition,
            final Long connectionId,
            final boolean cachingEnabled) {

        this.examId = (indicatorDefinition != null) ? indicatorDefinition.examId : null;
        this.connectionId = connectionId;
        this.cachingEnabled = cachingEnabled;
        if (indicatorDefinition == null || indicatorDefinition.tags == null) {
            this.tags = null;
        } else {
            this.tags = StringUtils.split(indicatorDefinition.tags, Constants.COMMA);
            for (int i = 0; i < this.tags.length; i++) {
                this.tags[i] = Constants.ANGLE_BRACE_OPEN + this.tags[i] + Constants.ANGLE_BRACE_CLOSE;
            }
        }

    }

    @Override
    public Long examId() {
        return this.examId;
    }

    @Override
    public Long connectionId() {
        return this.connectionId;
    }

    @Override
    public double getValue() {
        if (Double.isNaN(this.currentValue) || !this.cachingEnabled) {
            this.currentValue = computeValueAt(DateTime.now(DateTimeZone.UTC).getMillis());
        }

        return this.currentValue;
    }

}
