package com.configcloudbackuper.util;

import org.slf4j.Logger;

/**
 * 基于 SLF4J 的日志包装器实现
 */
public class LoggerWrapperSlf4j implements LoggerWrapper {

    private final Logger logger;

    public LoggerWrapperSlf4j(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Object... args) {
        logger.error(message, args);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
