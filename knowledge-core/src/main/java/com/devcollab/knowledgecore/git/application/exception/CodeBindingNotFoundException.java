package com.devcollab.knowledgecore.git.application.exception;

public class CodeBindingNotFoundException extends InvalidCodeBindingException {

    public CodeBindingNotFoundException() {
        super("代码路径关联不存在");
    }
}
