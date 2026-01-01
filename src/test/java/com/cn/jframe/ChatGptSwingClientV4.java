package com.cn.jframe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * ChatGPT Swing Client V4
 * 修改点：字体(Maple Mono NF CN)、大字号(18px)、清空历史功能
 */
public class ChatGptSwingClientV4 extends JFrame {

    // --- 配置常量 ---
    private static final String FONT_FACE = "Maple Mono NF CN";
    private static final int FONT_SIZE = 18; // 调大字号

    // --- UI 配色 ---
    private static final Color BG_COLOR = new Color(245, 245, 247);
    private static final Color TOP_BAR_COLOR = new Color(255, 255, 255);
    private static final Color USER_BUBBLE_COLOR = new Color(0, 122, 255);
    private static final Color AI_BUBBLE_COLOR = new Color(255, 255, 255);

    // --- 组件 ---
    private JPanel chatListPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private JButton sendButton;
    private JButton settingsButton;
    private JButton clearButton; // 新增：清空按钮
    private JLabel statusLabel;

    // --- 数据 ---
    private final List<Message> conversationHistory = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient;
    private final AppConfig config = new AppConfig();

    // --- 打字机状态 ---
    private volatile boolean isTyping = false;
    private Timer typewriterTimer;
    private BubblePanel currentAiBubble;
    private String currentAiFullText = "";
    private int typeIndex = 0;

    public ChatGptSwingClientV4() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        config.load();

