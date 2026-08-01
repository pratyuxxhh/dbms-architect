package com.example.buildmyschema.entity.schema;

import lombok.Data;

import java.util.List;

@Data
public class ResponseEntityy {
    private String schemaName;
    private List<QueryEntity> queries;
    private List<SchemaEntity> schemas;
}
