package com.maintainance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintainanceCliTest {

    @AfterEach
    void clearDatabaseFileProperty() {
        System.clearProperty(MaintainanceCli.DATABASE_FILE_PROPERTY);
    }

    @Test
    void applyOverrides_setsDatabaseFileFromEqualsForm() {
        String[] remaining = MaintainanceCli.applyOverrides(new String[] {
                "--database=/tmp/custom-db",
                "--server.port=9090"
        });

        assertThat(System.getProperty(MaintainanceCli.DATABASE_FILE_PROPERTY)).isEqualTo("/tmp/custom-db");
        assertThat(remaining).containsExactly("--server.port=9090");
    }

    @Test
    void applyOverrides_setsDatabaseFileFromSeparateArg() {
        String[] remaining = MaintainanceCli.applyOverrides(new String[] {
                "--database",
                "/tmp/custom-db"
        });

        assertThat(System.getProperty(MaintainanceCli.DATABASE_FILE_PROPERTY)).isEqualTo("/tmp/custom-db");
        assertThat(remaining).isEmpty();
    }

    @Test
    void applyOverrides_rejectsMissingPath() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--database"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--database requires a path argument");
    }

    @Test
    void applyOverrides_rejectsBlankPath() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--database="}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--database requires a non-empty path");
    }
}
