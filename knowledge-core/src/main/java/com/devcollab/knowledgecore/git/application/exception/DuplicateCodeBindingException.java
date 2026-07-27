package com.devcollab.knowledgecore.git.application.exception;

public class DuplicateCodeBindingException extends InvalidCodeBindingException {

    public DuplicateCodeBindingException() {
        super("该代码路径关联已存在");
    }
}
