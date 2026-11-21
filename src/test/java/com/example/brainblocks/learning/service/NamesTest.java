package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 2026-11-21 (security pass): names keep what's visible, lose what's hidden. */
class NamesTest {
    @Test
    void hiddenAndFlippingCharactersAreRemoved() {
        assertThat(Names.clean("דני‮תנש", 24, "השם")).isEqualTo("דניתנש");
        assertThat(Names.clean("  נועה\n\tכהן  ", 24, "השם")).isEqualTo("נועה כהן");
        assertThat(Names.clean("ש​יר", 24, "השם")).isEqualTo("שיר");
        assertThat(Names.clean("🦄🦄", 24, "השם")).isEqualTo("🦄🦄");
        assertThat(Names.clean("א".repeat(50), 24, "השם")).hasSize(24);
        assertThat(Names.clean("<b>x</b>", 24, "השם")).isEqualTo("<b>x</b>"); // shown as text by the page, never run
    }

    @Test
    void aNameMustKeepSomethingVisible() {
        assertThatThrownBy(() -> Names.clean("​​", 24, "השם")).hasMessageContaining("ריק");
        assertThatThrownBy(() -> Names.clean("‏ ‏", 24, "השם")).hasMessageContaining("ריק");
        assertThatThrownBy(() -> Names.clean(null, 24, "השם")).hasMessageContaining("ריק");
    }
}
