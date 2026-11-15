package com.navan.travel.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class TransientFailureRetry {

    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MILLIS = 25;

    public <T> T run(Supplier<T> unitOfWork) {
        for (int attempt = 1; ; attempt++) {
            try {
                return unitOfWork.get();
            } catch (TransientDataAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                pause(attempt);
            }
        }
    }

    private void pause(int attempt) {
        long ceiling = BASE_BACKOFF_MILLIS << attempt;
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MILLIS, ceiling));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}
