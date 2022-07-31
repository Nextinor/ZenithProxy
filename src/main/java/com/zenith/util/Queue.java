package com.zenith.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.exception.OutOfRangeException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.zenith.util.Constants.*;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.isNull;

public class Queue {
    private static final String apiUrl = "https://2bqueue.info/queue";
    private static final HttpClient httpClient = HttpClient.create()
            .secure()
            .baseUrl(apiUrl)
            .headers(h -> h.add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON));
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Duration refreshPeriod = Duration.of(CONFIG.server.queueStatusRefreshMinutes, MINUTES);
    private static QueueStatus queueStatus;
    private static ScheduledExecutorService refreshExecutorService = new ScheduledThreadPoolExecutor(1);
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    // for queue wait time estimation maths
    // shamelessly ripped from 2bored2wait
    private static final double[] QUEUE_PLACEMENT_DATA = ImmutableList.of(0, 93, 207, 231, 257, 412, 418, 486, 506, 550, 586, 666, 758, 789, 826).stream()
            .mapToDouble(i -> i)
            .toArray();
    private static final double[] QUEUE_FACTOR_DATA = ImmutableList.of(
            0.9996f,
            0.9998618838664679f, 0.9999220416881794f, 0.9999234240704379f,
            0.9999291667668093f, 0.9999410569845172f, 0.9999168965649361f,
            0.9999440195022513f, 0.9999262577896301f, 0.9999462301738332f,
            0.999938895110192f, 0.9999219189483673f, 0.9999473463335498f,
            0.9999337457796981f, 0.9999279556964097f).stream()
            .mapToDouble(i -> i)
            .toArray();
    private static final double CONSTANT_FACTOR = 250;

    static {
        refreshExecutorService.scheduleAtFixedRate(
                Queue::updateQueueStatus,
                0,
                refreshPeriod.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public static QueueStatus getQueueStatus() {
        if (isNull(queueStatus)) {
            updateQueueStatus();
        }
        return queueStatus;
    }

    private static void updateQueueStatus() {
        try {
            final String response = httpClient
                    .get()
                    .responseContent()
                    .aggregate()
                    .asString()
                    .block();
            queueStatus = mapper.readValue(response, QueueStatus.class);
        } catch (Exception e) {
            SERVER_LOG.error("Failed updating queue status", e);
            if (isNull(queueStatus)) {
                queueStatus = new QueueStatus(0, 0, 0, 0L, "");
            }
        }
    }

    // probably only valid for regular queue, prio seems to move a lot faster
    // returns double representing seconds until estimated queue completion time.
    public static double getQueueWait(final Integer queueLength, final Integer queuePos) {
        try {
            double value = LINEAR_INTERPOLATOR.interpolate(QUEUE_PLACEMENT_DATA, QUEUE_FACTOR_DATA).value(queueLength);
            return Math.log((new Integer(queueLength - queuePos).doubleValue() + CONSTANT_FACTOR)
                    / (queueLength.doubleValue() + CONSTANT_FACTOR))
                    /  Math.log(value);
        } catch (OutOfRangeException e) {
            CLIENT_LOG.warn(e);
            return 0;
        }
    }

    public static String getEtaStringFromSeconds(final double totalSeconds) {
        final int hour = (int)(totalSeconds / 3600);
        final int minutes = (int)((totalSeconds / 60) % 60);
        final int seconds = (int)(totalSeconds % 60);
        final String hourStr = hour >= 10 ? ""+hour : "0" + hour;
        final String minutesStr = minutes >= 10 ? ""+minutes : "0" + minutes;
        final String secondsStr = seconds >= 10 ? ""+seconds : "0" + seconds;
        return hourStr + ":" + minutesStr + ":" + secondsStr;
    }

    public static String getQueueEta(final Integer queueLength, final Integer queuePos) {
        return getEtaStringFromSeconds(getQueueWait(queueLength, queuePos));
    }
}
