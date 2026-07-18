package com.devcollab.gateway.collaboration;

import java.util.UUID;

public interface CoreDocumentAccessVerifier {

    void verifyCanAccess(UUID documentId, String accessToken);
}
