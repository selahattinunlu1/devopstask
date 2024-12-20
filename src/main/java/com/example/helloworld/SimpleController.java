package com.example.helloworld;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class SimpleController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${elasticsearch.username}")
    private String elasticsearchUsername;

    @Value("${elasticsearch.password}")
    private String elasticsearchPassword;

    @Value("${elasticsearch.url}")
    private String elasticsearchUrl;

    // 1. Buton: Sunucu saati ve istemci IP'sini döndüren endpoint
    @GetMapping("/get-time-and-ip")
    public Map<String, String> getTimeAndIp(@RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
                                            @RequestHeader(value = "Host", required = false) String host) {
        // Sunucu saatini alıyoruz
        LocalTime currentTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String serverTime = currentTime.format(formatter);

        // İstemci IP'sini alıyoruz
        String clientIp;
        if (xForwardedFor != null) {
            clientIp = xForwardedFor.split(",")[0];
        } else if (host != null) {
            clientIp = host;
        } else {
            clientIp = "Client IP could not be determined.";
        }

        // JSON olarak döndürüyoruz
        Map<String, String> response = new HashMap<>();
        response.put("server-time", serverTime);
        response.put("client-ip", clientIp);
        return response;
    }

    // 2. Buton: Veriyi Elasticsearch'e gönderen endpoint
    @GetMapping("/send-to-elasticsearch")
    public String sendToElasticsearch(@RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
                                      @RequestHeader(value = "Host", required = false) String host) {
        // Veriyi alalım (örnek olarak server-time ve client-ip)
        Map<String, String> timeAndIp = getTimeAndIp(xForwardedFor, host);

        // JSON verisini oluşturuyoruz
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("server-time", timeAndIp.get("server-time"));
        requestData.put("client-ip", timeAndIp.get("client-ip"));

        // Elasticsearch URL'sine veri gönderiyoruz
        String url = elasticsearchUrl + "/testinium_index/_doc";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Elasticsearch için Authentication bilgilerini ekliyoruz
        String auth = elasticsearchUsername + ":" + elasticsearchPassword;
        String encodedAuth = new String(java.util.Base64.getEncoder().encode(auth.getBytes()));
        headers.set("Authorization", "Basic " + encodedAuth);

        // JSON verisini HTTP POST isteği ile gönderiyoruz
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);

        try {
            // SSL doğrulamasını atlayacak RestTemplate oluşturma
            restTemplate = createRestTemplateWithNoSSLVerification();

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            logger.info("Data successfully sent to Elasticsearch at: {}", elasticsearchUrl); 
            return "Data sent to Elasticsearch: " + response.getBody();
        } catch (Exception e) {
            logger.error("Error sending data to Elasticsearch at: {}. Error: {}", elasticsearchUrl, e.getMessage()); 
            return "Error sending data to Elasticsearch: " + e.getMessage();
        }
    }

    // SSL doğrulamasını atlayacak RestTemplate oluşturma
    private RestTemplate createRestTemplateWithNoSSLVerification() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCertificates = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCertificates, new java.security.SecureRandom());

        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        return new RestTemplate();
    }
}
