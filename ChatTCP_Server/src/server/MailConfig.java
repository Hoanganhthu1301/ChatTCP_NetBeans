/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server;

/**
 *
 * @author hoang
 */


public class MailConfig {
    
    public static final String FROM_EMAIL = "tn1301000@gmail.com";
    public static final String APP_PASSWORD = "askrdtpjiinwbuyd";

    public static String fromEmail() { return FROM_EMAIL; }
    public static String appPassword() { return APP_PASSWORD; }
    public static boolean enabled() { return true; }
}