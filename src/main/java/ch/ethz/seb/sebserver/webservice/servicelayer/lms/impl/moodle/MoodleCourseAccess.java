/*
 * Copyright (c) 2020 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.lms.impl.moodle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.api.JSONMapper;
import ch.ethz.seb.sebserver.gbl.async.AsyncService;
import ch.ethz.seb.sebserver.gbl.model.exam.Chapters;
import ch.ethz.seb.sebserver.gbl.model.exam.QuizData;
import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetup;
import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetupTestResult;
import ch.ethz.seb.sebserver.gbl.model.user.ExamineeAccountDetails;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.gbl.util.Utils;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.impl.CourseAccess;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.impl.moodle.MoodleRestTemplateFactory.MoodleAPIRestTemplate;

/** Implements the LmsAPITemplate for Open edX LMS Course API access.
 *
 * See also: https://docs.moodle.org/dev/Web_service_API_functions */
public class MoodleCourseAccess extends CourseAccess {

    private static final Logger log = LoggerFactory.getLogger(MoodleCourseAccess.class);

    private static final String MOODLE_QUIZ_START_URL_PATH = "mod/quiz/view.php?id=";
    private static final String MOODLE_COURSE_API_FUNCTION_NAME = "core_course_get_courses";
    private static final String MOODLE_USER_PROFILE_API_FUNCTION_NAME = "core_user_get_users_by_field";
    private static final String MOODLE_QUIZ_API_FUNCTION_NAME = "mod_quiz_get_quizzes_by_courses";
    private static final String MOODLE_COURSE_API_COURSE_IDS = "courseids";

    private final JSONMapper jsonMapper;
    private final LmsSetup lmsSetup;
    private final MoodleRestTemplateFactory moodleRestTemplateFactory;

    private MoodleAPIRestTemplate restTemplate;

    protected MoodleCourseAccess(
            final JSONMapper jsonMapper,
            final LmsSetup lmsSetup,
            final MoodleRestTemplateFactory moodleRestTemplateFactory,
            final AsyncService asyncService) {

        super(asyncService);
        this.jsonMapper = jsonMapper;
        this.lmsSetup = lmsSetup;
        this.moodleRestTemplateFactory = moodleRestTemplateFactory;
    }

    @Override
    public Result<ExamineeAccountDetails> getExamineeAccountDetails(final String examineeSessionId) {
        return Result.tryCatch(() -> {
            final MoodleAPIRestTemplate template = getRestTemplate()
                    .getOrThrow();

            final MultiValueMap<String, String> queryAttributes = new LinkedMultiValueMap<>();
            queryAttributes.add("field", "id");
            queryAttributes.add("values[0]", examineeSessionId);

            final String userDetailsJSON = template.callMoodleAPIFunction(
                    MOODLE_USER_PROFILE_API_FUNCTION_NAME,
                    queryAttributes);

            final MoodleUserDetails[] userDetails = this.jsonMapper.<MoodleUserDetails[]> readValue(
                    userDetailsJSON,
                    new TypeReference<MoodleUserDetails[]>() {
                    });

            if (userDetails == null || userDetails.length <= 0) {
                throw new RuntimeException("No user details on Moodle API request");
            }

            final Map<String, String> additionalAttributes = new HashMap<>();
            additionalAttributes.put("firstname", userDetails[0].firstname);
            additionalAttributes.put("lastname", userDetails[0].lastname);
            additionalAttributes.put("department", userDetails[0].department);
            additionalAttributes.put("firstaccess", String.valueOf(userDetails[0].firstaccess));
            additionalAttributes.put("lastaccess", String.valueOf(userDetails[0].lastaccess));
            additionalAttributes.put("auth", userDetails[0].auth);
            additionalAttributes.put("suspended", String.valueOf(userDetails[0].suspended));
            additionalAttributes.put("confirmed", String.valueOf(userDetails[0].confirmed));
            additionalAttributes.put("lang", userDetails[0].lang);
            additionalAttributes.put("theme", userDetails[0].theme);
            additionalAttributes.put("timezone", userDetails[0].timezone);
            additionalAttributes.put("description", userDetails[0].description);
            additionalAttributes.put("mailformat", String.valueOf(userDetails[0].mailformat));
            additionalAttributes.put("descriptionformat", String.valueOf(userDetails[0].descriptionformat));
            return new ExamineeAccountDetails(
                    userDetails[0].id,
                    userDetails[0].fullname,
                    userDetails[0].username,
                    userDetails[0].email,
                    additionalAttributes);
        });
    }

