package com.devmate.project;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String id) {
        super("没有找到项目：" + id);
    }
}
