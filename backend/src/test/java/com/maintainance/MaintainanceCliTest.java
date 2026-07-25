package com.maintainance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintainanceCliTest {

    @AfterEach
    void clearCliProperties() {
        System.clearProperty(MaintainanceCli.DATABASE_FILE_PROPERTY);
        System.clearProperty(MaintainanceCli.SERVER_PORT_PROPERTY);
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
    void applyOverrides_setsServerPortFromEqualsForm() {
        String[] remaining = MaintainanceCli.applyOverrides(new String[] {
                "--port=9090",
                "--database=/tmp/custom-db"
        });

        assertThat(System.getProperty(MaintainanceCli.SERVER_PORT_PROPERTY)).isEqualTo("9090");
        assertThat(System.getProperty(MaintainanceCli.DATABASE_FILE_PROPERTY)).isEqualTo("/tmp/custom-db");
        assertThat(remaining).isEmpty();
    }

    @Test
    void applyOverrides_setsServerPortFromSeparateArg() {
        String[] remaining = MaintainanceCli.applyOverrides(new String[] {
                "--port",
                "3000"
        });

        assertThat(System.getProperty(MaintainanceCli.SERVER_PORT_PROPERTY)).isEqualTo("3000");
        assertThat(remaining).isEmpty();
    }

    @Test
    void applyOverrides_rejectsMissingDatabasePath() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--database"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--database requires a path argument");
    }

    @Test
    void applyOverrides_rejectsBlankDatabasePath() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--database="}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--database requires a non-empty path");
    }

    @Test
    void applyOverrides_rejectsMissingPort() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--port"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--port requires a port number");
    }

    @Test
    void applyOverrides_rejectsInvalidPort() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--port=abc"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--port requires a valid integer port number");
    }

    @Test
    void applyOverrides_rejectsOutOfRangePort() {
        assertThatThrownBy(() -> MaintainanceCli.applyOverrides(new String[] {"--port=0"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--port must be between 1 and 65535");
    }
}
