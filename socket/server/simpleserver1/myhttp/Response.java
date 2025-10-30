package myhttp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;

public class Response {

    /**
     * 套接字
     */
    private Socket socket;

    /**
     * 定义状态码
     */
    private int status;

    private static HashMap<Integer, String> codeMap;

    public Response(Socket socket) {
        this.socket = socket;
        if (codeMap == null) {
            // 初始化
            codeMap = new HashMap<>();
            codeMap.put(200, "OK");
            codeMap.put(404, "NOT FOUND");
            codeMap.put(500, "SERVER ERROR");
        }
    }

    public void send(String msg) throws IOException {
        var resp = "Http/1.1 " + this.status + " " + codeMap.get(this.status) + "\r\n";
        resp += "\r\n";
        resp += msg;
        this.senRaw(resp);
    }

    public void senRaw(String msg) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        bufferedWriter.write(msg);
        bufferedWriter.flush();
        socket.close();
    }

}
