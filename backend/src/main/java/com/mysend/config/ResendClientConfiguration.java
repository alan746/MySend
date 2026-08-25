package com.mysend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ResendClientConfiguration {

    @Bean
    @Qualifier("resendRestClient")
    RestClient resendRestClient(AppProperties properties) {
        AppProperties.Resend resend = properties.resend();
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(resend.connectTimeout());
        requestFactory.setReadTimeout(resend.readTimeout());
        return RestClient.builder()
                .baseUrl(resend.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + resend.apiKey()
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}
