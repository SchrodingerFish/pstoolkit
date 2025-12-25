package com.cn.test;

import cn.hutool.core.util.StrUtil;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class TestGemini {

    static void main() {
        // 从环境变量获取API Key，假设环境变量名为 "GEMINI_API_KEY"
        String apiKey = System.getenv("GEMINI_API_KEY");

        // 检查API Key是否已设置
        if (StrUtil.isEmpty(apiKey)) {
            throw new IllegalArgumentException("请设置环境变量 GEMINI_API_KEY");
        }

        GenerateContentResponse response;
        try (Client client = Client.builder().apiKey(apiKey).build()) {

            response = client.models.generateContent("gemini-flash-latest", "How do you think about American?", null);
        } catch (Exception e) {
            throw new RuntimeException("调用Gemini API失败", e);
        }

        System.out.println(response.text());
    }
}
