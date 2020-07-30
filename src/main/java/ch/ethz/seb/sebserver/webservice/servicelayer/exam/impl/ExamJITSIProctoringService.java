/*
 * Copyright (c) 2020 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.exam.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.model.exam.Exam;
import ch.ethz.seb.sebserver.gbl.model.exam.ExamProctoring;
import ch.ethz.seb.sebserver.gbl.model.exam.ExamProctoring.ServerType;
import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.gbl.util.Utils;
import ch.ethz.seb.sebserver.webservice.servicelayer.exam.ExamProctoringService;
import ch.ethz.seb.sebserver.webservice.servicelayer.session.ExamSessionService;

@Lazy
@Service
@WebServiceProfile
public class ExamJITSIProctoringService implements ExamProctoringService {

    private static final String JITSI_ACCESS_TOKEN_HEADER =
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private static final String JITSI_ACCESS_TOKEN_PAYLOAD =
            "{\"context\":{\"user\":{\"name\":\"%s\"}},\"iss\":\"%s\",\"aud\":\"%s\",\"sub\":\"%s\",\"room\":\"%s\"%s}";

    private final ExamSessionService examSessionService;

    protected ExamJITSIProctoringService(final ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    @Override
    public ServerType getType() {
        return ServerType.JITSI_MEET;
    }

    @Override
    public Result<Boolean> testExamProctoring(final ExamProctoring examProctoring) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result<String> createProctoringURL(
            final ExamProctoring examProctoring,
            final String connectionToken,
            final boolean server) {

        return Result.tryCatch(() -> {
            return createProctoringURL(examProctoring,
                    this.examSessionService
                            .getConnectionData(connectionToken)
                            .getOrThrow().clientConnection,
                    server)
                            .getOrThrow();
        });
    }

    @Override
    public Result<String> createProctoringURL(
            final ExamProctoring examProctoring,
            final ClientConnection clientConnection,
            final boolean server) {

        return Result.tryCatch(() -> {

            if (examProctoring.examId == null) {
                throw new IllegalStateException("Missing exam identifier from ExamProctoring data");
            }

            long expTime = System.currentTimeMillis() + Constants.DAY_IN_MILLIS;
            if (this.examSessionService.isExamRunning(examProctoring.examId)) {
                final Exam exam = this.examSessionService.getRunningExam(examProctoring.examId)
                        .getOrThrow();
                expTime = exam.endTime.getMillis();
            }

            return createProctoringURL(
                    examProctoring.serverURL,
                    examProctoring.appKey,
                    examProctoring.getAppSecret(),
                    clientConnection.userSessionId,
                    (server) ? "seb-server" : "seb-client",
                    clientConnection.connectionToken,
                    expTime)
                            .getOrThrow();
        });

    }

    public Result<String> createProctoringURL(
            final String url,
            final String appKey,
            final CharSequence appSecret,
            final String clientName,
            final String clientKey,
            final String roomName,
            final Long expTime) {

        return Result.tryCatch(() -> {

            final Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

            final String host = UriComponentsBuilder.fromHttpUrl(url)
                    .build()
                    .getHost();

            final StringBuilder builder = new StringBuilder();
            builder.append(url)
                    .append("/")
                    .append(roomName)
                    .append("?jwt=");

            final String jwtHeaderPart = urlEncoder
                    .encodeToString(JITSI_ACCESS_TOKEN_HEADER.getBytes(StandardCharsets.UTF_8));
            final String jwtPayload = String.format(
                    JITSI_ACCESS_TOKEN_PAYLOAD.replaceAll(" ", "").replaceAll("\n", ""),
                    clientName,
                    appKey,
                    clientKey,
                    host,
                    roomName,
                    (expTime != null)
                            ? String.format(",\"exp\":%s", String.valueOf(expTime))
                            : "");
            final String jwtPayloadPart = urlEncoder
                    .encodeToString(jwtPayload.getBytes(StandardCharsets.UTF_8));
            final String message = jwtHeaderPart + "." + jwtPayloadPart;

            final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            final SecretKeySpec secret_key =
                    new SecretKeySpec(Utils.toByteArray(appSecret), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            final String hash = urlEncoder.encodeToString(sha256_HMAC.doFinal(Utils.toByteArray(message)));

            builder.append(message)
                    .append(".")
                    .append(hash);

            return builder.toString();
        });
    }

}
