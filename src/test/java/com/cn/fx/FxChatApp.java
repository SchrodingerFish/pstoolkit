package com.cn.fx;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * JavaFX ChatGPT Client (JDK 25 Ready)
 */
public class FxChatApp extends Application {

    private static final Gson gson = new Gson();
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private VBox messageContainer;
    private ScrollPane scrollPane;
    private TextArea inputArea;
    private Button sendBtn;

    // 配置存储
    private final Preferences prefs = Preferences.userNodeForPackage(FxChatApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX AI Chat - JDK 25");

        // --- 顶部工具栏 ---
        Button settingsBtn = new Button("⚙ 设置");
        settingsBtn.setOnAction(e -> showSettingsDialog(primaryStage));

        Button clearBtn = new Button("🗑 清空历史");
        clearBtn.setOnAction(e -> clearHistory());

        Button exportBtn = new Button("💾 导出全部");
        exportBtn.setOnAction(e -> exportMarkdown(primaryStage, null)); // null exports all

        HBox toolbar = new HBox(10, settingsBtn, clearBtn, exportBtn);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        // --- 中间聊天区域 ---
        messageContainer = new VBox(15);
        messageContainer.setPadding(new Insets(20));
        messageContainer.setStyle("-fx-background-color: white;");

        scrollPane = new ScrollPane(messageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: white; -fx-border-color: transparent;");

        // --- 底部输入区域 ---
        inputArea = new TextArea();
        inputArea.setPromptText("输入消息... (Shift+Enter 换行)");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        sendBtn = new Button("发送");
        sendBtn.setPrefHeight(60);
        sendBtn.setPrefWidth(80);
        sendBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> sendMessage());

        // 回车发送支持
        inputArea.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                sendMessage();
            }
        });

        HBox inputLayout = new HBox(10, inputArea, sendBtn);
        inputLayout.setPadding(new Insets(10));
        inputLayout.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        // --- 主布局 ---
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);
        root.setBottom(inputLayout);

        Scene scene = new Scene(root, 900, 700);

        // 全局CSS美化
        scene.getStylesheets().add("data:text/css," +
                ".button { -fx-background-radius: 4; }" +
                ".text-area { -fx-background-radius: 4; -fx-background-color: transparent; }"
        );

        primaryStage.setScene(scene);
        primaryStage.show();

        // 检查配置，如果没有则弹出设置
        if (prefs.get("apiKey", "").isEmpty()) {
            showSettingsDialog(primaryStage);
        }
    }

    // --- 核心逻辑：发送消息 ---
    private void sendMessage() {
        String content = inputArea.getText().trim();
        if (content.isEmpty()) return;

        inputArea.clear();
        sendBtn.setDisable(true);

        // 1. UI添加用户气泡
        addMessageBubble(content, true);
        conversationHistory.add(new ChatMessage("user", content));

        // 2. UI添加AI占位气泡（用于流式更新）
        MarkdownWebView aiBubble = new MarkdownWebView();
        addAiBubbleContainer(aiBubble);
        StringBuilder fullResponse = new StringBuilder();

        // 3. 构建请求
        String apiKey = prefs.get("apiKey", "");
        String apiUrl = prefs.get("apiUrl", "https://api.openai.com/v1/chat/completions");
        String model = prefs.get("model", "gpt-3.5-turbo");

        JsonObject reqJson = new JsonObject();
        reqJson.addProperty("model", model);
        reqJson.addProperty("stream", true);

        JsonArray messages = new JsonArray();
        // 添加上下文历史 (简单策略：发送全部)
        for (ChatMessage msg : conversationHistory) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role);
            m.addProperty("content", msg.content);
            messages.add(m);
        }
        reqJson.add("messages", messages);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(reqJson)))
                .build();

        // 4. 异步流式处理
        new Thread(() -> {
            try {
                client.send(request, HttpResponse.BodyHandlers.ofLines())
                        .body()
                        .forEach(line -> {
                            // 处理 SSE 格式: data: {...}
                            if (line.startsWith("data:") && !line.contains("[DONE]")) {
                                String jsonStr = line.substring(5).trim();
                                try {
                                    JsonObject chunk = gson.fromJson(jsonStr, JsonObject.class);
                                    JsonArray choices = chunk.getAsJsonArray("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                        if (delta.has("content")) {
                                            String token = delta.get("content").getAsString();
                                            fullResponse.append(token);
                                            // 更新 UI 必须在 JavaFX 线程
                                            Platform.runLater(() -> aiBubble.updateMarkdown(fullResponse.toString()));
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        });

                // 完成后保存历史
                Platform.runLater(() -> {
                    conversationHistory.add(new ChatMessage("assistant", fullResponse.toString()));
                    sendBtn.setDisable(false);
                    // 绑定导出事件到这个气泡
                    aiBubble.setExportContent(fullResponse.toString());
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    aiBubble.updateMarkdown("**Error:** " + ex.getMessage());
                    sendBtn.setDisable(false);
                });
            }
        }).start();
    }

    // --- UI组件：用户气泡 ---
    private void addMessageBubble(String text, boolean isUser) {
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(600);
        textLabel.setStyle("-fx-text-fill: " + (isUser ? "white" : "black") + "; -fx-font-size: 14px;");
        textLabel.setPadding(new Insets(10));

        StackPane bubble = new StackPane(textLabel);
        bubble.setStyle(isUser
                ? "-fx-background-color: #007bff; -fx-background-radius: 15 15 0 15;"
                : "-fx-background-color: #e9ecef; -fx-background-radius: 15 15 15 0;");

        // 右键导出菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem exportItem = new MenuItem("导出此条消息");
        exportItem.setOnAction(e -> exportMarkdown(null, text));
        contextMenu.getItems().add(exportItem);
        bubble.setOnContextMenuRequested(e -> contextMenu.show(bubble, e.getScreenX(), e.getScreenY()));

        HBox row = new HBox(bubble);
        row.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageContainer.getChildren().add(row);

        // 自动滚动到底部
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    // --- UI组件：AI 气泡 (WebView) ---
    private void addAiBubbleContainer(MarkdownWebView webView) {
        HBox row = new HBox(webView);
        row.setAlignment(Pos.CENTER_LEFT);
        messageContainer.getChildren().add(row);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    // --- 设置弹窗 ---
    private void showSettingsDialog(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setTitle("API 设置");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField urlField = new TextField(prefs.get("apiUrl", "https://api.openai.com/v1/chat/completions"));
        TextField keyField = new PasswordField();
        keyField.setText(prefs.get("apiKey", ""));
        TextField modelField = new TextField(prefs.get("model", "gpt-3.5-turbo"));

        grid.addRow(0, new Label("API URL:"), urlField);
        grid.addRow(1, new Label("API Key:"), keyField);
        grid.addRow(2, new Label("Model:"), modelField);

        Button saveBtn = new Button("保存");
        saveBtn.setOnAction(e -> {
            prefs.put("apiUrl", urlField.getText().trim());
            prefs.put("apiKey", keyField.getText().trim());
            prefs.put("model", modelField.getText().trim());
            stage.close();
        });

        VBox layout = new VBox(15, grid, saveBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(10));

        stage.setScene(new Scene(layout, 400, 250));
        stage.showAndWait();
    }

    // --- 功能：清空历史 ---
    private void clearHistory() {
        conversationHistory.clear();
        messageContainer.getChildren().clear();
    }

    // --- 功能：导出 Markdown ---
    private void exportMarkdown(Stage stage, String singleContent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存 Markdown");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Files", "*.md"));
        fileChooser.setInitialFileName("chat_export.md");
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                if (singleContent != null) {
                    writer.write(singleContent);
                } else {
                    for (ChatMessage msg : conversationHistory) {
                        writer.write("### " + msg.role.toUpperCase() + "\n\n");
                        writer.write(msg.content + "\n\n");
                        writer.write("---\n\n");
                    }
                }
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "导出失败: " + ex.getMessage());
                alert.show();
            }
        }
    }

    // --- 内部类：简单的消息对象 ---
    private static class ChatMessage {
        String role;
        String content;
        ChatMessage(String role, String content) { this.role = role; this.content = content; }
    }

    /**
     * 自定义组件：支持 Markdown 的 WebView
     * 使用 marked.js 和 highlight.js 进行渲染
     */
    public class MarkdownWebView extends StackPane {
        private final WebView webView;
        private final WebEngine engine;
        private String rawContent = "";

        // HTML 模板：包含 Markdown 解析器和代码高亮样式
        // 注意：为了演示方便，这里使用了 CDN，请确保联网。
        private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: 'Segoe UI', sans-serif; font-size: 14px; margin: 0; padding: 10px; background-color: #f8f9fa; color: #333; }
                    pre { background: #2d2d2d; color: #ccc; padding: 10px; border-radius: 5px; overflow-x: auto; }
                    code { font-family: 'Consolas', monospace; }
                    p { margin-bottom: 10px; line-height: 1.6; }
                </style>
                <!-- 引入 Marked.js 解析 Markdown -->
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <!-- 引入 Highlight.js 代码高亮 -->
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            </head>
            <body>
                <div id="content"></div>
                <script>
                    function updateContent(markdownText) {
                        // 解析 Markdown
                        document.getElementById('content').innerHTML = marked.parse(markdownText);
                        // 代码高亮
                        hljs.highlightAll();
                        // 自动调整高度通知 Java (可选，此处略)
                        return document.body.scrollHeight;
                    }
                </script>
            </body>
            </html>
        """;

        public MarkdownWebView() {
            webView = new WebView();
            engine = webView.getEngine();

            // 设置气泡外观
            this.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15 15 15 0; -fx-border-color: #ddd; -fx-border-radius: 15 15 15 0;");
            this.setPadding(new Insets(2)); // 边框内边距

            // 限制 WebView 大小
            webView.setPrefWidth(650);
            webView.setPrefHeight(100); // 初始高度

            // 禁止 WebView 自身的右键菜单，使用我们自定义的导出
            webView.setContextMenuEnabled(false);

            // 加载模板
            engine.loadContent(HTML_TEMPLATE);

            this.getChildren().add(webView);

            // 导出菜单
            ContextMenu contextMenu = new ContextMenu();
            MenuItem exportItem = new MenuItem("导出此回答");
            exportItem.setOnAction(e -> exportMarkdown(null, rawContent));
            contextMenu.getItems().add(exportItem);

            // 添加遮罩层以捕获右键事件（因为WebView会吞掉事件）
            // 或者简单地绑定到 this，但在 WebView 上点击可能无效
            // 这里的简单做法是在 WebView 上覆盖一个透明层用于点击，但这会影响复制。
            // 更好的做法是利用 JavaFX WebView 的特性监听。
            webView.setOnContextMenuRequested(e -> contextMenu.show(webView, e.getScreenX(), e.getScreenY()));
        }

        public void setExportContent(String content) {
            this.rawContent = content;
        }

        public void updateMarkdown(String text) {
            this.rawContent = text;
            // 调用 JS 更新内容
            // 只有页面加载完成后才能调用
            if (engine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                executeJsUpdate(text);
            } else {
                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        executeJsUpdate(text);
                    }
                });
            }
        }

        private void executeJsUpdate(String text) {
            // 转义 JSON 字符串以安全传递给 JS
            String jsonStr = gson.toJson(text);
            // 移除首尾引号
            // 更好的方式是直接作为参数传给 JS 桥接，这里用简单字符串拼接演示
            try {
                // 使用 JSObject 桥接是更安全的方式，但为了单文件演示，我们用 executeScript
                // 注意：这里简单的 replace 可能会有注入风险，生产环境请用 JSObject setMember
                engine.executeScript("updateContent(" + jsonStr + ");");

                // 动态调整高度 (简单估算，完美调整需要 JS 回调)
                Integer height = (Integer) engine.executeScript("document.body.scrollHeight");
                if (height != null) {
                    webView.setPrefHeight(height + 20);
                }
            } catch (Exception e) {
                // 忽略 JS 执行期间的临时错误
            }
        }
    }
}