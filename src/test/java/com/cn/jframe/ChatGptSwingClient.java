package com.cn.jframe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatGPT Swing Client V2
 * 特性：Markdown渲染、打字机效果、设置弹窗、配置持久化、JDK 25 语法
 */
public class ChatGptSwingClient extends JFrame {

    // --- UI 组件 ---
    private JEditorPane chatPane; // 改用 JEditorPane 支持 HTML
    private JTextField inputField;
    private JButton sendButton;
    private JButton settingsButton;
    private JLabel statusLabel;

    // --- 数据与状态 ---
    private final List<Message> conversationHistory = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient;
    private final AppConfig config = new AppConfig(); // 配置管理

    // --- HTML 内容构建 ---
    private final StringBuilder fullHtmlContent = new StringBuilder(); // 存储整个对话的 HTML 结构

    // --- 打字机相关 ---
    private Timer typewriterTimer;
    private String currentAiResponseText = ""; // AI 原始文本
    private int typeWriterIndex = 0;
    private boolean isTyping = false; // 是否正在打字

    public ChatGptSwingClient() {
        // 初始化 HttpClient
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 加载保存的配置
        config.load();

        initUI();
        initHtmlStyle();
    }

    private void initUI() {
        setTitle("ChatGPT Client (Markdown & Typewriter)");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. 顶部工具栏 (放设置按钮)
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        settingsButton = new JButton("⚙ 设置 API / 模型");
        settingsButton.addActionListener(e -> showSettingsDialog());
        toolBar.add(Box.createHorizontalGlue()); // 让按钮靠右
        toolBar.add(settingsButton);
        add(toolBar, BorderLayout.NORTH);

        // 2. 中间聊天显示区域 (HTML)
        chatPane = new JEditorPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");

        JScrollPane scrollPane = new JScrollPane(chatPane);
        add(scrollPane, BorderLayout.CENTER);

        // 3. 底部输入区域
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        inputField = new JTextField();
        inputField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        inputField.addActionListener(e -> onSend());

        sendButton = new JButton("发送");
        sendButton.addActionListener(e -> onSend());

        JPanel inputContainer = new JPanel(new BorderLayout(5, 0));
        inputContainer.add(inputField, BorderLayout.CENTER);
        inputContainer.add(sendButton, BorderLayout.EAST);

        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
        statusLabel.setForeground(Color.GRAY);

        bottomPanel.add(inputContainer, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * 设置简单的 CSS 样式，使 Markdown 渲染更好看
     */
    private void initHtmlStyle() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: 'Microsoft YaHei', sans-serif; font-size: 14px; margin: 10px; }");
        styleSheet.addRule(".user { color: #2E86C1; font-weight: bold; margin-top: 10px; }");
        styleSheet.addRule(".ai { color: #212F3C; margin-bottom: 10px; }");
        styleSheet.addRule("code { font-family: 'Consolas', monospace; background-color: #F2F4F4; padding: 2px; }");
        styleSheet.addRule("pre { background-color: #F8F9F9; border: 1px solid #DDD; padding: 10px; border-radius: 5px; overflow: auto; }");
        styleSheet.addRule("p { margin: 5px 0; }");
        chatPane.setEditorKit(kit);
        chatPane.setText("<html><body><div id='content'></div></body></html>");
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "设置", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField urlField = new JTextField(config.getApiUrl(), 25);
        JTextField keyField = new JPasswordField(config.getApiKey(), 25);
        JTextField modelField = new JTextField(config.getModel(), 25);

        // Row 1
        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("API URL:"), gbc);
        gbc.gridx = 1; dialog.add(urlField, gbc);

        // Row 2
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; dialog.add(keyField, gbc);

        // Row 3
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; dialog.add(modelField, gbc);

