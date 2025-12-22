/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


import java.sql.Connection;
import java.sql.DriverManager;

public class Db {
    private static final String URL =
        "jdbc:sqlserver://localhost:1433;databaseName=ChatTCP;encrypt=true;trustServerCertificate=true";
    private static final String USER = "thu";       // sửa lại theo máy mày
    private static final String PASS = "123456";   // sửa lại

    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}

