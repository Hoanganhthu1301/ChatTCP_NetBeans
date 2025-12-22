/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

/**
 *
 * @author hoang
 */

import java.util.concurrent.ConcurrentHashMap;

public class PendingStore {
    private static final ConcurrentHashMap<Long, PendingFile> map = new ConcurrentHashMap<>();

    // add / update
    public static void put(PendingFile pf) {
        if (pf == null) return;
        map.put(pf.id, pf);
    }

    // get (không xoá)
    public static PendingFile get(long id) {
        return map.get(id);
    }

    // take (lấy rồi xoá) -> dùng cho /save
    public static PendingFile take(long id) {
        return map.remove(id);
    }
}
