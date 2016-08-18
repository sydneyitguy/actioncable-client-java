package com.hosopy.actioncable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectionMonitor {

    private static final int STALE_THRESHOLD = 6; // Server::Connections::BEAT_INTERVAL * 2 (missed two pings)

    private final Connection connection;

    private final boolean reconnection;

    private final int reconnectionMaxAttempts;

    private final int reconnectionDelay;

    private final int reconnectionDelayMax;

    private long pingedAt = 0; // milliseconds

    private long disconnectedAt = 0; // milliseconds

    private long startedAt = 0; // milliseconds

    private long stoppedAt = 0; // milliseconds

    private int reconnectAttempts = 0;
    private ScheduledExecutorService pollingTask;

    /*package*/ ConnectionMonitor(Connection connection, Connection.Options options) {
        this.connection = connection;

        this.reconnection = options.reconnection;
        this.reconnectionMaxAttempts = options.reconnectionMaxAttempts;
        this.reconnectionDelay = options.reconnectionDelay;
        this.reconnectionDelayMax = options.reconnectionDelayMax;
    }

    /*package*/ void recordConnect() {
        reset();
        pingedAt = now();
        disconnectedAt = 0;
    }

    /*package*/ void recordDisconnect() {
        disconnectedAt = now();
    }

    /*package*/ void recordPing() {
        pingedAt = now();
    }

    /*package*/ void start() {
        reset();
        stoppedAt = 0;
        startedAt = now();
        poll();
    }

    /*package*/ void stop() {
        stoppedAt = now();
    }

    private void reset() {
        reconnectAttempts = 0;
    }

    private void poll() {
        pollingTask = Executors.newSingleThreadScheduledExecutor();
        pollingTask.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (stoppedAt == 0) {
                    reconnectIfStale();
                }
            }
        }, 0, getInterval(), TimeUnit.MILLISECONDS);
    }

    private void reconnectIfStale() {
        if (reconnection && connectionIsStale() && reconnectAttempts < reconnectionMaxAttempts) {
            reconnectAttempts++;
            if (!disconnectedRecently()) {
                connection.reopen();
            }
        }
    }

    private boolean connectionIsStale() {
        return secondsSince(pingedAt > 0 ? pingedAt : startedAt) > STALE_THRESHOLD;
    }

    private boolean disconnectedRecently() {
        return disconnectedAt != 0 && secondsSince(disconnectedAt) < STALE_THRESHOLD;
    }

    private long getInterval() {
        final double interval = 5.0d * Math.log(reconnectAttempts + 1);
        return (long) clamp(interval, reconnectionDelay, reconnectionDelayMax) * 1000;
    }

    private static long secondsSince(long time) {
        return (now() - time) / 1000;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static double clamp(double number, int min, int max) {
        return Math.max(min, Math.min(max, number));
    }
}
