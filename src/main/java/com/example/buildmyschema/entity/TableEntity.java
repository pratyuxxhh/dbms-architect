package com.example.buildmyschema.entity;

import lombok.Data;

import java.util.List;

@Data
public class TableEntity {
    private String name;

    private List<ColumnEntity> columns;

    private List<ForeignKeyEntity> foreignKeys;
}
