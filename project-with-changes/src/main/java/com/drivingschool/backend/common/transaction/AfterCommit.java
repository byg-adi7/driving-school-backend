package com.drivingschool.backend.common.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a best-effort side effect only once the current transaction has committed - never
 * for work that rolled back - and never lets it fail the caller: an exception is logged.
 * Outside a transaction it runs straight away.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(String description, Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (Exception ex) {
                log.warn("After-commit action failed: {}", description, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
            return;
        }
        guarded.run();
    }
}
