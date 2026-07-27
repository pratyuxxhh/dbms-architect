package com.example.buildmyschema.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VectorService {
    @Autowired
    private VectorStore vectorStore;

    public void addVectorDocuments() {

        List<Document> documents = List.of(

                new Document("""
        This assistant specializes exclusively in relational database schema generation.

        Supported tasks:
        - Generate SQL schema
        - Design relational databases
        - Design entities
        - Create tables
        - Generate ER models
        - Define relationships
        - Generate DDL
        - Create indexes
        - Add constraints
        - Normalize schemas
        - Generate seed data
        - Improve existing database schemas

        Unsupported tasks:
        - Programming
        - Mathematics
        - History
        - Politics
        - Sports
        - Movies
        - General knowledge
        """),
                new Document("""
        Database concepts include:
        Database
        Schema
        Table
        Column
        Row
        Entity
        Attribute
        Primary Key
        Foreign Key
        Composite Key
        Candidate Key
        Alternate Key
        Unique Key
        Check Constraint
        Default Constraint
        Index
        View
        Sequence
        Trigger
        Stored Procedure
        """),
                new Document("""
        Normalization rules.
        Apply First Normal Form.
        Apply Second Normal Form.
        Apply Third Normal Form.
        Apply Boyce Codd Normal Form when applicable.
        Remove duplicate data.
        Reduce redundancy.
        Eliminate insertion anomalies.
        Eliminate update anomalies.
        Eliminate deletion anomalies.
        """),
                new Document("""
        SQL generation rules.
        Always generate:
        CREATE SCHEMA
        CREATE TABLE
        PRIMARY KEY
        FOREIGN KEY
        NOT NULL
        UNIQUE
        CHECK
        DEFAULT
        CREATE INDEX
        INSERT seed data
        Output only valid SQL syntax.
        """),
                new Document("""
        Naming conventions.
        Use snake_case.
        Table names should be plural.
        Column names should be meaningful.
        Primary key column should be id.
        Foreign keys should use table_id format.
        Avoid reserved SQL keywords.
        """),
                new Document("""
        Relationship types.
        One to One
        One to Many
        Many to Many
        Recursive Relationships
        Junction tables should resolve many-to-many relationships.
        """),
                new Document("""
        Constraints.
        Primary Key
        Foreign Key
        Unique
        Not Null
        Check
        Default
        Auto Increment
        Identity
        """),
                new Document("""
        Supported SQL databases.
        PostgreSQL
        MySQL
        MariaDB
        Oracle
        SQL Server
        SQLite
        """),
                new Document("""
        Supported business domains.
        Restaurant
        Hospital
        Banking
        School
        College
        University
        Hotel
        Inventory
        Warehouse
        E-Commerce
        Food Delivery
        Ride Sharing
        Library
        CRM
        ERP
        Payroll
        HRMS
        Hospital Management
        Pharmacy
        Airline Reservation
        Hotel Booking
        Movie Ticket Booking
        Gym Management
        UPI Payment System
        """),
                new Document("""
        Restaurant schema usually contains:
        Restaurant
        Customer
        Employee
        Menu Item
        Category
        Order
        Order Item
        Payment
        Reservation
        Table
        Kitchen Order
        """),
                new Document("""
        Hospital schema usually contains:
        Patient
        Doctor
        Appointment
        Prescription
        Medicine
        Department
        Ward
        Billing
        Laboratory
        Medical Record
        """),
                new Document("""
        Banking schema usually contains:
        Customer
        Account
        Transaction
        Branch
        Loan
        Card
        Beneficiary
        UPI
        Payment
        Statement
        """),
                new Document("""
        Ecommerce schema usually contains:
        User
        Product
        Category
        Inventory
        Supplier
        Cart
        Cart Item
        Order
        Order Item
        Payment
        Shipment
        Review
        Wishlist
        """),
                new Document("""
        Common SQL data types.
        INTEGER
        BIGINT
        SMALLINT
        DECIMAL
        NUMERIC
        FLOAT
        DOUBLE
        VARCHAR
        CHAR
        TEXT
        BOOLEAN
        DATE
        TIME
        TIMESTAMP
        UUID
        JSON
        JSONB
        BLOB
        """),
                new Document("""
        Schema generation workflow.
        Read requirements.
        Identify entities.
        Identify attributes.
        Identify relationships.
        Determine cardinality.
        Select primary keys.
        Add foreign keys.
        Choose data types.
        Normalize schema.
        Add indexes.
        Add constraints.
        Generate SQL.
        """),
                new Document("Generate a SQL database schema based on my requirements."),
                new Document("Design a relational database for the following application."),
                new Document("Create the tables, primary keys and foreign keys for this system."),
                new Document("Generate an ER diagram and corresponding SQL DDL for this domain."),
                new Document("Normalize this database design and remove redundant data."),
                new Document("Add appropriate indexes and constraints to this schema."),
                new Document("Generate CREATE TABLE statements with proper relationships."),
                new Document("Improve and optimize this existing database schema."),
                new Document("Convert these requirements into a normalized relational schema."),
                new Document("Generate seed data / sample INSERT statements for this schema."),
                new Document("Add a new column and a foreign key constraint to this table."),
                new Document("Suggest a many-to-many relationship design using a junction table."),
                new Document("Review my schema and point out normalization issues."),
                new Document("Convert this schema from MySQL syntax to PostgreSQL syntax."),
                new Document("What primary key and indexing strategy should I use for this table?"),

                // ---------- Restaurant domain ----------
                new Document("Design a database schema for a restaurant management system with menu items, orders, and payments."),
                new Document("Create tables for a restaurant app that handles reservations, tables, and kitchen orders."),
                new Document("I need a schema for a food ordering system with customers, menu categories, and order items."),

                // ---------- Hospital / healthcare domain ----------
                new Document("Design a hospital management database with patients, doctors, appointments, and prescriptions."),
                new Document("Create a schema for a pharmacy system tracking medicines, suppliers, and billing."),
                new Document("I need tables for a clinic system with departments, wards, and medical records."),

                // ---------- Banking / fintech domain ----------
                new Document("Design a banking database schema with accounts, transactions, branches, and loans."),
                new Document("Create a schema for a UPI payment system with beneficiaries and transaction history."),
                new Document("I need tables for a credit card and billing system with statements and payments."),

                // ---------- E-commerce domain ----------
                new Document("Design an e-commerce database with products, categories, cart, orders, and payments."),
                new Document("Create a schema for an online store with inventory, suppliers, shipments, and reviews."),
                new Document("I need tables for a wishlist and cart feature linked to users and products."),

                // ---------- Education domain ----------
                new Document("Design a school management schema with students, teachers, classes, and attendance."),
                new Document("Create a database for a college with courses, enrollments, grades, and departments."),
                new Document("I need a library management schema with books, members, and borrowing records."),

                // ---------- Hospitality / travel domain ----------
                new Document("Design a hotel booking system schema with rooms, guests, reservations, and billing."),
                new Document("Create a database for an airline reservation system with flights, seats, and passengers."),
                new Document("I need a schema for a movie ticket booking app with shows, screens, and seats."),

                // ---------- Operations / enterprise domain ----------
                new Document("Design a warehouse and inventory management database with stock levels and suppliers."),
                new Document("Create a CRM schema with leads, contacts, deals, and activities."),
                new Document("Design an HRMS schema with employees, payroll, attendance, and departments."),
                new Document("Create an ERP database schema covering purchasing, inventory, and finance modules."),

                // ---------- Misc app domains ----------
                new Document("Design a ride sharing app database with drivers, riders, trips, and payments."),
                new Document("Create a gym management schema with members, trainers, and subscription plans."),

                // ---------- Concept grounding (as sentences, not bare nouns) ----------
                new Document("A primary key is a column or set of columns that uniquely identifies each row in a table."),
                new Document("A foreign key is a column that references the primary key of another table to enforce a relationship."),
                new Document("A composite key uses two or more columns together to uniquely identify a row."),
                new Document("Normalization is the process of organizing tables to reduce redundancy and avoid update anomalies."),
                new Document("A junction table is used to resolve a many-to-many relationship between two entities."),
                new Document("An index improves query performance on frequently searched columns."),
                new Document("A check constraint restricts the values that can be stored in a column."),

                // ---------- Naming / style conventions ----------
                new Document("Use snake_case naming, plural table names, and an id column as the primary key for every table."),
                new Document("Foreign key columns should follow the table_id naming convention.")
        );
        vectorStore.add(documents);
    }


    public boolean isRelevantPrompt(String prompt) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(prompt)
                        .topK(1)
                        .build()
        );

        if (docs.isEmpty()) {
            return false;
        }

        double distance = ((Number) docs.getFirst()
                .getMetadata()
                .get("distance"))
                .doubleValue();
        log.info("{}", distance);
        return distance <= 0.60;
    }

    public void testSimilaritySearch() {

        List<String> prompts = List.of(
                "Create a hospital database",
                "Design a student attendance schema",
                "Generate SQL for an ecommerce app",
                "Make a banking database",
                "Create a restaurant management system",

                "Who is Virat Kohli?",
                "Write a Java program",
                "Explain Spring Boot",
                "Tell me a joke",
                "What's the weather today?"
        );

        for (String prompt : prompts) {

            System.out.println("\n====================================");
            System.out.println("Prompt: " + prompt);

            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(prompt)
                            .topK(1)
                            .build()
            );

            if (docs.isEmpty()) {
                System.out.println("No document found.");
                continue;
            }

            Document doc = docs.getFirst();
//
//            System.out.println("Retrieved Document:");
//            System.out.println(doc.getText());

            System.out.println("Metadata:");
            System.out.println(doc.getMetadata());
        }
    }
}
