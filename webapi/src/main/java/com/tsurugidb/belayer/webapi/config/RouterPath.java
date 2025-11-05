package com.tsurugidb.belayer.webapi.config;

import static com.tsurugidb.belayer.webapi.config.FunctionPermission.*;

public enum RouterPath {

    HEALTH_API("/api/hello",
            "/api/hello", P_NONE),
    AUTH_API("/api/auth",
            "/api/auth", P_NONE),
    AUTH_REFRESH_API("/api/refresh",
            "/api/refresh", P_NONE),
    UPLOAD_API("/api/upload",
            "/api/upload", P_UPLOAD),
    DOWNLOAD_API("/api/download/{filepath}",
            "/api/download/*", P_DOWNLOAD),
    DOWNLOADZIP_API("/api/downloadzip",
            "/api/downloadzip", P_DOWNLOAD),
    LIST_FILES_API("/api/dirlist/{dirpath}",
            "/api/dirlist/*", P_FILE_LIST),
    DELETE_FILE_API("/api/delete/file",
            "/api/delete/file", P_FILE_DIR_DELETE),
    DELETE_FILES_API("/api/delete/files",
            "/api/delete/files", P_FILE_DIR_DELETE),
    DELETE_DIR_API("/api/delete/dir",
            "/api/delete/dir", P_FILE_DIR_DELETE),
    BACKUP_START_API("/api/backup",
            "/api/backup", P_BACKUP),
    RESTORE_START_API("/api/restore",
            "/api/restore", P_RESTORE),
    BACKUP_STATUS_API("/api/br/status/{type}/{jobid}",
            "/api/br/status/**", P_BACKUP, P_RESTORE),
    LIST_BACKUP_STATUS_API("/api/br/list/{type}",
            "/api/br/list/*", P_BACKUP, P_RESTORE),
    CANCEL_BACKUP_RESTORE_API("/api/br/cancel/{type}/{jobid}",
            "/api/br/cancel/**", P_RESTORE),
    DUMP_START_API("/api/dump/{table}",
            "/api/dump/*", P_DUMP),
    LOAD_START_API("/api/load/{table}",
            "/api/load/*", P_LOAD),
    DUMP_LOAD_STATUS_API("/api/dumpload/status/{type}/{jobid}",
            "/api/dumpload/status/**", P_DUMP, P_LOAD),
    LIST_DUMP_LOAD_STATUS_API("/api/dumpload/list/{type}",
            "/api/dumpload/list/*", P_DUMP, P_LOAD),
    CANCEL_DUMP_LOAD_API("/api/dumpload/cancel/{type}/{jobid}",
            "/api/dumpload/cancel/**", P_DUMP, P_LOAD),
    START_TRANSACTION_API("/api/transaction/begin",
            "/api/transaction/begin", P_STREAM_API),
    FINISH_TRANSACTION_API("/api/transaction/{type}/{transactionid}",
            "/api/transaction/**", P_STREAM_API),
    SHOW_TRANSACTION_STATUS_API("/api/transaction/status/{transactionid}",
            "/api/transaction/status/*", P_STREAM_API),
    STREAM_DUMP_API("/api/transaction/dump/{transactionid}/{table_name}",
            "/api/transaction/dump/**", P_STREAM_API),
    STREAM_LOAD_API("/api/transaction/load/{transactionid}/{table_name}",
            "/api/transaction/load/**", P_STREAM_API),
    SHOW_SESSION_STATUS_API("/api/session/status/{session_id}",
            "/api/session/status/*", P_SESSION_CTL),
    SET_SESSION_VAR_API("/api/session/set",
            "/api/session/set", P_SESSION_CTL),
    KILL_SESSION_API("/api/session/kill",
            "/api/session/kill", P_SESSION_CTL),
    ENDPOINTS_API("/api/instance/list",
            "/api/instance/list", P_LIST_ENDPOINTS),
    START_DB_API("/api/db/start",
            "/api/db/start", P_DB_START),
    SHUTDOWN_DB_API("/api/db/shutdown",
            "/api/db/shutdown", P_DB_STOP),
    SHOW_DB_STATUS_API("/api/db/status",
            "/api/db/status", P_DB_STATUS),
    LIST_TABLE_NAMES_API("/api/db/tablenames",
            "/api/db/tablenames", P_TABLE_LIST),
    LIST_ROLES_API("/api/list/roles",
            "/api/list/roles", P_ROLE_EDIT),
    SHOW_ROLE_USER_MAPPING_API("/api/show/roleuser",
            "/api/show/roleuser", P_ROLE_EDIT),
    UPDATE_ROLE_USER_MAPPING_API("/api/update/roleuser",
            "/api/update/roleuser", P_ROLE_EDIT);

    private final String path;
    private final String pathMatch;
    private final FunctionPermission[] authorities;

    private RouterPath(String path, String pathMach, FunctionPermission... authorities) {
        this.path = path;
        this.pathMatch = pathMach;
        this.authorities = authorities;

    }

    public String getPath() {
        return path;
    }

    public String getPathMatch() {
        return pathMatch;
    }

    public FunctionPermission[] getAuthorities() {
        return authorities;
    }

}
