package cz.trinera.anakon.dtd_executor;

public enum ProcessState {
    CREATED, //never set to this by executor, only by anakon_backend
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED, // Process was cancelled by user
}
