package com.devcollab.mcp.capability.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolSchemasTests {

    @Test
    @SuppressWarnings("unchecked")
    void bindingListBatchOutputAcceptsDocumentTitleReturnedByTheService() {
        Map<String, Object> schema = McpToolSchemas.bindingListBatchOutput();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> files = (Map<String, Object>) properties.get("files");
        Map<String, Object> file = (Map<String, Object>) files.get("items");
        Map<String, Object> fileProperties =
                (Map<String, Object>) file.get("properties");
        Map<String, Object> bindings =
                (Map<String, Object>) fileProperties.get("bindings");
        Map<String, Object> binding =
                (Map<String, Object>) bindings.get("items");
        Map<String, Object> bindingProperties =
                (Map<String, Object>) binding.get("properties");

        assertThat(bindingProperties).containsKey("documentTitle");
        assertThat((List<String>) binding.get("required"))
                .contains("documentTitle");
    }
}
