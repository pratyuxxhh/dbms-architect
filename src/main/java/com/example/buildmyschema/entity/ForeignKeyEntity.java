package com.example.buildmyschema.entity;

import lombok.Data;

@Data
public class ForeignKeyEntity {
    private String column;
    private String referencedTable;
    private String referencedColumn;
    private String onDelete;
    private String onUpdate;
}
