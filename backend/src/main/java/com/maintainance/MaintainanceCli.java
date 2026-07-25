package com.maintainance;

import java.util.ArrayList;
import java.util.List;

final class MaintainanceCli {

    static final String DATABASE_FILE_PROPERTY = "maintainance.database-file";
    static final String SERVER_PORT_PROPERTY = "server.port";
    private static final String DATABASE_OPTION = "--database";
    private static final String PORT_OPTION = "--port";

    private MaintainanceCli() {
    }

    static String[] applyOverrides(String[] args) {
        List<String> remaining = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith(DATABASE_OPTION + "=")) {
                setDatabaseFile(arg.substring(DATABASE_OPTION.length() + 1));
            } else if (DATABASE_OPTION.equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(DATABASE_OPTION + " requires a path argument");
                }
                setDatabaseFile(args[++i]);
            } else if (arg.startsWith(PORT_OPTION + "=")) {
                setServerPort(arg.substring(PORT_OPTION.length() + 1));
            } else if (PORT_OPTION.equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(PORT_OPTION + " requires a port number");
                }
                setServerPort(args[++i]);
            } else {
                remaining.add(arg);
            }
        }
        return remaining.toArray(String[]::new);
    }

    private static void setDatabaseFile(String path) {
        if (path.isBlank()) {
            throw new IllegalArgumentException(DATABASE_OPTION + " requires a non-empty path");
        }
        System.setProperty(DATABASE_FILE_PROPERTY, path);
    }

    private static void setServerPort(String portValue) {
        if (portValue.isBlank()) {
            throw new IllegalArgumentException(PORT_OPTION + " requires a port number");
        }
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(PORT_OPTION + " requires a valid integer port number");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(PORT_OPTION + " must be between 1 and 65535");
        }
        System.setProperty(SERVER_PORT_PROPERTY, Integer.toString(port));
    }
}
