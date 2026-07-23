package com.example.buildmyschema.entity;

import lombok.Data;

@Data
public class ColumnEntity {

    private String name;
    private String dataType;
    private boolean nullable = true;
    private boolean primaryKey;
    private boolean unique;
    private boolean autoIncrement;
    private String defaultValue;
}