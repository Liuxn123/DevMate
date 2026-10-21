package com.devmate.project.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String id) {
        super("没有找到项目：" + id);
    }
}
