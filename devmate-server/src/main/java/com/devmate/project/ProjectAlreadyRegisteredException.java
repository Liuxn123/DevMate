package com.devmate.project;

public class ProjectAlreadyRegisteredException extends RuntimeException {

    public ProjectAlreadyRegisteredException(String rootPath) {
        super("该路径已经登记：" + rootPath);
    }
}
