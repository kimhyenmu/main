package com.example.assignment.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SocketIOConfig {

    @Value("${socket.host:localhost}")
    private String host;

    @Value("${socket.port:9092}")
    private Integer port;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        
        // CORS 설정
        config.setOrigin("*");
        
        // 연결 설정
        config.setMaxHttpContentLength(1000000);
        config.setMaxFramePayloadLength(1000000);
        
        // 하트비트 설정
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        
        return new SocketIOServer(config);
    }
}