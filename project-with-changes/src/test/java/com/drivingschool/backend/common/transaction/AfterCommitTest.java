package com.drivingschool.backend.common.transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AfterCommitTest {

    private final List<String> ran = new ArrayList<>();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insideATransaction_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run("test", () -> ran.add("points"));
        assertThat(ran).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(ran).containsExactly("points");
    }

    @Test
    void whenTheTransactionRollsBack_nothingRuns() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run("test", () -> ran.add("points"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(ran).isEmpty();
    }

    @Test
    void outsideATransaction_runsNow() {
        AfterCommit.run("test", () -> ran.add("points"));

        assertThat(ran).containsExactly("points");
    }

    @Test
    void aFailingAction_isLoggedNeverThrown() {
        assertThatCode(() -> AfterCommit.run("test", () -> {
            throw new IllegalStateException("gamification down");
        })).doesNotThrowAnyException();
    }
}
