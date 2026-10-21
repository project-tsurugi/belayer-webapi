package com.tsurugidb.belayer.webapi.config;

import static com.tsurugidb.belayer.webapi.config.FunctionPermission.*;

/**
 * Definition of the API endpoints.
 *
 * <p>
 * Each constant holds the path used for routing, the path pattern used for
 * authorization, and the permissions required to call the endpoint (any one of
 * them is sufficient).
 * </p>
 */
public enum RouterPath {

    /** Health check API. */
    HEALTH_API("/api/hello",
            "/api/hello", P_NONE),
    /** Authentication API (issues tokens). */
    AUTH_API("/api/auth",
            "/api/auth", P_NONE),
    /** Token refresh API. */
    AUTH_REFRESH_API("/api/refresh",
            "/api/refresh", P_NONE),
    /** File upload API. */
    UPLOAD_API("/api/upload",
            "/api/upload", P_UPLOAD),
    /** File download API. */
    DOWNLOAD_API("/api/download/{filepath}",
            "/api/download/*", P_DOWNLOAD),
    /** Bulk file download API (as a zip file). */
    DOWNLOADZIP_API("/api/downloadzip",
            "/api/downloadzip", P_DOWNLOAD),
    /** Directory listing API. */
    LIST_FILES_API("/api/dirlist/{dirpath}",
            "/api/dirlist/*", P_FILE_LIST),
    /** File deletion API. */
    DELETE_FILE_API("/api/delete/file",
            "/api/delete/file", P_FILE_DIR_DELETE),
    /** Multiple files deletion API. */
    DELETE_FILES_API("/api/delete/files",
            "/api/delete/files", P_FILE_DIR_DELETE),
    /** Directory deletion API. */
    DELETE_DIR_API("/api/delete/dir",
            "/api/delete/dir", P_FILE_DIR_DELETE),
    /** Backup start API. */
    BACKUP_START_API("/api/backup",
            "/api/backup", P_BACKUP),
    /** Restore start API. */
    RESTORE_START_API("/api/restore",
            "/api/restore", P_RESTORE),
    /** Backup/restore job status API. */
    BACKUP_STATUS_API("/api/br/status/{type}/{jobid}",
            "/api/br/status/**", P_BACKUP, P_RESTORE),
    /** Backup/restore job list API. */
    LIST_BACKUP_STATUS_API("/api/br/list/{type}",
            "/api/br/list/*", P_BACKUP, P_RESTORE),
    /** Backup/restore job cancel API. */
    CANCEL_BACKUP_RESTORE_API("/api/br/cancel/{type}/{jobid}",
            "/api/br/cancel/**", P_BACKUP, P_RESTORE),
    /** Dump start API. */
    DUMP_START_API("/api/dump/{table}",
            "/api/dump/*", P_DUMP),
    /** Load start API. */
    LOAD_START_API("/api/load/{table}",
            "/api/load/*", P_LOAD),
    /** Dump/load job status API. */
    DUMP_LOAD_STATUS_API("/api/dumpload/status/{type}/{jobid}",
            "/api/dumpload/status/**", P_DUMP, P_LOAD),
    /** Dump/load job list API. */
    LIST_DUMP_LOAD_STATUS_API("/api/dumpload/list/{type}",
            "/api/dumpload/list/*", P_DUMP, P_LOAD),
    /** Dump/load job cancel API. */
    CANCEL_DUMP_LOAD_API("/api/dumpload/cancel/{type}/{jobid}",
            "/api/dumpload/cancel/**", P_DUMP, P_LOAD),
    /** Transaction begin API (Stream API). */
    START_TRANSACTION_API("/api/transaction/begin",
            "/api/transaction/begin", P_STREAM_API),
    /** Transaction commit/rollback API (Stream API). */
    FINISH_TRANSACTION_API("/api/transaction/{type}/{transaction_id}",
            "/api/transaction/*/*", P_STREAM_API),
    /** Transaction status API (Stream API). */
    SHOW_TRANSACTION_STATUS_API("/api/transaction/status/{transaction_id}",
            "/api/transaction/status/*", P_STREAM_API),
    /** Stream dump API: dumps a table within a transaction. */
    STREAM_DUMP_API("/api/transaction/dump/{transaction_id}/{table_name}",
            "/api/transaction/dump/**", P_STREAM_API),
    /** Stream load API: loads data into a table within a transaction. */
    STREAM_LOAD_API("/api/transaction/load/{transaction_id}/{table_name}",
            "/api/transaction/load/**", P_STREAM_API),
    /** Session status API. */
    SHOW_SESSION_STATUS_API("/api/session/status/{session_id}",
            "/api/session/status/*", P_SESSION_CTL),
    /** Session variable setting API. */
    SET_SESSION_VAR_API("/api/session/set",
            "/api/session/set", P_SESSION_CTL),
    /** Session kill API. */
    KILL_SESSION_API("/api/session/kill",
            "/api/session/kill", P_SESSION_CTL),
    /** Endpoint list API. */
    ENDPOINTS_API("/api/instance/list",
            "/api/instance/list", P_LIST_ENDPOINTS),
    /** Database start API. */
    START_DB_API("/api/db/start",
            "/api/db/start", P_DB_START),
    /** Database shutdown API. */
    SHUTDOWN_DB_API("/api/db/shutdown",
            "/api/db/shutdown", P_DB_STOP),
    /** Database mode change API. */
    CHANGE_DB_MODE_API("/api/db/changemode",
            "/api/db/changemode", P_DB_CHANGE_MODE),
    /** Database sync API (instructs the database to synchronize its WAL). */
    SYNC_TRAN_LOG_API("/api/db/sync",
            "/api/db/sync", P_DB_SYNC),
    /** Database status API. */
    SHOW_DB_STATUS_API("/api/db/status",
            "/api/db/status", P_DB_STATUS),
    /** Table name list API. */
    LIST_TABLE_NAMES_API("/api/db/tablenames",
            "/api/db/tablenames", P_TABLE_LIST),
    /** Role definition list API. */
    LIST_ROLES_API("/api/list/roles",
            "/api/list/roles", P_ROLE_EDIT),
    /** Role-user mapping show API. */
    SHOW_ROLE_USER_MAPPING_API("/api/show/roleuser",
            "/api/show/roleuser", P_ROLE_EDIT),
    /** Role-user mapping update API. */
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

    /**
     * Returns the path used for routing.
     *
     * @return path (may contain path variables such as {@code {jobid}})
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the path pattern used for authorization.
     *
     * @return path pattern (may contain wildcards)
     */
    public String getPathMatch() {
        return pathMatch;
    }

    /**
     * Returns the permissions required to call this endpoint.
     * Any one of them is sufficient.
     *
     * @return required permissions
     */
    public FunctionPermission[] getAuthorities() {
        return authorities;
    }

}
