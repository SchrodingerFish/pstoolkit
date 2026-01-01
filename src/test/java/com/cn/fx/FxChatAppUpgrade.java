package com.cn.fx;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class FxChatAppUpgrade extends Application {

    private static final Gson gson = new Gson();
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private VBox messageContainer;
    private ScrollPane scrollPane;
    private TextArea inputArea;
    private Button sendBtn;
    private final Preferences prefs = Preferences.userNodeForPackage(FxChatAppUpgrade.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX AI Chat Pro");
        // 尝试加载网络图标，如果失败不会报错
        try {
            primaryStage.getIcons().add(new Image("https://cdn-icons-png.flaticon.com/512/4712/4712027.png"));
        } catch (Exception ignored) {}

        // --- 顶部工具栏 ---
        Button settingsBtn = new Button("⚙ 设置");
        settingsBtn.setOnAction(e -> showSettingsDialog(primaryStage));

        Button clearBtn = new Button("🗑 清空");
        clearBtn.setOnAction(e -> clearHistory());

        HBox topBar = new HBox(10, settingsBtn, clearBtn);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        topBar.setAlignment(Pos.CENTER_RIGHT);

        // --- 聊天显示区域 ---
        messageContainer = new VBox(20); // 消息块之间的垂直间距
        messageContainer.setPadding(new Insets(20));
        messageContainer.setStyle("-fx-background-color: white;");

        scrollPane = new ScrollPane(messageContainer);
        scrollPane.setFitToWidth(true); // 关键：让内容宽度自适应
        scrollPane.setStyle("-fx-background: white; -fx-border-color: transparent;");

        // --- 底部输入区域 ---
        inputArea = new TextArea();
        inputArea.setPromptText("请输入内容 (Shift+Enter 换行)...");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        sendBtn = new Button("发送");
        sendBtn.setPrefHeight(60);
        sendBtn.setPrefWidth(80);
        sendBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> sendMessage());

        // 键盘 Enter 发送，Shift+Enter 换行
        inputArea.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                sendMessage();
            }
        });

        HBox inputLayout = new HBox(10, inputArea, sendBtn);
        inputLayout.setPadding(new Insets(10));
        inputLayout.setStyle("-fx-background-color: #f8f9fa; -fx-border-width: 1 0 0 0; -fx-border-color: #ddd;");

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(scrollPane);
        root.setBottom(inputLayout);

        Scene scene = new Scene(root, 950, 750);
        // 添加通用样式
        scene.getStylesheets().add("data:text/css," +
                ".action-btn { -fx-background-color: transparent; -fx-text-fill: #999; -fx-cursor: hand; -fx-padding: 2 5 2 5; }" +
                ".action-btn:hover { -fx-background-color: #eee; -fx-text-fill: #333; -fx-background-radius: 3; }"
        );

        primaryStage.setScene(scene);
        primaryStage.show();

        // 如果没有配置过API Key，自动弹出设置框
        if (prefs.get("apiKey", "").isEmpty()) {
            Platform.runLater(() -> showSettingsDialog(primaryStage));
        }
    }

    // --- 核心逻辑：发送消息 ---
    private void sendMessage() {
        String content = inputArea.getText().trim();
        if (content.isEmpty()) return;

        inputArea.clear();
        sendBtn.setDisable(true);

        // 1. 添加用户消息 (右侧)
        addUserMessage(content);
        conversationHistory.add(new ChatMessage("user", content));

        // 2. 添加AI消息占位符 (左侧)
        MarkdownWebView aiWebView = new MarkdownWebView();
        // isUser = false, 靠左对齐
        MessageBlock aiBlock = new MessageBlock(aiWebView, false);
        messageContainer.getChildren().add(aiBlock);
        scrollToBottom();

        StringBuilder fullResponse = new StringBuilder();

        // 3. 异步请求API
        new Thread(() -> {
            try {
                String apiKey = prefs.get("apiKey", "");
                String apiUrl = prefs.get("apiUrl", "https://api.openai.com/v1/chat/completions");
                String model = prefs.get("model", "gpt-3.5-turbo");

                JsonObject req = new JsonObject();
                req.addProperty("model", model);
                req.addProperty("stream", true);
                JsonArray messages = new JsonArray();
                for (ChatMessage msg : conversationHistory) {
                    JsonObject m = new JsonObject();
                    m.addProperty("role", msg.role);
                    m.addProperty("content", msg.content);
                    messages.add(m);
                }
                req.add("messages", messages);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(req)))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofLines()).body()
                        .forEach(line -> {
                            if (line.startsWith("data:") && !line.contains("[DONE]")) {
                                try {
                                    JsonObject chunk = gson.fromJson(line.substring(5), JsonObject.class);
                                    if (chunk.getAsJsonArray("choices").size() > 0) {
                                        JsonObject delta = chunk.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");
                                        if (delta.has("content")) {
                                            String text = delta.get("content").getAsString();
                                            fullResponse.append(text);
                                            // 实时更新WebView内容
                                            Platform.runLater(() -> aiWebView.updateMarkdown(fullResponse.toString()));
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                        });

                Platform.runLater(() -> {
                    conversationHistory.add(new ChatMessage("assistant", fullResponse.toString()));
                    sendBtn.setDisable(false);
                    // 保存完整内容供导出使用
                    aiBlock.setContentForExport(fullResponse.toString());
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    aiWebView.updateMarkdown("**Error:** " + e.getMessage() + "\n\n请检查设置中的API Key和网络连接。");
                    sendBtn.setDisable(false);
                });
            }
        }).start();
    }

    private void addUserMessage(String text) {
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(500); // 限制用户气泡最大宽度，防止太宽阅读困难
        textLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        textLabel.setPadding(new Insets(10));

        StackPane bubble = new StackPane(textLabel);
        // 调整圆角，使其看起来更像聊天气泡（右上角直角改为右下角直角或其他风格）
        bubble.setStyle("-fx-background-color: #007bff; -fx-background-radius: 15 15 0 15;");

        // 添加一点阴影让气泡更立体
        DropShadow ds = new DropShadow();
        ds.setColor(Color.color(0, 0, 0, 0.1));
        ds.setOffsetY(2);
        bubble.setEffect(ds);

        // isUser = true
        MessageBlock userBlock = new MessageBlock(bubble, true);
        userBlock.setContentForExport(text);
        messageContainer.getChildren().add(userBlock);
        scrollToBottom();
    }

    private void scrollToBottom() {
        // 延迟执行以确保高度计算完成
        Platform.runLater(() -> {
            try { Thread.sleep(50); } catch (Exception e) {}
            scrollPane.setVvalue(1.0);
        });
    }

    private void clearHistory() {
        conversationHistory.clear();
        messageContainer.getChildren().clear();
    }

    // --- 修复后的设置对话框 ---
    private void showSettingsDialog(Stage owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT); // 透明无边框

        // 标题
        Label titleLabel = new Label("API 设置");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #333;");

        Separator separator = new Separator();
        separator.setPadding(new Insets(10, 0, 15, 0));

        // 表单
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        TextField urlField = new TextField(prefs.get("apiUrl", "https://api.openai.com/v1/chat/completions"));
        urlField.setPrefWidth(280);
        urlField.setPromptText("例如 https://api.openai.com/v1/chat/completions");

        PasswordField keyField = new PasswordField();
        keyField.setText(prefs.get("apiKey", ""));
        keyField.setPromptText("sk-...");

        TextField modelField = new TextField(prefs.get("model", "gpt-3.5-turbo"));
        modelField.setPromptText("gpt-4, gpt-3.5-turbo 等");

        // 统一样式
        String fieldStyle = "-fx-background-radius: 5; -fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 8;";
        urlField.setStyle(fieldStyle);
        keyField.setStyle(fieldStyle);
        modelField.setStyle(fieldStyle);

        grid.addRow(0, new Label("接口地址:"), urlField);
        grid.addRow(1, new Label("API Key:"), keyField);
        grid.addRow(2, new Label("模型名称:"), modelField);

        // --- 按钮修复区域 ---

        // 1. 取消按钮
        Button cancelBtn = new Button("取消");
        cancelBtn.setPrefWidth(90);
        cancelBtn.setCancelButton(true); // 允许按 ESC 触发
        cancelBtn.setStyle("-fx-background-color: #f1f3f5; -fx-text-fill: #333; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-size: 14px;");

        // 2. 保存按钮
        Button saveBtn = new Button("保存");
        saveBtn.setPrefWidth(90);
        saveBtn.setDefaultButton(true); // 允许按 Enter 触发
        saveBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-size: 14px;");

        // 3. 水平布局容器 (HBox) 并居中
        HBox buttonBox = new HBox(20, cancelBtn, saveBtn);
        buttonBox.setAlignment(Pos.CENTER); // [关键] 按钮水平居中
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        // 根容器
        VBox root = new VBox(titleLabel, separator, grid, buttonBox);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #e0e0e0; -fx-border-width: 1;");

        DropShadow dropShadow = new DropShadow();
        dropShadow.setColor(Color.color(0, 0, 0, 0.2));
        dropShadow.setRadius(25);
        dropShadow.setOffsetY(5);
        root.setEffect(dropShadow);

        // 事件绑定
        // 传递 root 用于播放退出动画
        cancelBtn.setOnAction(e -> animateClose(stage, root));

        saveBtn.setOnAction(e -> {
            prefs.put("apiUrl", urlField.getText().trim());
            prefs.put("apiKey", keyField.getText().trim());
            prefs.put("model", modelField.getText().trim());
            animateClose(stage, root);
        });

        // 窗口拖拽支持
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });

        Scene scene = new Scene(root, Color.TRANSPARENT);
        stage.setScene(scene);

        // 入场动画
        root.setScaleX(0.9);
        root.setScaleY(0.9);
        root.setOpacity(0);
        stage.setOnShown(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(200), root);
            fade.setToValue(1);
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), root);
            scale.setToX(1);
            scale.setToY(1);
            new ParallelTransition(fade, scale).play();
        });

        stage.showAndWait();
    }

    private void animateClose(Stage stage, Node root) {
        FadeTransition fade = new FadeTransition(Duration.millis(150), root);
        fade.setToValue(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(150), root);
        scale.setToX(0.9);
        scale.setToY(0.9);

        ParallelTransition pt = new ParallelTransition(fade, scale);
        pt.setOnFinished(e -> stage.close());
        pt.play();
    }

    // --- 核心修复：消息块组件 ---
    // 包含气泡和下方的工具栏，并控制左右对齐
    class MessageBlock extends VBox {
        private String rawContent = "";
        private final Node bubbleNode;

        public MessageBlock(Node bubble, boolean isUser) {
            this.bubbleNode = bubble;

            // [关键修复] 禁止子元素（气泡）填满整行宽度，这样对齐属性（setAlignment）才会生效
            this.setFillWidth(false);

            // 1. 设置整个块的对齐方式
            // 用户：靠右；AI：靠左
            this.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            this.setSpacing(5); // 气泡和工具栏的垂直间距

            // 2. 工具栏 (复制/截图按钮)
            HBox toolbar = new HBox(8);
            // 工具栏内部按钮的对齐方式
            toolbar.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            toolbar.setPadding(new Insets(0, 5, 0, 5));

            Button btnCopy = createIconBtn("📄", "复制内容");
            btnCopy.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(rawContent);
                Clipboard.getSystemClipboard().setContent(cc);
                flashButton(btnCopy);
            });

            Button btnImg = createIconBtn("📷", "导出图片");
            btnImg.setOnAction(e -> exportAsImage(bubbleNode));

            // Markdown导出只对AI消息有意义，或者都加也可以
            Button btnMd = createIconBtn("⬇", "导出Markdown");
            btnMd.setOnAction(e -> exportAsMarkdown(rawContent));

            toolbar.getChildren().addAll(btnCopy, btnImg, btnMd);

            // 3. 组装：气泡在上，工具栏在下
            this.getChildren().addAll(bubble, toolbar);
        }

        public void setContentForExport(String content) {
            this.rawContent = content;
        }

        private Button createIconBtn(String text, String tooltipText) {
            Button btn = new Button(text);
            btn.getStyleClass().add("action-btn");
            btn.setTooltip(new Tooltip(tooltipText));
            // 稍微调大字体，去除背景，灰色图标
            btn.setStyle("-fx-font-size: 11px; -fx-background-color: transparent; -fx-text-fill: #999; -fx-cursor: hand;");
            return btn;
        }

        private void flashButton(Button btn) {
            String originalText = btn.getText();
            btn.setText("✔");
            btn.setStyle("-fx-text-fill: #28a745; -fx-background-color: transparent; -fx-font-size: 11px;");
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    btn.setText(originalText);
                    btn.setStyle("-fx-text-fill: #999; -fx-background-color: transparent; -fx-font-size: 11px;");
                });
            }).start();
        }

        private void exportAsMarkdown(String content) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("保存 Markdown");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
            fileChooser.setInitialFileName("chat_export.md");
            File file = fileChooser.showSaveDialog(getScene().getWindow());
            if (file != null) {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(content);
                } catch (IOException ex) {
                    new Alert(Alert.AlertType.ERROR, "导出失败: " + ex.getMessage()).show();
                }
            }
        }

        private void exportAsImage(Node node) {
            // 截图前确保节点已经布局
            WritableImage image = node.snapshot(new SnapshotParameters(), null);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("保存为图片");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
            fileChooser.setInitialFileName("chat_snapshot.png");
            File file = fileChooser.showSaveDialog(getScene().getWindow());
            if (file != null) {
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                } catch (IOException ex) {
                    new Alert(Alert.AlertType.ERROR, "保存图片失败: " + ex.getMessage()).show();
                }
            }
        }
    }

    // --- Markdown WebView 组件 (无需修改，保持原样) ---
    public class MarkdownWebView extends StackPane {
        private final WebView webView;
        private final WebEngine engine;

        // CSS 样式：确保 body 背景也是浅灰色，与气泡融合
        private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: 'Segoe UI Emoji', 'Microsoft YaHei', sans-serif; 
                        font-size: 14px; 
                        margin: 0; 
                        padding: 12px; 
                        background-color: #f8f9fa; 
                        color: #333; 
                        overflow-wrap: break-word;
                    }
                    pre { background: #2d2d2d; color: #ccc; padding: 10px; border-radius: 5px; overflow-x: auto; }
                    code { font-family: 'Consolas', monospace; }
                    p { margin-bottom: 8px; line-height: 1.6; }
                    img { max-width: 100%; }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            </head>
            <body>
                <div id="content"></div>
                <script>
                    function updateContent(markdownText) {
                        document.getElementById('content').innerHTML = marked.parse(markdownText);
                        hljs.highlightAll();
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
            this.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; -fx-border-color: #ddd; -fx-border-radius: 10; -fx-border-width: 1;");
            this.setPadding(new Insets(1)); // 边框内边距

            // 阴影效果
            DropShadow ds = new DropShadow();
            ds.setColor(Color.color(0, 0, 0, 0.05));
            ds.setOffsetY(2);
            this.setEffect(ds);

            // 限制宽度：设为固定宽度或根据窗口调整。
            // 650px 是一个比较舒适的阅读宽度。因为外层 MessageBlock 设置了 setFillWidth(false)，
            // 所以这个 StackPane 不会再被拉伸到全屏，而是保持这个首选宽度。
            webView.setPrefWidth(650);
            webView.setMinWidth(300); // 最小宽度
            webView.setPrefHeight(60); // 初始高度

            webView.setContextMenuEnabled(false);
            engine.loadContent(HTML_TEMPLATE);

            this.getChildren().add(webView);
        }

        public void updateMarkdown(String text) {
            if (engine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                executeJsUpdate(text);
            } else {
                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) executeJsUpdate(text);
                });
            }
        }

        private void executeJsUpdate(String text) {
            String jsonStr = gson.toJson(text);
            try {
                engine.executeScript("updateContent(" + jsonStr + ");");
                Object res = engine.executeScript("document.body.scrollHeight");
                if (res instanceof Integer h) {
                    webView.setPrefHeight(h + 25);
                }
            } catch (Exception ignored) {}
        }
    }

    private static class ChatMessage {
        String role, content;
        ChatMessage(String role, String content) { this.role = role; this.content = content; }
    }
}