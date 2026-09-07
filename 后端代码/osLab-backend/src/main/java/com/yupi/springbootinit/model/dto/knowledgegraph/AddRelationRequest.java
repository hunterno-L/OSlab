package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;
import org.neo4j.ogm.annotation.typeconversion.DateLong;

@Data
public class AddRelationRequest {
    private String fromName;
    private String toName;
    private String relationType;
}
