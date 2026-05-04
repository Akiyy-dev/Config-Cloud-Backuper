package com.naocraftlab.configbackuper.util;

/**
 * 日志包装器接口 - 用于抽象不同平台的日志实现
 */
public interface LoggerWrapper {

    void info(String message);

    void info(String message, Object... args);

    void warn(String message);

    void warn(String message, Object... args);

    void error(String message);

    void error(String message, Object... args);

    void error(String message, Throwable throwable);
}
