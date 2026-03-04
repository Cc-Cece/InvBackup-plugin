package com.invbackup.request;

public class RestoreRequest {

    public String requestId;
    public String targetUuid;
    public String targetName;
    public String snapshotId;
    public String requestedBy;
    public String requestedByUuid;
    public long timestamp;
    public String status; // pending, accepted, declined, expired

    public RestoreRequest() {
    }

    public RestoreRequest(String targetUuid, String targetName,
                          String snapshotId, String requestedBy,
                          String requestedByUuid) {
        this.requestId = System.currentTimeMillis() + "_" + requestedByUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.snapshotId = snapshotId;
        this.requestedBy = requestedBy;
        this.requestedByUuid = requestedByUuid;
        this.timestamp = System.currentTimeMillis();
        this.status = "pending";
    }
}
