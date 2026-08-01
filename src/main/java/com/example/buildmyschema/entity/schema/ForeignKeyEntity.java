package com.example.buildmyschema.entity.schema;

import lombok.Data;

@Data
public class ForeignKeyEntity {
    private String column;
    private String referencedTable;
    private String referencedColumn;
    private String onDelete;
    private String onUpdate;
}
