package myhttp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;


public class Request {
    // 定义请求方式
    private static final Pattern menthodRegex = Pattern.compile("^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|TRACE|CONNECT)\\s");

    /**
     * 请求体
     */
    private String body;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求头
     * @return
     */
    private Map<String,String> headers;

    public String getBody() {
        return body;
    }

    public String getMethod() {
        return method;
    }

    public HashMap<String,String> getHeaders() {
        return (HashMap<String, String>) headers;
    }


    public Request(Socket socket) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
        // 设置的请求头编码
        Header[] headers1 = HttpParser.parseHeaders(dataInputStream, "UTF-8");
        var methodLine = HttpParser.readLine(dataInputStream, "UTF-8");
        Matcher matcher = menthodRegex.matcher(methodLine);
        matcher.find();
        String method = matcher.group();

        HashMap<String, String> headMap = new HashMap<>();
        for (Header header : headers1) {
            headMap.put(header.getName(), header.getValue());
        }
        BufferedReader bufferedReader1 = new BufferedReader(new InputStreamReader(dataInputStream));
        var body = new StringBuilder();
        char[] buffer = new char[1024];

        // 对输入刘进行读取
        while (dataInputStream.available() > 0) {
            bufferedReader1.read(buffer);
            body.append(buffer);
        }
        this.body = body.toString();
        this.method = method ;
        this.headers = headMap;

    }


}
