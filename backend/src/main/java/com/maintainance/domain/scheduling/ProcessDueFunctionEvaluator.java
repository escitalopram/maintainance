package com.maintainance.domain.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessDueFunctionEvaluator implements DueFunctionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ProcessDueFunctionEvaluator.class);

    private final long timeoutMs;

    public ProcessDueFunctionEvaluator(@Value("${maintainance.due-script-timeout-ms:5000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean isDue(String scriptPath, String scriptArgs) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return false;
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(scriptPath);
            if (scriptArgs != null && !scriptArgs.isBlank()) {
                command.add(scriptArgs);
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Due script timed out: {}", scriptPath);
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("Due script exit code {}: {}", process.exitValue(), scriptPath);
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.equalsIgnoreCase("true")) {
                        return true;
                    }
                    if (trimmed.equalsIgnoreCase("false")) {
                        return false;
                    }
                }
            }
            log.warn("Due script missing true/false line: {}", scriptPath);
            return false;
        } catch (Exception e) {
            log.warn("Due script failed: {}", scriptPath, e);
            return false;
        }
    }
}