    LmsSetupTestResult initAPIAccess() {

        final LmsSetupTestResult attributesCheck = this.moodleRestTemplateFactory.test();
        if (!attributesCheck.isOk()) {
            return attributesCheck;
        }

        final Result<MoodleAPIRestTemplate> restTemplateRequest = getRestTemplate();
        if (restTemplateRequest.hasError()) {
            final String message = "Failed to gain access token from Moodle Rest API:\n tried token endpoints: " +
                    this.moodleRestTemplateFactory.knownTokenAccessPaths;
            log.error(message, restTemplateRequest.getError().getMessage());
            return LmsSetupTestResult.ofTokenRequestError(message);
        }

        final MoodleAPIRestTemplate restTemplate = restTemplateRequest.get();

        try {
            restTemplate.testAPIConnection(
                    MOODLE_COURSE_API_FUNCTION_NAME,
                    MOODLE_QUIZ_API_FUNCTION_NAME);
        } catch (final RuntimeException e) {
            log.error("Failed to access Moodle course API: ", e);
            return LmsSetupTestResult.ofQuizAccessAPIError(e.getMessage());
        }

        return LmsSetupTestResult.ofOkay();
    }

    @Override
    protected Supplier<List<QuizData>> allQuizzesSupplier() {
        return () -> getRestTemplate()
                .map(this::collectAllQuizzes)
                .getOrThrow();
    }

    @Override
    protected Supplier<Chapters> getCourseChaptersSupplier(final String courseId) {
        throw new UnsupportedOperationException("not available yet");
    }

    private ArrayList<QuizData> collectAllQuizzes(final MoodleAPIRestTemplate restTemplate) {
        final String urlPrefix = (this.lmsSetup.lmsApiUrl.endsWith(Constants.URL_PATH_SEPARATOR))
                ? this.lmsSetup.lmsApiUrl + MOODLE_QUIZ_START_URL_PATH
                : this.lmsSetup.lmsApiUrl + Constants.URL_PATH_SEPARATOR + MOODLE_QUIZ_START_URL_PATH;
        return collectAllCourses(restTemplate)
                .stream()
                .reduce(
                        new ArrayList<>(),
                        (list, courseData) -> {
                            list.addAll(quizDataOf(
                                    this.lmsSetup,
                                    courseData,
                                    urlPrefix));
                            return list;
                        },
                        (list1, list2) -> {
                            list1.addAll(list2);
                            return list1;
                        });
    }

    private List<CourseData> collectAllCourses(final MoodleAPIRestTemplate restTemplate) {

        try {

            // first get courses from Moodle...
            final String coursesJSON = restTemplate.callMoodleAPIFunction(MOODLE_COURSE_API_FUNCTION_NAME);
            Map<String, CourseData> courseData = this.jsonMapper.<Collection<CourseData>> readValue(
                    coursesJSON,
                    new TypeReference<Collection<CourseData>>() {
                    })
                    .stream()
                    .collect(Collectors.toMap(d -> d.id, Function.identity()));

            if (courseData.size() > 100) {
                log.warn(
                        "Got more then 100 courses form Moodle: size {}. Trim it to the latest 100 courses",
                        courseData.size());
                final long nowInMillis = DateTime.now(DateTimeZone.UTC).getMillis();
                courseData = courseData.values().stream()
                        .filter(cd -> cd.end_date != null && cd.end_date.longValue() < nowInMillis)
                        .sorted((cd1, cd2) -> cd1.start_date.compareTo(cd2.start_date))
                        .limit(100)
                        .collect(Collectors.toMap(cd -> cd.id, Function.identity()));
            }

            // then get all quizzes of courses and filter
            final LinkedMultiValueMap<String, String> attributes = new LinkedMultiValueMap<>();
            attributes.put(MOODLE_COURSE_API_COURSE_IDS, new ArrayList<>(courseData.keySet()));

            final String quizzesJSON = restTemplate.callMoodleAPIFunction(
                    MOODLE_QUIZ_API_FUNCTION_NAME,
                    attributes);

            final CourseQuizData courseQuizData = this.jsonMapper.readValue(
                    quizzesJSON,
                    CourseQuizData.class);

            final Map<String, CourseData> finalCourseDataRef = courseData;
            courseQuizData.quizzes
                    .forEach(quiz -> {
                        final CourseData course = finalCourseDataRef.get(quiz.course);
                        if (course != null) {
                            course.quizzes.add(quiz);
                        }
                    });

            return courseData.values()
                    .stream()
                    .filter(c -> !c.quizzes.isEmpty())
                    .collect(Collectors.toList());
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected exception while trying to get course data: ", e);
        }
    }

