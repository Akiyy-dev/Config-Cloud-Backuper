package com.naocraftlab.configbackuper.core;

/**
 * 关键性配置备份异常 - 继承 RuntimeException，表示不可恢复的错误
 */
public class CriticalConfigBackuperException extends RuntimeException {

    public CriticalConfigBackuperException(String message) {
        super(message);
    }

    public CriticalConfigBackuperException(String message, Throwable cause) {
        super(message, cause);
    }
}
