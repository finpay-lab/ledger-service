package com.finpay.ledger.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostingStatusTest {

    @Test
    void posted_can_transition_to_reversed() {
        assertThat(PostingStatus.POSTED.canTransitionTo(PostingStatus.REVERSED)).isTrue();
    }

    @Test
    void reversed_is_terminal() {
        assertThat(PostingStatus.REVERSED.canTransitionTo(PostingStatus.POSTED)).isFalse();
        assertThat(PostingStatus.REVERSED.canTransitionTo(PostingStatus.REVERSED)).isFalse();
    }
}