package com.maintainance;

import java.util.ArrayList;
import java.util.List;

final class MaintainanceCli {

    static final String DATABASE_FILE_PROPERTY = "maintainance.database-file";
    private static final String DATABASE_OPTION = "--database";

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
}
