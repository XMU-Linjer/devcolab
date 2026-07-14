package com.devcollab.knowledgecore.document.application.exception;

public class ReviewIssueNotFoundException extends RuntimeException {

    public ReviewIssueNotFoundException() {
        super("评审问题不存在");
    }
}
