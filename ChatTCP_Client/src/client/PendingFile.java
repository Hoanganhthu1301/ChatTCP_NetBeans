/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */


public class PendingFile {
    public final long id;
    public final String fileName;
    public final String from;
    public final String mime;
    public final byte[] data;
    public String sender; 

    public PendingFile(long id, String fileName, String from, String mime, byte[] data) {
        this.id = id;
        this.fileName = fileName;
        this.from = from;
        this.mime = mime;
        this.data = data;
    }
}