        initUI();
    }

    private void initUI() {
        setTitle("ChatGPT Client - Maple Mono Edition");
        setSize(1000, 800); // 稍微加大窗口以适应大字体
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);
        setContentPane(mainPanel);

        // 1. 顶部栏 (Header)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(TOP_BAR_COLOR);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        headerPanel.setPreferredSize(new Dimension(getWidth(), 60));

        JLabel titleLabel = new JLabel(" AI Assistant", JLabel.LEFT);
        // 标题也使用指定字体
        titleLabel.setFont(new Font(FONT_FACE, Font.BOLD, 20));
        titleLabel.setBorder(new EmptyBorder(0, 20, 0, 0));

        // 顶部按钮容器
        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnContainer.setOpaque(false);

        // 新增：清空按钮
        clearButton = new JButton("🗑 清空历史");
        styleButton(clearButton, new Color(255, 59, 48), Color.WHITE); // 红色警示色
        clearButton.addActionListener(e -> clearHistory());

        settingsButton = new JButton("⚙ 设置");
        styleButton(settingsButton, new Color(240, 240, 240), Color.BLACK);
        settingsButton.addActionListener(e -> showSettingsDialog());

        btnContainer.add(clearButton);
        btnContainer.add(settingsButton);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(btnContainer, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // 2. 聊天列表区域 (Center)
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListPanel.setBackground(BG_COLOR);
        chatListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(chatListPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20); // 滚动速度更快
        add(scrollPane, BorderLayout.CENTER);

        // 3. 底部输入区 (Bottom)
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(TOP_BAR_COLOR);
        bottomPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        inputField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque() && getBorder() instanceof RoundBorder) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        inputField.setOpaque(false);
        inputField.setBorder(new RoundBorder(new Color(200, 200, 200), 10));
        // 输入框应用新字体和字号
        inputField.setFont(new Font(FONT_FACE, Font.PLAIN, FONT_SIZE));
        inputField.setBackground(new Color(245, 245, 245));
        inputField.addActionListener(e -> onSend());

        sendButton = new JButton("发送");
        styleButton(sendButton, USER_BUBBLE_COLOR, Color.WHITE);
        sendButton.setFont(new Font(FONT_FACE, Font.BOLD, 14));
        sendButton.setPreferredSize(new Dimension(80, 40));
        sendButton.addActionListener(e -> onSend());

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font(FONT_FACE, Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- 功能实现：清空历史 ---
    private void clearHistory() {
        if (isTyping) {
            JOptionPane.showMessageDialog(this, "请等待当前回复完成");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "确定要清空所有对话记录吗？这会重置上下文记忆。",
                "清空确认",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            // 1. 清空内存
            conversationHistory.clear();
            // 2. 清空界面
            chatListPanel.removeAll();
            chatListPanel.revalidate();
            chatListPanel.repaint();
            // 3. 状态重置
            statusLabel.setText("记录已清空");
        }
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font(FONT_FACE, Font.BOLD, 14)); // 按钮字体
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void onSend() {
        if (isTyping) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        if (config.getApiKey().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请点击设置配置 API Key");
            return;
        }

        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        clearButton.setEnabled(false); // 发送中禁止清空
        statusLabel.setText("思考中...");

        addBubble(text, true);
        conversationHistory.add(new Message("user", text));

        currentAiBubble = addBubble("...", false);

        ChatRequest payload = new ChatRequest(config.getModel(), conversationHistory);
        String jsonBody = gson.toJson(payload);

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
                    ChatResponse chatResp = gson.fromJson(response.body(), ChatResponse.class);
                    if (chatResp.choices() != null && !chatResp.choices().isEmpty()) {
                        String content = chatResp.choices().get(0).message().content();
                        SwingUtilities.invokeLater(() -> startTypewriter(content));
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        currentAiBubble.setTextContent("Error: " + response.statusCode());
                        resetState();
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    currentAiBubble.setTextContent("Exception: " + e.getMessage());
                    resetState();
                });
                e.printStackTrace();
            }
        });
    }

    private BubblePanel addBubble(String text, boolean isUser) {
        JPanel wrapper = new JPanel(new FlowLayout(isUser ? FlowLayout.RIGHT : FlowLayout.LEFT));
        wrapper.setBackground(BG_COLOR);
        wrapper.setBorder(new EmptyBorder(5, 0, 5, 0));

        AvatarPanel avatar = new AvatarPanel(isUser ? "Me" : "AI", isUser ? USER_BUBBLE_COLOR : new Color(100, 100, 100));
        BubblePanel bubble = new BubblePanel(text, isUser);

        if (isUser) {
            wrapper.add(bubble);
            wrapper.add(avatar);
        } else {
            wrapper.add(avatar);
            wrapper.add(bubble);
        }

        chatListPanel.add(wrapper);
        chatListPanel.revalidate();
        scrollToBottom();
        return bubble;
    }

    private void startTypewriter(String fullText) {
        isTyping = true;
        currentAiFullText = fullText;
        typeIndex = 0;
        statusLabel.setText("输入中...");

        conversationHistory.add(new Message("assistant", fullText));

        typewriterTimer = new Timer(20, e -> {
            typeIndex += 3;
            if (typeIndex > currentAiFullText.length()) typeIndex = currentAiFullText.length();

            String sub = currentAiFullText.substring(0, typeIndex);
            currentAiBubble.setTextContent(sub);
            scrollToBottom();

            if (typeIndex >= currentAiFullText.length()) {
                ((Timer)e.getSource()).stop();
                resetState();
            }
        });
        typewriterTimer.start();
    }

    private void resetState() {
        isTyping = false;
        inputField.setEnabled(true);
        sendButton.setEnabled(true);
        clearButton.setEnabled(true);
        inputField.requestFocus();
        statusLabel.setText("就绪");
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    // --- 气泡组件 (CSS 字体修改核心) ---
    static class BubblePanel extends JPanel {
        private final boolean isUser;
        private final JEditorPane textPane;
        private static final int MAX_WIDTH = 700; // 调大气泡宽度以容纳大字体

        public BubblePanel(String text, boolean isUser) {
            this.isUser = isUser;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(12, 18, 12, 18)); // 增加内边距

            textPane = new JEditorPane();
            textPane.setEditable(false);
            textPane.setContentType("text/html");
            textPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            textPane.setOpaque(false);

            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet style = kit.getStyleSheet();

            // ----------------------------------------------------
            // 核心修改：CSS 中指定 Maple Mono NF CN 字体和大字号
            // ----------------------------------------------------
            String fontRule = String.format(
                    "body { font-family: '%s', 'Microsoft YaHei', sans-serif; font-size: %dpx; margin: 0; }",
                    FONT_FACE, FONT_SIZE
            );

            String textColor = isUser ? "white" : "#333333";
            String codeBg = isUser ? "#1E90FF" : "#F0F0F0";

            style.addRule(fontRule);
            style.addRule("body { color: " + textColor + "; }");
            style.addRule("p { margin: 4px 0; line-height: 1.4; }"); // 增加行高
            style.addRule("code { font-family: '" + FONT_FACE + "', monospace; background-color: " + codeBg + "; padding: 4px; border-radius: 4px;}");
            style.addRule("pre { font-family: '" + FONT_FACE + "', monospace; background-color: " + codeBg + "; padding: 8px; border-radius: 6px; overflow: auto; margin: 8px 0;}");

            textPane.setEditorKit(kit);
            setTextContent(text);

            add(textPane, BorderLayout.CENTER);
        }

        public void setTextContent(String text) {
            String html = SimpleMarkdownParser.parse(text);
            textPane.setText("<html><body>" + html + "</body></html>");

            setSize(new Dimension(MAX_WIDTH, Short.MAX_VALUE));
            Dimension pref = textPane.getPreferredSize();
            int w = Math.min(MAX_WIDTH, pref.width + 30);
            int h = pref.height + 24; // 调整高度容差
            setPreferredSize(new Dimension(w, h));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isUser ? USER_BUBBLE_COLOR : AI_BUBBLE_COLOR);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24); // 圆角稍微加大
            if (!isUser) {
                g2.setColor(new Color(220, 220, 220));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class AvatarPanel extends JPanel {
        private final String letter;
        private final Color color;
        public AvatarPanel(String letter, Color color) {
            this.letter = letter;
            this.color = color;
            setPreferredSize(new Dimension(42, 42)); // 头像稍微加大
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, 42, 42);
            g2.setColor(Color.WHITE);
            // 头像字体也用配置的字体
            g2.setFont(new Font(FONT_FACE, Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            int x = (42 - fm.stringWidth(letter)) / 2;
            int y = ((42 - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(letter, x, y);
            g2.dispose();
        }
    }

    static class RoundBorder implements javax.swing.border.Border {
        private final Color color;
        private final int radius;
        public RoundBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        public Insets getBorderInsets(Component c) { return new Insets(radius+5, radius+5, radius+5, radius+5); }
        public boolean isBorderOpaque() { return true; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }
    }

    // --- 设置、Markdown、Config 保持不变 ---
    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "设置", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField urlField = new JTextField(config.getApiUrl(), 25);
        JTextField keyField = new JPasswordField(config.getApiKey(), 25);
        JTextField modelField = new JTextField(config.getModel(), 25);

        Font f = new Font(FONT_FACE, Font.PLAIN, 14);
        urlField.setFont(f); keyField.setFont(f); modelField.setFont(f);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("接口地址:"), gbc);
        gbc.gridx = 1; dialog.add(urlField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; dialog.add(keyField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("模型名称:"), gbc);
        gbc.gridx = 1; dialog.add(modelField, gbc);

        JButton saveBtn = new JButton("保存配置");
        saveBtn.setFont(new Font(FONT_FACE, Font.BOLD, 14));
        saveBtn.addActionListener(e -> {
            config.setApiUrl(urlField.getText().trim());
            config.setApiKey(keyField.getText().trim());
            config.setModel(modelField.getText().trim());
            config.save();
            dialog.dispose();
        });
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        dialog.add(saveBtn, gbc);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    static class SimpleMarkdownParser {
        public static String parse(String markdown) {
            if (markdown == null) return "";
            String html = markdown.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            html = html.replaceAll("```(.*?)```", "<pre>$1</pre>");
            html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
            html = html.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
            html = html.replace("\n", "<br>");
            return html;
        }
    }

    static class AppConfig {
        private final Preferences prefs = Preferences.userNodeForPackage(ChatGptSwingClientV4.class);
        private String apiUrl, apiKey, model;
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
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String s) { apiUrl = s; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String s) { apiKey = s; }
        public String getModel() { return model; }
        public void setModel(String s) { model = s; }
    }

    record ChatRequest(String model, List<Message> messages) {}
    record Message(String role, String content) {}
    record ChatResponse(String id, List<Choice> choices) {}
    record Choice(int index, Message message) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatGptSwingClientV4().setVisible(true));
    }
}
