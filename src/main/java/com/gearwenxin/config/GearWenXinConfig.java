package com.gearwenxin.config;

import com.gearwenxin.client.*;
import com.gearwenxin.model.request.PromptRequest;
import com.gearwenxin.model.response.PromptResponse;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * @author Ge Mingjia
 */
@Data
@Configuration
@ComponentScan
@ConfigurationProperties("gear.wenxin")
public class GearWenXinConfig {

    private String accessToken;

    @Bean
    public ErnieBotClient ernieBotClient() {
        return new ErnieBotClient() {
            @Override
            public String getAccessToken() {
                return accessToken;
            }
        };
    }

    @Bean
    public ErnieBotTurboClient ernieBotTurboClient() {
        return new ErnieBotTurboClient() {
            @Override
            public String getAccessToken() {
                return accessToken;
            }
        };
    }

    @Bean
    public Bloomz7BClient bloomz7BClient() {
        return new Bloomz7BClient() {

            @Override
            public String getAccessToken() {
                return accessToken;
            }
        };
    }

    @Bean
    public PromptBotClient promptBotClient() {
        return new PromptBotClient() {
            @Override
            public String getAccessToken() {
                return accessToken;
            }
        };
    }

}