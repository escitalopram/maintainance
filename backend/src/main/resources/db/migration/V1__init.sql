CREATE TABLE settings (
    id              BIGINT PRIMARY KEY,
    soft_budget_minutes INT NOT NULL DEFAULT 120,
    hard_cap_minutes    INT NOT NULL DEFAULT 480,
    pain_threshold      DOUBLE NOT NULL DEFAULT 100,
    pain_per_minute_over_threshold DOUBLE NOT NULL DEFAULT 0.05,
    beta                DOUBLE NOT NULL DEFAULT 0.5,
    default_backlog_p   DOUBLE NOT NULL DEFAULT 0.6,
    planning_extend_factor INT NOT NULL DEFAULT 2
);

INSERT INTO settings (id) VALUES (1);

CREATE TABLE task (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    description         CLOB,
    archived            BOOLEAN NOT NULL DEFAULT FALSE,
    rules_json          CLOB NOT NULL,
    epoch_start         DATE,
    next_scheduled      DATE,
    last_missed_scheduled_at DATE,
    catch_up_count      INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);

CREATE TABLE open_instance (
    id                  UUID PRIMARY KEY,
    task_id             UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    scheduled_at        DATE NOT NULL,
    snooze_until        DATE,
    created_at          TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_open_instance_task ON open_instance(task_id);

CREATE TABLE completion (
    id                  UUID PRIMARY KEY,
    task_id             UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    scheduled_at        DATE NOT NULL,
    planned_at          DATE,
    completed_at        TIMESTAMP NOT NULL
);

CREATE INDEX ix_completion_task ON completion(task_id);

CREATE TABLE task_ordering_edge (
    id                  UUID PRIMARY KEY,
    before_task_id      UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    after_task_id       UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE
);
