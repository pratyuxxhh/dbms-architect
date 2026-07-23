package com.example.buildmyschema.entity;

import lombok.Data;

@Data
public class RelationEntity {

    private String fromTable;
    private String fromColumn;

    private String toTable;
    private String toColumn;

    private RelationshipType type;
}
