package com.devcollab.knowledgecore.document.application.exception;

public class InvalidDocumentBlockPositionException
        extends RuntimeException {

    public InvalidDocumentBlockPositionException() {
        super("Block 目标位置超出文档范围");
    }
}
