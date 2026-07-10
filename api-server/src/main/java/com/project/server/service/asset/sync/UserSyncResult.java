package com.project.server.service.asset.sync;

public record UserSyncResult(boolean success, boolean skipped, String reason) {

    public static UserSyncResult succeeded() {
        return new UserSyncResult(true, false, null);
    }

    public static UserSyncResult failed(String reason) {
        return new UserSyncResult(false, false, reason);
    }

    public static UserSyncResult skipped(String reason) {
        return new UserSyncResult(false, true, reason);
    }
}
