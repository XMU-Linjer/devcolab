package com.devcollab.knowledgecore.document.application.exception;

public class DocumentParentCycleException extends RuntimeException {

    public DocumentParentCycleException() {
        super("不能将文档移动到自身或自己的子文档下");
    }
}
