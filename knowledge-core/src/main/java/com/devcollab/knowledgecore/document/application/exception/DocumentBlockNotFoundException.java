package com.devcollab.knowledgecore.document.application.exception;

public class DocumentBlockNotFoundException extends RuntimeException {

    public DocumentBlockNotFoundException() {
        super("文档 Block 不存在");
    }
}
