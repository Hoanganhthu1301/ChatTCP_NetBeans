/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ClientContext {
    public static Socket socket;
    public static DataInputStream in;
    public static DataOutputStream out;
    public static String username;

    public static MainFrame main;

    // "ALL" | "USER:diem" | "GROUP:g1"
    public static volatile String currentChatKey = "ALL";

    public static ConcurrentHashMap<String, StringBuilder> history = new ConcurrentHashMap<>();
    static String currentChatPeer;

    public static StringBuilder getHistory(String key) {
        return history.computeIfAbsent(key, k -> new StringBuilder());
        
    }
    private static final java.util.Map<String, Long> convMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setConvId(String chatKey, long convId) {
        convMap.put(chatKey, convId);
    }
    public static Long getConvId(String chatKey) {
        return convMap.get(chatKey);
    }

}