        // Buttons
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            config.setApiUrl(urlField.getText().trim());
            config.setApiKey(keyField.getText().trim());
            config.setModel(modelField.getText().trim());
            config.save(); // 持久化
            dialog.dispose();
        });

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        dialog.add(saveBtn, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void onSend() {
        if (isTyping) return; // 防止在打字时再次发送

        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        if (config.getApiKey().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在右上角设置中配置 API Key");
            return;
        }

        // UI 状态更新
        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        statusLabel.setText("正在思考...");

        // 1. 显示用户消息 (直接显示，不需要打字机)
        appendHtml("User", text);
        conversationHistory.add(new Message("user", text));

        // 2. 异步请求 API
        ChatRequest requestPayload = new ChatRequest(config.getModel(), conversationHistory);
        String jsonBody = gson.toJson(requestPayload);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.getApiUrl()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    ChatResponse chatResponse = gson.fromJson(response.body(), ChatResponse.class);
                    if (chatResponse.choices() != null && !chatResponse.choices().isEmpty()) {
                        String aiContent = chatResponse.choices().get(0).message().content();

                        // 成功获取，开始打字机效果
                        SwingUtilities.invokeLater(() -> startTypewriterEffect(aiContent));
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        appendHtml("System", "Error: " + response.statusCode() + " - " + response.body());
                        resetInputState();
                    });
                }

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendHtml("System", "Exception: " + ex.getMessage());
                    resetInputState();
                });
                ex.printStackTrace();
            }
        });
    }

    /**
     * 启动打字机效果
     */
    private void startTypewriterEffect(String fullText) {
        currentAiResponseText = fullText;
        typeWriterIndex = 0;
        isTyping = true;
        statusLabel.setText("正在输入...");

        // 将新的 AI 消息占位符添加到 conversation history
        conversationHistory.add(new Message("assistant", fullText));

        // 创建定时器，每 30ms 输出几个字符
        typewriterTimer = new Timer(30, e -> {
            typeWriterIndex += 2; // 每次输出2个字符，加快一点速度
            if (typeWriterIndex > currentAiResponseText.length()) {
                typeWriterIndex = currentAiResponseText.length();
            }

            String partialText = currentAiResponseText.substring(0, typeWriterIndex);
            updateLastAiMessage(partialText);

            // 滚动到底部
            chatPane.setCaretPosition(chatPane.getDocument().getLength());

            if (typeWriterIndex >= currentAiResponseText.length()) {
                ((Timer)e.getSource()).stop();
                isTyping = false;
                resetInputState();
            }
        });
        typewriterTimer.start();
    }

    /**
     * 将消息追加到 HTML 视图
     */
    private void appendHtml(String role, String content) {
        String roleClass = role.equalsIgnoreCase("User") ? "user" : "ai";
        String displayRole = role.equalsIgnoreCase("User") ? "You" : config.getModel();

        // 转换简单的 Markdown 到 HTML
        String htmlContent = SimpleMarkdownParser.parse(content);

        // 这里我们把整个对话作为 HTML 重新构建 (对于 JEditorPane 简单处理)
        // 优化方案：只插入最后一段。但为了处理打字机效果更新最后一段，我们需要维护状态。
        // 为了简单演示，我们采用追加到 StringBuilder 并刷新整个 Document 的方式(对于长对话性能一般，但够用)

        fullHtmlContent.append(String.format("<div class='%s'><strong>[%s]:</strong></div>", roleClass, displayRole));
        fullHtmlContent.append(String.format("<div class='%s'>%s</div>", roleClass, htmlContent));
        fullHtmlContent.append("<hr color='#eeeeee' size='1'>"); // 分隔线

        chatPane.setText("<html><body>" + fullHtmlContent.toString() + "</body></html>");
    }

    /**
     * 更新最后一条 AI 消息的内容（用于打字机效果刷新）
     * 逻辑：从 fullHtmlContent 中移除最后一部分，加上新的部分，然后刷新界面
     */
    private void updateLastAiMessage(String partialContent) {
        // 解析 Markdown
        String htmlContent = SimpleMarkdownParser.parse(partialContent);

        // 构造临时的完整 HTML
        // 注意：这里我们并未真正修改 fullHtmlContent，直到打字机结束或下一次追加
        // 这是一个视觉欺骗

        String tempHtml = "<html><body>" +
                fullHtmlContent.toString() +
                String.format("<div class='ai'><strong>[%s]:</strong></div>", config.getModel()) +
                String.format("<div class='ai'>%s</div>", htmlContent) +
                "</body></html>";

        chatPane.setText(tempHtml);
    }

    private void resetInputState() {
        inputField.setEnabled(true);
        sendButton.setEnabled(true);
        statusLabel.setText("就绪");
        inputField.requestFocus();

        // 打字机结束后，将最后的内容正式写入 fullHtmlContent，防止下次被覆盖
        if (!isTyping && !currentAiResponseText.isEmpty()) {
            // 注意：这里需要避免重复添加，因为 startTypewriterEffect 没有调用 appendHtml
            String htmlContent = SimpleMarkdownParser.parse(currentAiResponseText);
            String displayRole = config.getModel();
            fullHtmlContent.append(String.format("<div class='ai'><strong>[%s]:</strong></div>", displayRole));
            fullHtmlContent.append(String.format("<div class='ai'>%s</div>", htmlContent));
            fullHtmlContent.append("<hr color='#eeeeee' size='1'>");
            currentAiResponseText = ""; // 清空缓冲区
        }
    }

    // ==========================================
    // 简单的 Markdown 解析器 (正则实现)
    // ==========================================
    static class SimpleMarkdownParser {
        public static String parse(String markdown) {
            if (markdown == null) return "";

            // 1. HTML 转义 (防止 XSS 或 标签混乱)
            String html = markdown.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");

            // 2. 代码块 ```code``` -> <pre>code</pre>
            // 非贪婪匹配
            Pattern codeBlock = Pattern.compile("```(.*?)```", Pattern.DOTALL);
            Matcher m = codeBlock.matcher(html);
            html = m.replaceAll(mr -> "<pre>" + mr.group(1).trim() + "</pre>");

            // 3. 行内代码 `code` -> <code>code</code>
            html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

            // 4. 粗体 **text** -> <b>text</b>
            html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

            // 5. 换行符 \n -> <br> (除了在 pre 标签内的)
            // 简单处理：先全部换行，JEditorPane 的 pre 样式会处理显示
            // 更精细的处理需要分段解析，这里简化处理
            html = html.replace("\n", "<br>");

            return html;
        }
    }

    // ==========================================
    // 配置管理 (使用 Java Preferences)
    // ==========================================
    static class AppConfig {
        private final Preferences prefs = Preferences.userNodeForPackage(ChatGptSwingClient.class);
        private String apiUrl;
        private String apiKey;
        private String model;

        public void load() {
            apiUrl = prefs.get("api_url", "https://api.openai.com/v1/chat/completions");
            apiKey = prefs.get("api_key", "");
            model = prefs.get("model", "gpt-3.5-turbo");
        }

        public void save() {
            prefs.put("api_url", apiUrl);
            prefs.put("api_key", apiKey);
            prefs.put("model", model);
        }

        // Getters & Setters
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    // ==========================================
    // API 数据结构 Record
    // ==========================================
    record ChatRequest(String model, List<Message> messages) {}
    record Message(String role, String content) {}
    record ChatResponse(String id, List<Choice> choices) {}
    record Choice(int index, Message message, String finish_reason) {}

    // ==========================================
    // Main
    // ==========================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ChatGptSwingClient().setVisible(true));
    }
}