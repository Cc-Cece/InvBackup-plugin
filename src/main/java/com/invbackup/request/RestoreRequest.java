package com.invbackup.request;

public class RestoreRequest {

    public String requestId;
    public String targetUuid;
    public String targetName;
    // UUID/name of the backup owner; for most cases this is the same
    // as targetUuid, but allows admins to queue a backup taken from A
    // to be restored by B.
    public String sourceUuid;
    public String sourceName;
    public String snapshotId;
    public String requestedBy;
    public String requestedByUuid;
    public long timestamp;
    public String status; // pending, accepted, declined, expired
    // Timestamp until which RestoreGui can be reopened after accepting
    // (0 = only first open is allowed)
    public long openExpiredAt;

    public RestoreRequest() {
    }

    public RestoreRequest(String sourceUuid, String sourceName,
                          String targetUuid, String targetName,
                          String snapshotId, String requestedBy,
                          String requestedByUuid) {
        this.requestId = System.currentTimeMillis() + "_" + requestedByUuid;
        this.sourceUuid = sourceUuid;
        this.sourceName = sourceName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.snapshotId = snapshotId;
        this.requestedBy = requestedBy;
        this.requestedByUuid = requestedByUuid;
        this.timestamp = System.currentTimeMillis();
        this.status = "pending";
        this.openExpiredAt = 0L;
    }
}
