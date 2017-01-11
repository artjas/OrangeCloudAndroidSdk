package com.orange.labs.sdk.exception;

/**
 * Created by Artur Jaszczyk on 11.01.2017.
 */

public class OrangeCloudOperationException extends Exception {
    public OrangeCloudOperationException() {
        super();
    }

    public OrangeCloudOperationException(Exception e) {
        super(e);
    }
}
