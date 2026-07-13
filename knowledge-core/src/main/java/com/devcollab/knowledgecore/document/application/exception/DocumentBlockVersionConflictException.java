package com.devcollab.knowledgecore.document.application.exception;

public class DocumentBlockVersionConflictException extends RuntimeException {

    public DocumentBlockVersionConflictException() {
        super("Document block has been changed by another request");
    }
}
