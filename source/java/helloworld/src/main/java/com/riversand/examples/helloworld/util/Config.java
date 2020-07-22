package com.riversand.examples.helloworld.util;

/**
 * Config class read the values from Environment variables
 */
public class Config {

    public static final String RDP_URL = System.getenv("RDP_URL");
    public static final String RDP_PORT = System.getenv("RDP_PORT");
    public static final String REQUEST_FILE_NAME = System.getenv("REQUEST_FILE_NAME");
    public static final String TENANT = System.getenv("TENANT");
    public static final String REQUEST_FILE_PATH = System.getenv("REQUEST_FILE_PATH");
    public static final String SOURCE_CONFIG_PATH = System.getenv("SOURCE_CONFIG_PATH");
    public static final String POD_ID = System.getenv("POD_ID");
    public static final String TASK_ID = System.getenv("TASK_ID");
    public static final String RDP_COMPLETE_URL = "http://" + RDP_URL + ":" + RDP_PORT + "/";
}
