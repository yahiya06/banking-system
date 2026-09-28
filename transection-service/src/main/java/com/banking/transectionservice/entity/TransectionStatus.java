package com.banking.transectionservice.entity;

/**
 * Transection lifecycle
 * PENDING -> PROCESSING -> COMPLETED (clean transection)
 *                       -> PENDING_VERIFICATION (suspicious detected)
 *                                  -> COMPLETED (verified)
 *                                  -> FLAGGED (SAGS refund)
 *                        -> FAILED
 *                        -> FLAGGED
 */
public enum TransectionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PENDING_VERIFICATION,
    FAILED,
    FLAGGED
}