    static Map<String, String> additionalAttrs = new HashMap<>();

    private List<QuizData> quizDataOf(
            final LmsSetup lmsSetup,
            final CourseData courseData,
            final String uriPrefix) {

        additionalAttrs.clear();
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_CREATION_TIME, String.valueOf(courseData.time_created));
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_SHORT_NAME, courseData.short_name);
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_ID_NUMBER, courseData.idnumber);
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_FULL_NAME, courseData.full_name);
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_DISPLAY_NAME, courseData.display_name);
        additionalAttrs.put(QuizData.ATTR_ADDITIONAL_SUMMARY, courseData.summary);

        final List<QuizData> courseAndQuiz = courseData.quizzes
                .stream()
                .map(courseQuizData -> {
                    final String startURI = uriPrefix + courseQuizData.course_module;
                    additionalAttrs.put(QuizData.ATTR_ADDITIONAL_TIME_LIMIT, String.valueOf(courseQuizData.time_limit));
                    return new QuizData(
                            getInternalQuizId(courseQuizData.course_module, courseData.short_name, courseData.idnumber),
                            lmsSetup.getInstitutionId(),
                            lmsSetup.id,
                            lmsSetup.getLmsType(),
                            courseQuizData.name,
                            courseQuizData.intro,
                            Utils.toDateTimeUTCUnix(courseData.start_date),
                            Utils.toDateTimeUTCUnix(courseData.end_date),
                            startURI,
                            additionalAttrs);
                })
                .collect(Collectors.toList());

        return courseAndQuiz;
    }

    private Result<MoodleAPIRestTemplate> getRestTemplate() {
        if (this.restTemplate == null) {
            final Result<MoodleAPIRestTemplate> templateRequest = this.moodleRestTemplateFactory
                    .createRestTemplate();
            if (templateRequest.hasError()) {
                return templateRequest;
            } else {
                this.restTemplate = templateRequest.get();
            }
        }

        return Result.of(this.restTemplate);
    }

    /** Maps the Moodle course API course data */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CourseData {
        final String id;
        final String short_name;
        final String idnumber;
        final String full_name;
        final String display_name;
        final String summary;
        final Long start_date; // unix-time milliseconds UTC
        final Long end_date; // unix-time milliseconds UTC
        final Long time_created; // unix-time milliseconds UTC
        final Collection<CourseQuiz> quizzes = new ArrayList<>();

        @JsonCreator
        protected CourseData(
                @JsonProperty(value = "id") final String id,
                @JsonProperty(value = "shortname") final String short_name,
                @JsonProperty(value = "idnumber") final String idnumber,
                @JsonProperty(value = "fullname") final String full_name,
                @JsonProperty(value = "displayname") final String display_name,
                @JsonProperty(value = "summary") final String summary,
                @JsonProperty(value = "startdate") final Long start_date,
                @JsonProperty(value = "enddate") final Long end_date,
                @JsonProperty(value = "timecreated") final Long time_created) {

            this.id = id;
            this.short_name = short_name;
            this.idnumber = idnumber;
            this.full_name = full_name;
            this.display_name = display_name;
            this.summary = summary;
            this.start_date = start_date;
            this.end_date = end_date;
            this.time_created = time_created;
        }

    }

    public static final String getInternalQuizId(final String quizId, final String shortname, final String idnumber) {
        final StringBuilder sb = new StringBuilder(quizId);
        if (StringUtils.isNotEmpty(shortname)) {
            sb.insert(0, ":").insert(0, shortname);
        }
        if (StringUtils.isNotEmpty(idnumber)) {
            sb.insert(0, ":").insert(0, idnumber);
        }
        return sb.toString();
    }

    public static final String getQuizId(final String internalQuizId) {
        if (StringUtils.isBlank(internalQuizId)) {
            return null;
        }

        final String[] ids = StringUtils.split(internalQuizId, Constants.COLON);
        return ids[ids.length - 1];
    }

    public static final String getShortname(final String internalQuizId) {
        if (StringUtils.isBlank(internalQuizId)) {
            return null;
        }

        final String[] ids = StringUtils.split(internalQuizId, Constants.COLON);
        if (ids.length == 3) {
            return ids[1];
        } else if (ids.length == 2) {
            return ids[0];
        } else {
            return null;
        }
    }

    public static final String getIdnumber(final String internalQuizId) {
        if (StringUtils.isBlank(internalQuizId)) {
            return null;
        }

        final String[] ids = StringUtils.split(internalQuizId, Constants.COLON);
        if (ids.length == 3) {
            return ids[0];
        } else {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CourseQuizData {
        final Collection<CourseQuiz> quizzes;

        @JsonCreator
        protected CourseQuizData(
                @JsonProperty(value = "quizzes") final Collection<CourseQuiz> quizzes) {
            this.quizzes = quizzes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CourseQuiz {
        final String id;
        final String course;
        final String course_module;
        final String name;
        final String intro; // HTML
        final Long time_limit; // unix-time milliseconds UTC

        @JsonCreator
        protected CourseQuiz(
                @JsonProperty(value = "id") final String id,
                @JsonProperty(value = "course") final String course,
                @JsonProperty(value = "coursemodule") final String course_module,
                @JsonProperty(value = "name") final String name,
                @JsonProperty(value = "intro") final String intro,
                @JsonProperty(value = "timelimit") final Long time_limit) {

            this.id = id;
            this.course = course;
            this.course_module = course_module;
            this.name = name;
            this.intro = intro;
            this.time_limit = time_limit;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class MoodleUserDetails {
        final String id;
        final String username;
        final String firstname;
        final String lastname;
        final String fullname;
        final String email;
        final String department;
        final Long firstaccess;
        final Long lastaccess;
        final String auth;
        final Boolean suspended;
        final Boolean confirmed;
        final String lang;
        final String theme;
        final String timezone;
        final String description;
        final Integer mailformat;
        final Integer descriptionformat;

        @JsonCreator
        protected MoodleUserDetails(
                @JsonProperty(value = "id") final String id,
                @JsonProperty(value = "username") final String username,
                @JsonProperty(value = "firstname") final String firstname,
                @JsonProperty(value = "lastname") final String lastname,
                @JsonProperty(value = "fullname") final String fullname,
                @JsonProperty(value = "email") final String email,
                @JsonProperty(value = "department") final String department,
                @JsonProperty(value = "firstaccess") final Long firstaccess,
                @JsonProperty(value = "lastaccess") final Long lastaccess,
                @JsonProperty(value = "auth") final String auth,
                @JsonProperty(value = "suspended") final Boolean suspended,
                @JsonProperty(value = "confirmed") final Boolean confirmed,
                @JsonProperty(value = "lang") final String lang,
                @JsonProperty(value = "theme") final String theme,
                @JsonProperty(value = "timezone") final String timezone,
                @JsonProperty(value = "description") final String description,
                @JsonProperty(value = "mailformat") final Integer mailformat,
                @JsonProperty(value = "descriptionformat") final Integer descriptionformat) {

            this.id = id;
            this.username = username;
            this.firstname = firstname;
            this.lastname = lastname;
            this.fullname = fullname;
            this.email = email;
            this.department = department;
            this.firstaccess = firstaccess;
            this.lastaccess = lastaccess;
            this.auth = auth;
            this.suspended = suspended;
            this.confirmed = confirmed;
            this.lang = lang;
            this.theme = theme;
            this.timezone = timezone;
            this.description = description;
            this.mailformat = mailformat;
            this.descriptionformat = descriptionformat;
        }
    }

}
