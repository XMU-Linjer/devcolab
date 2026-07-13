package com.devcollab.knowledgecore.document.application.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("文档不存在");
    }
}
